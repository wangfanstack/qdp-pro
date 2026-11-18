package com.agsccc.agriledger.module.system.service.auth;

import com.agsccc.agriledger.module.system.controller.admin.auth.vo.*;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantDO;
import com.agsccc.agriledger.module.system.dal.dataobject.user.AdminUserDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 管理后台的认证 Service 接口
 * <p>
 * 提供用户的登录、登出的能力
 *
 * @author 农E链源码
 */
public interface AdminAuthService {

    /**
     * 验证账号 + 密码。如果通过，则返回用户
     *
     * @param username 账号
     * @param password 密码
     * @return 用户
     */
    AdminUserDO authenticate(String username, String password);

    /**
     * 账号登录
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AuthLoginRespVO login(@Valid AuthLoginReqVO reqVO);

    /**
     * 基于 token 退出登录
     *
     * @param token   token
     * @param logType 登出类型
     */
    void logout(String token, Integer logType);

    /**
     * 短信验证码发送
     *
     * @param reqVO 发送请求
     */
    void sendSmsCode(AuthSmsSendReqVO reqVO);

    /**
     * 短信登录（全局账号级，两段式第一段）：消费验证码 → 获取或创建全局账号并校验状态 → 颁发 Auth-Ticket。
     * 不校验任何租户/关联信息，与移动端 smsLogin 行为一致
     *
     * @param reqVO 登录信息
     * @return 登录凭证（Auth-Ticket）与全局账号编号
     */
    AuthTicketRespVO smsLogin(AuthSmsLoginReqVO reqVO);

    /**
     * 进入租户（两段式第二段）：校验 Auth-Ticket → 账号-目标租户有效关联、租户状态、成员用户三重校验
     * → 一次性消费 ticket → 签发 Web 正式令牌（default client）
     *
     * @param ticket 登录凭证（请求头 Auth-Ticket 携带）
     * @param reqVO 目标租户
     * @return Web 正式令牌
     */
    AuthLoginRespVO enterTenant(String ticket, @Valid AuthEnterTenantReqVO reqVO);

    /**
     * 社交快捷登录，使用 code 授权码
     *
     * @param reqVO 登录信息
     * @return 登录结果
     */
    AuthLoginRespVO socialLogin(@Valid AuthSocialLoginReqVO reqVO);

    /**
     * 刷新访问令牌
     *
     * @param refreshToken 刷新令牌
     * @return 登录结果
     */
    AuthLoginRespVO refreshToken(String refreshToken);

    /**
     * 用户注册
     *
     * @param createReqVO 注册用户
     * @return 注册结果
     */
    AuthLoginRespVO register(AuthRegisterReqVO createReqVO);

    /**
     * 重置密码
     *
     * @param reqVO 验证码信息
     */
    void resetPassword(AuthResetPasswordReqVO reqVO);

    /**
     * 获得指定管理用户的全局账号可访问的租户列表
     *
     * @param userId 管理用户编号（system_users.id）
     * @return 租户列表；无全局账号时返回空列表
     */
    List<TenantDO> getAccessibleTenantList(Long userId);

    /**
     * 切换租户：校验全局账号与目标租户的有效关联、目标租户状态、目标租户管理用户后，
     * 吊销当前令牌并在目标租户上下文重签（旧租户令牌立即失效，不影响 APP 会话）
     *
     * @param token 当前访问令牌（待吊销）
     * @param reqVO 切换请求（目标租户）
     * @return 新租户上下文的令牌
     */
    AuthLoginRespVO switchTenant(String token, AuthSwitchTenantReqVO reqVO);

}
