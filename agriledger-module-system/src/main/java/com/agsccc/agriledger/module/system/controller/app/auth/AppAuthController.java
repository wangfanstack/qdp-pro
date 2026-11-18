package com.agsccc.agriledger.module.system.controller.app.auth;

import com.agsccc.agriledger.framework.common.pojo.CommonResult;
import com.agsccc.agriledger.framework.ratelimiter.core.annotation.RateLimiter;
import com.agsccc.agriledger.framework.ratelimiter.core.keyresolver.impl.ExpressionRateLimiterKeyResolver;
import com.agsccc.agriledger.framework.security.config.SecurityProperties;
import com.agsccc.agriledger.framework.security.core.util.SecurityFrameworkUtils;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthEnterMerchantReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthLoginRespVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthPreLoginRespVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthRefreshTokenReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSmsLoginReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSmsSendReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSwitchTenantReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthTenantRespVO;
import com.agsccc.agriledger.module.system.convert.auth.AppAuthConvert;
import com.agsccc.agriledger.module.system.service.auth.AppAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.agsccc.agriledger.framework.common.pojo.CommonResult.success;
import static com.agsccc.agriledger.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 移动端会话入口（/app-api/auth/*）
 * <p>
 * 与 Web 管理端共用全局账号、system_users 与 RBAC，但 Token 走独立 client（app）；
 * 业务与 system 接口两端共用 /admin-api（app client 可访问，见 TokenAuthenticationFilter）
 *
 * @author 农E链源码
 */
@Tag(name = "用户 APP - 认证（管理用户）")
@RestController
@RequestMapping("/auth")
@Validated
public class AppAuthController {

    @Resource
    private AppAuthService authService;

    @Resource
    private SecurityProperties securityProperties;

    @PostMapping("/send-sms-code")
    @PermitAll
    @Operation(summary = "发送短信验证码（登录场景，未注册手机号亦可发送）")
    @RateLimiter(time = 60, count = 6, keyResolver = ExpressionRateLimiterKeyResolver.class, keyArg = "#reqVO.mobile")
    public CommonResult<Boolean> sendSmsCode(@RequestBody @Valid AppAuthSmsSendReqVO reqVO) {
        authService.sendSmsCode(reqVO);
        return success(true);
    }

    @PostMapping("/sms-login")
    @PermitAll
    @Operation(summary = "使用短信验证码登录（全局账号级，两段式第一段：消费验证码并颁发 Ticket，不签发令牌）")
    @RateLimiter(time = 60, count = 6, keyResolver = ExpressionRateLimiterKeyResolver.class, keyArg = "#reqVO.mobile")
    public CommonResult<AppAuthPreLoginRespVO> smsLogin(@RequestBody @Valid AppAuthSmsLoginReqVO reqVO) {
        return success(authService.smsLogin(reqVO));
    }

    @PostMapping("/enter-merchant")
    @PermitAll
    @Operation(summary = "进入商户（两段式第二段：凭 Ticket + 目标租户，经关联/租户/成员三重校验换取正式 APP 令牌，ticket 一次性消费）")
    public CommonResult<AppAuthLoginRespVO> enterMerchant(@RequestHeader("Ticket") String ticket, @RequestBody @Valid AppAuthEnterMerchantReqVO reqVO) {
        return success(authService.enterMerchant(ticket, reqVO));
    }

    @PostMapping("/refresh-token")
    @PermitAll
    @Operation(summary = "刷新访问令牌")
    public CommonResult<AppAuthLoginRespVO> refreshToken(@RequestBody @Valid AppAuthRefreshTokenReqVO reqVO) {
        return success(authService.refreshToken(reqVO.getRefreshToken()));
    }

    @PostMapping("/logout")
    @PermitAll
    @Operation(summary = "退出登录")
    public CommonResult<Boolean> logout(HttpServletRequest request) {
        authService.logout(SecurityFrameworkUtils.obtainAuthorization(request, securityProperties.getTokenHeader(), securityProperties.getTokenParameter()));
        return success(true);
    }

    @GetMapping("/accessible-tenants")
    @Operation(summary = "获得当前账号可访问的租户列表（全局账号关联）")
    public CommonResult<List<AppAuthTenantRespVO>> getAccessibleTenantList() {
        return success(AppAuthConvert.INSTANCE.convertList(authService.getAccessibleTenantList(getLoginUserId())));
    }

    @PostMapping("/switch-tenant")
    @Operation(summary = "切换租户（服务端校验关联并重签令牌，仅影响 APP 会话）")
    public CommonResult<AppAuthLoginRespVO> switchTenant(HttpServletRequest request, @RequestBody @Valid AppAuthSwitchTenantReqVO reqVO) {
        String token = SecurityFrameworkUtils.obtainAuthorization(request, securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        return success(authService.switchTenant(token, reqVO));
    }

}
