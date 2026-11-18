package com.agsccc.agriledger.module.system.api.account;

import com.agsccc.agriledger.module.system.dal.redis.auth.AuthTicketRedisDAO;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

/**
 * 移动端全局账号登录凭证（Ticket）API 实现类
 *
 * @author 农E链源码
 */
@Service
public class AuthTicketApiImpl implements AuthTicketApi {

    @Resource
    private AuthTicketRedisDAO authTicketRedisDAO;

    @Override
    public Long getAccountIdByTicket(String ticket) {
        return authTicketRedisDAO.getAccountId(ticket);
    }

}
