package com.agsccc.agriledger.module.system.api.account;

/**
 * 移动端全局账号登录凭证（Ticket）API：供 merchant 等业务模块跨模块读取
 * <p>
 * ticket 为两段式登录第一段凭证，仅用于进入商户前的自助操作；本 API 只读不消费，
 * 一次性消费仅在 {@code AppAuthService#enterMerchant} 中发生
 *
 * @author 农E链源码
 */
public interface AuthTicketApi {

    /**
     * 查询登录凭证绑定的全局账号编号（不消费凭证）
     *
     * @param ticket 登录凭证（请求头 Ticket 携带）
     * @return 全局账号编号；凭证无效或已过期返回 null
     */
    Long getAccountIdByTicket(String ticket);

}
