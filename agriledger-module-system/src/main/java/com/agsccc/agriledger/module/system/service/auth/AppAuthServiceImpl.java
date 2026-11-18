package com.agsccc.agriledger.module.system.service.auth;

import com.agsccc.agriledger.framework.common.enums.UserTypeEnum;
import com.agsccc.agriledger.framework.common.util.monitor.TracerUtils;
import com.agsccc.agriledger.framework.common.util.servlet.ServletUtils;
import com.agsccc.agriledger.framework.security.core.LoginUser;
import com.agsccc.agriledger.framework.security.core.util.SecurityFrameworkUtils;
import com.agsccc.agriledger.framework.tenant.core.util.TenantUtils;
import com.agsccc.agriledger.module.system.api.logger.dto.LoginLogCreateReqDTO;
import com.agsccc.agriledger.module.system.api.sms.SmsCodeApi;
import com.agsccc.agriledger.module.system.api.sms.dto.code.SmsCodeSendReqDTO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthEnterMerchantReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthLoginRespVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthPreLoginRespVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSmsLoginReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSmsSendReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSwitchTenantReqVO;
import com.agsccc.agriledger.module.system.convert.auth.AppAuthConvert;
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
import com.agsccc.agriledger.module.system.service.oauth2.OAuth2TokenService;
import com.agsccc.agriledger.module.system.service.user.AdminUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.framework.common.util.servlet.ServletUtils.getClientIP;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.ACCOUNT_NOT_EXISTS;
import static com.agsccc.agriledger.module.system.enums.ErrorCodeConstants.AUTH_TICKET_INVALID;

/**
 * 移动端会话 Service 实现类（两段式登录）
 * <p>
 * 第一段 smsLogin 仅做全局账号级认证（验证码 → SystemAccount → 颁发 Ticket）；
 * 第二段 enterMerchant 凭 ticket 经三重校验换正式 APP 令牌（app client，与 Web 会话独立）
 *
 * @author 农E链源码
 */
@Service
@Validated
@Slf4j
public class AppAuthServiceImpl implements AppAuthService {

    @Resource
    private SmsCodeApi smsCodeApi;
    @Resource
    private SystemAccountService systemAccountService;
    @Resource
    private OAuth2TokenService oauth2TokenService;
    @Resource
    private LoginLogService loginLogService;
    @Resource
    private AdminUserService userService;
    @Resource
    private AuthTicketRedisDAO authTicketRedisDAO;

    /**
     * 登录凭证（Ticket）有效期
     */
    @Value("${agriledger.auth.ticket-validity}")
    private Duration ticketValidity;

    @Override
    public void sendSmsCode(AppAuthSmsSendReqVO reqVO) {
        // 登录场景：未注册手机号亦可发送（新用户注册流程入口），不校验租户内用户存在性
        smsCodeApi.sendSmsCode(new SmsCodeSendReqDTO().setMobile(reqVO.getMobile())
                .setScene(SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene()).setCreateIp(getClientIP()));
    }

    @Override
    public AppAuthPreLoginRespVO smsLogin(AppAuthSmsLoginReqVO reqVO) {
        // 1. 校验并消费短信验证码（一次性）
        smsCodeApi.useSmsCode(AppAuthConvert.INSTANCE.convert(reqVO,
                SmsSceneEnum.ADMIN_MEMBER_LOGIN.getScene(), getClientIP()));

        // 2. 获取或创建全局账号，并校验账号状态（全局账号级认证，不涉及任何租户/关联信息）
        SystemAccountDO account = systemAccountService.getOrCreateAccountByMobile(reqVO.getMobile());
        systemAccountService.validateAccountEnabled(account);

        // 3. 颁发临时登录凭证（Ticket）：仅用于进入商户前的自助操作（选商户进入/注册/查进度/取消/重提）
        String ticket = UUID.randomUUID().toString().replace("-", "");
        authTicketRedisDAO.create(ticket, account.getId(), ticketValidity);
        log.info("[smsLogin][手机号({}) 全局账号({}) 登录成功，颁发 ticket]", reqVO.getMobile(), account.getId());
        return AppAuthPreLoginRespVO.builder()
                .accountId(account.getId())
                .ticket(ticket)
                .ticketExpiresTime(LocalDateTime.now().plus(ticketValidity))
                .build();
    }

