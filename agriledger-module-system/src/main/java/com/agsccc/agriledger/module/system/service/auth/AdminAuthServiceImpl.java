package com.agsccc.agriledger.module.system.service.auth;

import com.agsccc.agriledger.framework.common.enums.CommonStatusEnum;
import com.agsccc.agriledger.framework.common.enums.UserTypeEnum;
import com.agsccc.agriledger.framework.common.util.monitor.TracerUtils;
import com.agsccc.agriledger.framework.common.util.servlet.ServletUtils;
import com.agsccc.agriledger.framework.common.util.validation.ValidationUtils;
import com.agsccc.agriledger.framework.datapermission.core.annotation.DataPermission;
import com.agsccc.agriledger.framework.security.core.LoginUser;
import com.agsccc.agriledger.framework.security.core.util.SecurityFrameworkUtils;
import com.agsccc.agriledger.framework.tenant.core.util.TenantUtils;
import com.agsccc.agriledger.module.system.api.logger.dto.LoginLogCreateReqDTO;
import com.agsccc.agriledger.module.system.api.sms.SmsCodeApi;
import com.agsccc.agriledger.module.system.api.sms.dto.code.SmsCodeUseReqDTO;
import com.agsccc.agriledger.module.system.api.social.dto.SocialUserBindReqDTO;
import com.agsccc.agriledger.module.system.api.social.dto.SocialUserRespDTO;
import com.agsccc.agriledger.module.system.controller.admin.auth.vo.*;
import com.agsccc.agriledger.module.system.convert.auth.AuthConvert;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountDO;
import com.agsccc.agriledger.module.system.dal.dataobject.account.SystemAccountUserRelDO;
import com.agsccc.agriledger.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantDO;
import com.agsccc.agriledger.module.system.dal.dataobject.user.AdminUserDO;
import com.agsccc.agriledger.module.system.dal.redis.auth.AuthTicketRedisDAO;
import com.agsccc.agriledger.module.system.enums.logger.LoginLogTypeEnum;
import com.agsccc.agriledger.module.system.enums.logger.LoginResultEnum;
import com.agsccc.agriledger.module.system.enums.oauth2.OAuth2ClientConstants;
import com.agsccc.agriledger.module.system.enums.sms.SmsSceneEnum;
import com.agsccc.agriledger.module.system.service.account.SystemAccountService;
import com.agsccc.agriledger.module.system.service.logger.LoginLogService;
import com.agsccc.agriledger.module.system.service.member.MemberService;
import com.agsccc.agriledger.module.system.service.oauth2.OAuth2TokenService;
import com.agsccc.agriledger.module.system.service.social.SocialUserService;
import com.agsccc.agriledger.module.system.service.user.AdminUserService;
import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.anji.captcha.service.CaptchaService;
import com.google.common.annotations.VisibleForTesting;
import jakarta.annotation.Resource;
import jakarta.validation.Validator;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.framework.common.util.servlet.ServletUtils.getClientIP;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.*;
/**
 * Auth Service 实现类
 *
 * @author 农E链源码
 */
@Service
@Slf4j
public class AdminAuthServiceImpl implements AdminAuthService {

    @Resource
    private AdminUserService userService;
    @Resource
    private LoginLogService loginLogService;
    @Resource
    private OAuth2TokenService oauth2TokenService;
    @Resource
    private SocialUserService socialUserService;
    @Resource
    private MemberService memberService;
    @Resource
    private Validator validator;
    @Resource
    private CaptchaService captchaService;
    @Resource
    private SmsCodeApi smsCodeApi;
    @Resource
    private SystemAccountService systemAccountService;
    @Resource
    private AuthTicketRedisDAO authTicketRedisDAO;

    /**
     * 登录凭证（Auth-Ticket）有效期
     */
    @Value("${agriledger.auth.ticket-validity:30m}")
    private Duration ticketValidity;

    /**
     * 验证码的开关，默认为 true
     */
    @Value("${agriledger.captcha.enable:true}")
    @Setter // 为了单测：开启或者关闭验证码
    private Boolean captchaEnable;

