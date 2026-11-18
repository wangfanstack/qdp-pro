package com.agsccc.agriledger.module.system.dal.redis.auth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static com.agsccc.agriledger.module.system.dal.redis.RedisKeyConstants.AUTH_TICKET;

/**
 * 移动端全局账号登录凭证（Ticket）的 RedisDAO
 * <p>
 * ticket 为两段式登录的第一段凭证：短信验证码登录后颁发，仅用于"进入商户前"的自助操作
 * （选择/进入商户、注册、查进度、取消、重提），进入商户（enter-merchant）时一次性消费换取正式令牌
 *
 * @author 农E链源码
 */
@Repository
public class AuthTicketRedisDAO {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 创建登录凭证
     *
     * @param ticket     凭证值（UUID）
     * @param accountId  绑定的全局账号编号
     * @param validity   有效期
     * @return 凭证过期时间点（毫秒时间戳）
     */
    public Long create(String ticket, Long accountId, Duration validity) {
        String key = formatKey(ticket);
        stringRedisTemplate.opsForValue().set(key, String.valueOf(accountId), validity);
        Long expiresAt = stringRedisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        return System.currentTimeMillis() + expiresAt;
    }

    /**
     * 查询凭证绑定的全局账号编号（不消费）
     *
     * @return 全局账号编号；凭证无效或已过期返回 null
     */
    public Long getAccountId(String ticket) {
        String accountId = stringRedisTemplate.opsForValue().get(formatKey(ticket));
        return accountId != null ? Long.valueOf(accountId) : null;
    }

    /**
     * 删除凭证（一次性消费/登出）
     */
    public void delete(String ticket) {
        stringRedisTemplate.delete(formatKey(ticket));
    }

    private static String formatKey(String ticket) {
        return String.format(AUTH_TICKET, ticket);
    }

}
