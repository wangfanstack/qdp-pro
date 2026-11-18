package com.agsccc.agriledger.module.infra.framework.file.config;

import com.agsccc.agriledger.module.infra.framework.file.core.client.FileClientFactory;
import com.agsccc.agriledger.module.infra.framework.file.core.client.FileClientFactoryImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件配置类
 *
 * @author Sail
 */
@Configuration(proxyBeanMethods = false)
public class HftFileAutoConfiguration {

    @Bean
    public FileClientFactory fileClientFactory() {
        return new FileClientFactoryImpl();
    }

}
