package com.agsccc.agriledger.framework.security.core.filter;

import com.agsccc.agriledger.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import com.agsccc.agriledger.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import com.agsccc.agriledger.framework.common.enums.UserTypeEnum;
import com.agsccc.agriledger.framework.common.exception.ServiceException;
import com.agsccc.agriledger.framework.common.pojo.CommonResult;
import com.agsccc.agriledger.framework.common.util.servlet.ServletUtils;
import com.agsccc.agriledger.framework.security.config.SecurityProperties;
import com.agsccc.agriledger.framework.security.core.LoginUser;
import com.agsccc.agriledger.framework.security.core.util.SecurityFrameworkUtils;
import com.agsccc.agriledger.framework.web.core.handler.GlobalExceptionHandler;
import com.agsccc.agriledger.framework.web.core.util.WebFrameworkUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Token 过滤器，验证 token 的有效性
 * 验证通过后，获得 {@link LoginUser} 信息，并加入到 Spring Security 上下文
 *
 * @author 农E链源码
 */
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final SecurityProperties securityProperties;

    private final GlobalExceptionHandler globalExceptionHandler;

    private final OAuth2TokenCommonApi oauth2TokenApi;

    @Override
    @SuppressWarnings("NullableProblems")
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = SecurityFrameworkUtils.obtainAuthorization(request,
                securityProperties.getTokenHeader(), securityProperties.getTokenParameter());
        if (StringUtils.isNotEmpty(token)) {
            Integer userType = WebFrameworkUtils.getLoginUserType(request);
            try {
                // 1.1 基于 token 构建登录用户
                LoginUser loginUser = buildLoginUserByToken(token, userType);
                // 1.2 模拟 Login 功能，方便日常开发调试
                if (loginUser == null) {
                    loginUser = mockLoginUser(request, token, userType);
                }

                // 2. 设置当前用户
                if (loginUser != null) {
                    SecurityFrameworkUtils.setLoginUser(loginUser, request);
                }
            } catch (Throwable ex) {
                CommonResult<?> result = globalExceptionHandler.allExceptionHandler(request, ex);
                ServletUtils.writeJSON(response, result);
                return;
            }
        }

        // 继续过滤链
        chain.doFilter(request, response);
    }

    private LoginUser buildLoginUserByToken(String token, Integer userType) {
        try {
            OAuth2AccessTokenCheckRespDTO accessToken = oauth2TokenApi.checkAccessToken(token);
            if (accessToken == null) {
                return null;
            }
            // 用户类型 + 端与 client 绑定校验
            // 注意：只有 /admin-api/* 和 /app-api/* 有 userType，才需要比对用户类型
            // 类似 WebSocket 的 /ws/* 连接地址，是不需要比对用户类型
            if (userType != null) {
                validateUserTypeAndClient(accessToken, userType);
            }
            // 构建登录用户
            return new LoginUser().setId(accessToken.getUserId()).setUserType(accessToken.getUserType())
                    .setClientId(accessToken.getClientId())
                    .setAccountId(getAccountId(accessToken.getUserInfo()))
                    .setInfo(accessToken.getUserInfo()) // 额外的用户信息
                    .setTenantId(accessToken.getTenantId()).setScopes(accessToken.getScopes())
                    .setExpiresTime(accessToken.getExpiresTime());
        } catch (ServiceException serviceException) {
            // 校验 Token 不通过时，考虑到一些接口是无需登录的，所以直接返回 null 即可
            return null;
        }
    }

    /**
     * 校验 token 的用户类型与端（URL 前缀推导）是否匹配
     * <p>
     * 1. /admin-api：业务接口统一入口——接受 ADMIN 类型 token（Web 的 default client 与
     *    APP 专用 client 均可，两端业务面相同），拒绝 MEMBER 类型
     * 2. /app-api：会话类接口——接受 MEMBER 类型 token（原生行为），或 APP 专用 client 的
     *    ADMIN 类型 token（管理端 APP 复用 system_users 与 RBAC，userType 保持 ADMIN）
     *
     * @param accessToken 访问令牌
     * @param userType    URL 前缀推导的期望用户类型
     */
    private void validateUserTypeAndClient(OAuth2AccessTokenCheckRespDTO accessToken, Integer userType) {
        boolean adminApi = Objects.equals(userType, UserTypeEnum.ADMIN.getValue());
        boolean tokenAdmin = Objects.equals(accessToken.getUserType(), UserTypeEnum.ADMIN.getValue());
        if (adminApi) {
            // 管理端：仅要求 ADMIN 类型（default / app client 均可访问统一业务入口）
            if (!tokenAdmin) {
                throw new AccessDeniedException("错误的用户类型");
            }
            return;
        }
        // APP 端（/app-api）：MEMBER 类型（原生），或 APP 专用 client 的 ADMIN 类型
        boolean appClient = accessToken.getClientId() != null
                && securityProperties.getAppApiClientIds().contains(accessToken.getClientId());
        if (!Objects.equals(accessToken.getUserType(), userType) && !(tokenAdmin && appClient)) {
            throw new AccessDeniedException("错误的用户类型");
        }
    }

    private Long getAccountId(Map<String, String> userInfo) {
        if (userInfo == null || userInfo.get(LoginUser.INFO_KEY_ACCOUNT_ID) == null) {
            return null;
        }
        return Long.valueOf(userInfo.get(LoginUser.INFO_KEY_ACCOUNT_ID));
    }

    /**
     * 模拟登录用户，方便日常开发调试
     * <p>
     * 注意，在线上环境下，一定要关闭该功能！！！
     *
     * @param request  请求
     * @param token    模拟的 token，格式为 {@link SecurityProperties#getMockSecret()} + 用户编号
     * @param userType 用户类型
     * @return 模拟的 LoginUser
     */
    private LoginUser mockLoginUser(HttpServletRequest request, String token, Integer userType) {
        if (!securityProperties.getMockEnable()) {
            return null;
        }
        // 必须以 mockSecret 开头
        if (!token.startsWith(securityProperties.getMockSecret())) {
            return null;
        }
        // 构建模拟用户
        Long userId = Long.valueOf(token.substring(securityProperties.getMockSecret().length()));
        return new LoginUser().setId(userId).setUserType(userType)
                .setTenantId(WebFrameworkUtils.getTenantId(request));
    }

}
