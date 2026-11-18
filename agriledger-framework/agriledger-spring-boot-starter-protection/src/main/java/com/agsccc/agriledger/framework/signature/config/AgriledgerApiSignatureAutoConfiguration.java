package com.agsccc.agriledger.framework.signature.config;

import com.agsccc.agriledger.framework.redis.config.AgriledgerRedisAutoConfiguration;
import com.agsccc.agriledger.framework.signature.core.aop.ApiSignatureAspect;
import com.agsccc.agriledger.framework.signature.core.redis.ApiSignatureRedisDAO;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * HTTP API 签名的自动配置类
 *
 * @author Zhougang
 */
@AutoConfiguration(after = AgriledgerRedisAutoConfiguration.class)
public class AgriledgerApiSignatureAutoConfiguration {

    @Bean
    public ApiSignatureAspect signatureAspect(ApiSignatureRedisDAO signatureRedisDAO) {
        return new ApiSignatureAspect(signatureRedisDAO);
    }

    @Bean
    public ApiSignatureRedisDAO signatureRedisDAO(StringRedisTemplate stringRedisTemplate) {
        return new ApiSignatureRedisDAO(stringRedisTemplate);
    }

}