    @Override
    public AdminUserDO authenticate(String username, String password) {
        final LoginLogTypeEnum logTypeEnum = LoginLogTypeEnum.LOGIN_USERNAME;
        // 校验账号是否存在
        AdminUserDO user = userService.getUserByUsername(username);
        if (user == null) {
            createLoginLog(null, username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (!userService.isPasswordMatch(password, user.getPassword())) {
            createLoginLog(user.getId(), username, logTypeEnum, LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        // 校验是否禁用
        validateUserStatus(user, username, logTypeEnum);
        return user;
    }

    @Override
    @DataPermission(enable = false)
    public AuthLoginRespVO login(AuthLoginReqVO reqVO) {
        // 校验验证码
        validateCaptcha(reqVO);

        // 使用账号密码，进行登录
        AdminUserDO user = authenticate(reqVO.getUsername(), reqVO.getPassword());

        // 如果 socialType 非空，说明需要绑定社交用户
        if (reqVO.getSocialType() != null) {
            socialUserService.bindSocialUser(new SocialUserBindReqDTO(user.getId(), getUserType().getValue(),
                    reqVO.getSocialType(), reqVO.getSocialCode(), reqVO.getSocialState()));
        }
        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, reqVO.getUsername(), LoginLogTypeEnum.LOGIN_USERNAME);
    }

    @Override
    public void sendSmsCode(AuthSmsSendReqVO reqVO) {
        // 如果是重置密码场景，需要校验图形验证码是否正确
        if (Objects.equals(SmsSceneEnum.ADMIN_MEMBER_RESET_PASSWORD.getScene(), reqVO.getScene())) {
            ResponseModel response = doValidateCaptcha(reqVO);
            if (!response.isSuccess()) {
                throw exception(AUTH_REGISTER_CAPTCHA_CODE_ERROR, response.getRepMsg());
            }
        }

        // 重置密码场景，验证租户内用户是否存在；登录场景走全局账号体系，不要求租户内已有用户
        if (Objects.equals(SmsSceneEnum.ADMIN_MEMBER_RESET_PASSWORD.getScene(), reqVO.getScene())
                && userService.getUserByMobile(reqVO.getMobile()) == null) {
            throw exception(AUTH_MOBILE_NOT_EXISTS);
        }
        // 发送验证码
        smsCodeApi.sendSmsCode(AuthConvert.INSTANCE.convert(reqVO).setCreateIp(getClientIP()));
    }

    @Override
    public AuthTicketRespVO smsLogin(AuthSmsLoginReqVO reqVO) {
        // 1. 校验并消费短信验证码（一次性）
        smsCodeApi.useSmsCode(AuthConvert.INSTANCE.convert(reqVO, SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene(), getClientIP()));

        // 2. 获取或创建全局账号，并校验账号状态（全局账号级认证，不涉及任何租户/关联信息，
        //    与移动端 smsLogin 行为一致）
        SystemAccountDO account = systemAccountService.getOrCreateAccountByMobile(reqVO.getMobile());
        systemAccountService.validateAccountEnabled(account);

        // 3. 颁发临时登录凭证（Auth-Ticket）：用于选择主体（mylist）与进入租户（enter-tenant）
        String ticket = UUID.randomUUID().toString().replace("-", "");
        authTicketRedisDAO.create(ticket, account.getId(), ticketValidity);
        log.info("[smsLogin][Web 手机号({}) 全局账号({}) 登录成功，颁发 ticket]", reqVO.getMobile(), account.getId());
        return AuthTicketRespVO.builder()
                .accountId(account.getId())
                .ticket(ticket)
                .ticketExpiresTime(LocalDateTime.now().plus(ticketValidity))
                .build();
    }

    @Override
    public AuthLoginRespVO enterTenant(String ticket, AuthEnterTenantReqVO reqVO) {
        // 1. 校验登录凭证（无效/已过期/已消费均视为无效）
        Long accountId = authTicketRedisDAO.getAccountId(ticket);
        if (accountId == null) {
            throw exception(AUTH_TICKET_INVALID);
        }

        // 2. 三重校验：账号-目标租户有效关联 → 目标租户状态 → 成员用户（存在/归属/状态）
        AdminUserDO user = systemAccountService.validateSwitchContext(accountId, reqVO.getTenantId());

        // 3. 在目标租户上下文签发 Web 正式令牌（default client，与 APP 会话独立）
        OAuth2AccessTokenDO accessTokenDO = TenantUtils.execute(reqVO.getTenantId(), () ->
                oauth2TokenService.createAccessToken(user.getId(), getUserType().getValue(),
                        OAuth2ClientConstants.CLIENT_ID_DEFAULT, null));

        // 4. 一次性消费 ticket（签发成功后删除；签发失败可凭原 ticket 重试）
        authTicketRedisDAO.delete(ticket);
        createLoginLog(user.getId(), user.getMobile(), LoginLogTypeEnum.LOGIN_MOBILE, LoginResultEnum.SUCCESS);
        log.info("[enterTenant][Web 全局账号({}) 进入租户({})，用户({})", accountId, reqVO.getTenantId(), user.getId());
        return AuthConvert.INSTANCE.convertToLoginRespVO(accessTokenDO)
                .setAccountId(accountId);
    }

    private void createLoginLog(Long userId, String username,
                                LoginLogTypeEnum logTypeEnum, LoginResultEnum loginResult) {
        // 插入登录日志
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logTypeEnum.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(getUserType().getValue());
        reqDTO.setUsername(username);
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(loginResult.getResult());
        loginLogService.createLoginLog(reqDTO);
        // 更新最后登录时间
        if (userId != null && Objects.equals(LoginResultEnum.SUCCESS.getResult(), loginResult.getResult())) {
            userService.updateUserLogin(userId, ServletUtils.getClientIP());
        }
    }

    @Override
    public AuthLoginRespVO socialLogin(AuthSocialLoginReqVO reqVO) {
        // 使用 code 授权码，进行登录。然后，获得到绑定的用户编号
        SocialUserRespDTO socialUser = socialUserService.getSocialUserByCode(UserTypeEnum.ADMIN.getValue(), reqVO.getType(),
                reqVO.getCode(), reqVO.getState());
        if (socialUser == null || socialUser.getUserId() == null) {
            throw exception(AUTH_THIRD_LOGIN_NOT_BIND);
        }

        // 获得用户
        AdminUserDO user = userService.getUser(socialUser.getUserId());
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }

        // 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, user.getUsername(), LoginLogTypeEnum.LOGIN_SOCIAL);
    }

    @VisibleForTesting
    void validateCaptcha(AuthLoginReqVO reqVO) {
        ResponseModel response = doValidateCaptcha(reqVO);
        // 校验验证码
        if (!response.isSuccess()) {
            // 创建登录失败日志（验证码不正确)
            createLoginLog(null, reqVO.getUsername(), LoginLogTypeEnum.LOGIN_USERNAME, LoginResultEnum.CAPTCHA_CODE_ERROR);
            throw exception(AUTH_LOGIN_CAPTCHA_CODE_ERROR, response.getRepMsg());
        }
    }

    private ResponseModel doValidateCaptcha(CaptchaVerificationReqVO reqVO) {
        // 如果验证码关闭，则不进行校验
        if (!captchaEnable) {
            return ResponseModel.success();
        }
        ValidationUtils.validate(validator, reqVO, CaptchaVerificationReqVO.CodeEnableGroup.class);
        CaptchaVO captchaVO = new CaptchaVO();
        captchaVO.setCaptchaVerification(reqVO.getCaptchaVerification());
        return captchaService.verification(captchaVO);
    }

    private AuthLoginRespVO createTokenAfterLoginSuccess(AdminUserDO user, String username, LoginLogTypeEnum logType) {
        // 统一校验用户状态，避免短信、社交等登录方式遗漏
        validateUserStatus(user, username, logType);

        // 插入登陆日志
        createLoginLog(user.getId(), username, logType, LoginResultEnum.SUCCESS);
        // 创建访问令牌
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(user.getId(), getUserType().getValue(), OAuth2ClientConstants.CLIENT_ID_DEFAULT, null);
        // 构建返回结果；存在全局账号关联时，附加全局账号编号
        AuthLoginRespVO respVO = AuthConvert.INSTANCE.convertToLoginRespVO(accessTokenDO);
        SystemAccountUserRelDO rel = systemAccountService.getRelBySystemUserId(user.getId());
        if (rel != null) {
            respVO.setAccountId(rel.getAccountId());
        }
        return respVO;
    }

    @Override
    public List<TenantDO> getAccessibleTenantList(Long userId) {
        return systemAccountService.getAccessibleTenantListByUserId(userId);
    }

    @Override
    public AuthLoginRespVO switchTenant(String token, AuthSwitchTenantReqVO reqVO) {
        // 1. 获取当前登录用户与全局账号（优先 token 携带，旧令牌回退按用户反查）
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        Long accountId = resolveAccountId(loginUser);
        // 2. 切换前置校验：账号-目标租户有效关联、目标租户状态、目标租户管理用户；
        //    任一失败直接抛出，不触碰任何令牌
        AdminUserDO user = systemAccountService.validateSwitchContext(accountId, reqVO.getTenantId());
        // 3. 吊销当前令牌后重签（先吊销：签发异常时当前会话登出重登，避免新旧租户令牌并存）
        oauth2TokenService.removeAccessToken(token);
        // 4. 在目标租户上下文重签令牌（userInfo 按目标租户构建，含 accountId）。
        //    业务接口统一入口后，APP client 的 token 也可调用本接口：按原 token 的 client 重签，
        //    保持会话所属端不变；旧令牌无 client 信息时按 Web default 处理
        final String renewClientId = loginUser != null && OAuth2ClientConstants.CLIENT_ID_APP.equals(loginUser.getClientId())
                ? OAuth2ClientConstants.CLIENT_ID_APP : OAuth2ClientConstants.CLIENT_ID_DEFAULT;
        return TenantUtils.execute(reqVO.getTenantId(), () -> {
            OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(
                    user.getId(), getUserType().getValue(), renewClientId, null);
            return AuthConvert.INSTANCE.convertToLoginRespVO(accessTokenDO)
                    .setAccountId(accountId);
        });
    }

    /**
     * 解析当前登录用户的全局账号编号；无关联时抛出异常（无法参与多租户切换）
     */
    private Long resolveAccountId(LoginUser loginUser) {
        if (loginUser != null && loginUser.getAccountId() != null) {
            return loginUser.getAccountId();
        }
        SystemAccountUserRelDO rel = loginUser != null
                ? systemAccountService.getRelBySystemUserId(loginUser.getId()) : null;
        if (rel == null) {
            throw exception(ACCOUNT_NOT_EXISTS);
        }
        return rel.getAccountId();
    }

    private void validateUserStatus(AdminUserDO user, String username, LoginLogTypeEnum logType) {
        if (CommonStatusEnum.isDisable(user.getStatus())) {
            createLoginLog(user.getId(), username, logType, LoginResultEnum.USER_DISABLED);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
    }

    @Override
    public AuthLoginRespVO refreshToken(String refreshToken) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.refreshAccessToken(refreshToken, OAuth2ClientConstants.CLIENT_ID_DEFAULT);
        return AuthConvert.INSTANCE.convertToLoginRespVO(accessTokenDO);
    }

    @Override
    public void logout(String token, Integer logType) {
        // 删除访问令牌
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.removeAccessToken(token);
        if (accessTokenDO == null) {
            return;
        }
        // 删除成功，则记录登出日志
        createLogoutLog(accessTokenDO.getUserId(), accessTokenDO.getUserType(), logType);
    }

    private void createLogoutLog(Long userId, Integer userType, Integer logType) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logType);
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(userType);
        if (Objects.equals(getUserType().getValue(), userType)) {
            reqDTO.setUsername(getUsername(userId));
        } else {
            reqDTO.setUsername(memberService.getMemberUserMobile(userId));
        }
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(LoginResultEnum.SUCCESS.getResult());
        loginLogService.createLoginLog(reqDTO);
    }

