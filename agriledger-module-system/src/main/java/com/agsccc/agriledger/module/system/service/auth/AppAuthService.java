package com.agsccc.agriledger.module.system.service.auth;

import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthEnterMerchantReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthLoginRespVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthPreLoginRespVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSmsLoginReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSmsSendReqVO;
import com.agsccc.agriledger.module.system.controller.app.auth.vo.AppAuthSwitchTenantReqVO;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantDO;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 移动端会话 Service 接口（两段式登录）
 * <p>
 * 第一段（全局账号级）：短信验证码登录 → 校验/创建全局账号 → 颁发临时 ticket（Ticket），
 * ticket 仅用于进入商户前的自助操作（选商户进入/注册/查进度/取消/重提）；<br>
 * 第二段（商户级）：凭 ticket + 目标租户进入商户，经账号-租户关联、租户状态、成员用户三重校验后
 * 签发正式 APP 令牌（app client，userType=ADMIN 复用管理端 RBAC，与 Web 会话相互独立）
 *
 * @author 农E链源码
 */
public interface AppAuthService {

    /**
     * 发送短信验证码（登录场景，未注册手机号亦可发送，供新用户注册流程使用）
     *
     * @param reqVO 发送请求
     */
    void sendSmsCode(@Valid AppAuthSmsSendReqVO reqVO);

    /**
     * 短信验证码登录（全局账号级，两段式第一段）：消费验证码 → 获取或创建全局账号并校验状态 → 颁发 ticket。
     * 不校验任何租户/关联信息，有无商户行为一致
     *
     * @param reqVO 登录信息
     * @return ticket 与全局账号编号
     */
    AppAuthPreLoginRespVO smsLogin(@Valid AppAuthSmsLoginReqVO reqVO);

    /**
     * 进入商户（两段式第二段）：校验 ticket → 账号-目标租户有效关联、租户状态、成员用户三重校验
     * → 一次性消费 ticket → 签发正式 APP 令牌
     *
     * @param ticket 登录凭证（请求头 Ticket）
     * @param reqVO 目标租户
     * @return 正式 APP 令牌
     */
    AppAuthLoginRespVO enterMerchant(String ticket, @Valid AppAuthEnterMerchantReqVO reqVO);

    /**
     * 刷新访问令牌（app client）
     *
     * @param refreshToken 刷新令牌
     * @return 新的令牌
     */
    AppAuthLoginRespVO refreshToken(String refreshToken);

    /**
     * 基于 token 退出登录（仅影响 APP 会话）
     *
     * @param token 访问令牌
     */
    void logout(String token);

    /**
     * 获得指定管理用户的全局账号可访问的租户列表
     *
     * @param userId 管理用户编号（system_users.id）
     * @return 租户列表；无全局账号时返回空列表
     */
    List<TenantDO> getAccessibleTenantList(Long userId);

    /**
     * 切换租户：校验全局账号与目标租户的有效关联、目标租户状态、目标租户管理用户后，
     * 吊销当前 APP 令牌并在目标租户上下文重签（仅影响 APP 会话，Web 会话不受影响）
     *
     * @param token 当前 APP 访问令牌（待吊销）
     * @param reqVO 切换请求（目标租户）
     * @return 新租户上下文的 APP 令牌
     */
    AppAuthLoginRespVO switchTenant(String token, AppAuthSwitchTenantReqVO reqVO);

}