    @Override
    public AppAuthLoginRespVO enterMerchant(String ticket, AppAuthEnterMerchantReqVO reqVO) {
        // 1. 校验登录凭证（无效/已过期/已消费均视为无效）
        Long accountId = authTicketRedisDAO.getAccountId(ticket);
        if (accountId == null) {
            throw exception(AUTH_TICKET_INVALID);
        }

        // 2. 三重校验：账号-目标租户有效关联 → 目标租户状态 → 成员用户（存在/归属/状态）
        AdminUserDO user = systemAccountService.validateSwitchContext(accountId, reqVO.getTenantId());

        // 3. 在目标租户上下文签发正式 APP 令牌（app client，userType=ADMIN 复用管理端 RBAC 与数据权限）
        OAuth2AccessTokenDO accessTokenDO = TenantUtils.execute(reqVO.getTenantId(), () ->
                oauth2TokenService.createAccessToken(user.getId(), UserTypeEnum.ADMIN.getValue(), OAuth2ClientConstants.CLIENT_ID_APP, null));

        // 4. 一次性消费 ticket（签发成功后删除；签发失败可凭原 ticket 重试）
        authTicketRedisDAO.delete(ticket);
        createLoginLog(user.getId(), user.getMobile());
        log.info("[enterMerchant][全局账号({}) 进入租户({})，用户({})", accountId, reqVO.getTenantId(), user.getId());
        return AppAuthConvert.INSTANCE.convert(accessTokenDO)
                .setAccountId(accountId)
                .setTenantId(reqVO.getTenantId());
    }

    @Override
    public AppAuthLoginRespVO refreshToken(String refreshToken) {
        // 刷新 app client 的访问令牌（clientId 匹配由 refreshAccessToken 校验）
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.refreshAccessToken(
                refreshToken, OAuth2ClientConstants.CLIENT_ID_APP);
        AppAuthLoginRespVO respVO = AppAuthConvert.INSTANCE.convert(accessTokenDO);
        // 补充全局账号编号（由登录时绑定的用户反查）
        SystemAccountUserRelDO rel = systemAccountService.getRelBySystemUserId(accessTokenDO.getUserId());
        if (rel != null) {
            respVO.setAccountId(rel.getAccountId());
        }
        return respVO;
    }

    @Override
    public void logout(String token) {
        // 删除访问令牌（含对应刷新令牌），仅影响当前 APP 会话
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.removeAccessToken(token);
        if (accessTokenDO == null) {
            return;
        }
        // 记录登出日志
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(LoginLogTypeEnum.LOGOUT_SELF.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(accessTokenDO.getUserId());
        reqDTO.setUserType(UserTypeEnum.ADMIN.getValue());
        reqDTO.setUsername(getUsername(accessTokenDO.getUserId()));
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(LoginResultEnum.SUCCESS.getResult());
        loginLogService.createLoginLog(reqDTO);
    }

    @Override
    public List<TenantDO> getAccessibleTenantList(Long userId) {
        return systemAccountService.getAccessibleTenantListByUserId(userId);
    }

    @Override
    public AppAuthLoginRespVO switchTenant(String token, AppAuthSwitchTenantReqVO reqVO) {
        // 1. 获取当前登录用户与全局账号（优先 token 携带，旧令牌回退按用户反查）
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        Long accountId = resolveAccountId(loginUser);
        // 2. 切换前置校验：账号-目标租户有效关联、目标租户状态、目标租户管理用户；
        //    任一失败直接抛出，不触碰任何令牌
        AdminUserDO user = systemAccountService.validateSwitchContext(accountId, reqVO.getTenantId());
        // 3. 吊销当前 APP 令牌后重签（先吊销：签发异常时当前会话登出重登，避免新旧租户令牌并存）
        oauth2TokenService.removeAccessToken(token);
        // 4. 在目标租户上下文重签 APP 专用 client 令牌（仅影响 APP 会话）
        return TenantUtils.execute(reqVO.getTenantId(), () -> {
            OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(
                    user.getId(), UserTypeEnum.ADMIN.getValue(), OAuth2ClientConstants.CLIENT_ID_APP, null);
            return AppAuthConvert.INSTANCE.convert(accessTokenDO)
                    .setAccountId(accountId)
                    .setTenantId(reqVO.getTenantId());
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

    private void createLoginLog(Long userId, String mobile) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(LoginLogTypeEnum.LOGIN_MOBILE.getType());
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(userId);
        reqDTO.setUserType(UserTypeEnum.ADMIN.getValue());
        reqDTO.setUsername(mobile);
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(LoginResultEnum.SUCCESS.getResult());
        loginLogService.createLoginLog(reqDTO);
        // 更新最后登录时间
        userService.updateUserLogin(userId, ServletUtils.getClientIP());
    }

    private String getUsername(Long userId) {
        AdminUserDO user = userService.getUser(userId);
        return user != null ? user.getUsername() : null;
    }

}