    private String getUsername(Long userId) {
        if (userId == null) {
            return null;
        }
        AdminUserDO user = userService.getUser(userId);
        return user != null ? user.getUsername() : null;
    }

    private UserTypeEnum getUserType() {
        return UserTypeEnum.ADMIN;
    }

    @Override
    public AuthLoginRespVO register(AuthRegisterReqVO registerReqVO) {
        // 1. 校验验证码
        validateCaptcha(registerReqVO);

        // 2. 校验用户名是否已存在
        AdminUserDO user = userService.registerUser(registerReqVO);

        // 3. 创建 Token 令牌，记录登录日志
        return createTokenAfterLoginSuccess(user, registerReqVO.getUsername(), LoginLogTypeEnum.LOGIN_USERNAME);
    }

    @VisibleForTesting
    void validateCaptcha(AuthRegisterReqVO reqVO) {
        ResponseModel response = doValidateCaptcha(reqVO);
        // 验证不通过
        if (!response.isSuccess()) {
            throw exception(AUTH_REGISTER_CAPTCHA_CODE_ERROR, response.getRepMsg());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(AuthResetPasswordReqVO reqVO) {
        AdminUserDO userByMobile = userService.getUserByMobile(reqVO.getMobile());
        if (userByMobile == null) {
            throw exception(USER_MOBILE_NOT_EXISTS);
        }

        smsCodeApi.useSmsCode(new SmsCodeUseReqDTO()
                .setCode(reqVO.getCode())
                .setMobile(reqVO.getMobile())
                .setScene(SmsSceneEnum.ADMIN_MEMBER_RESET_PASSWORD.getScene())
                .setUsedIp(getClientIP())
        );

        userService.updateUserPassword(userByMobile.getId(), reqVO.getPassword());
    }
}
