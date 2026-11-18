package com.agsccc.agriledger.module.infra.framework.web.config;

import com.agsccc.agriledger.framework.swagger.config.AgriledgerSwaggerAutoConfiguration;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * infra 模块的 web 组件的 Configuration
 *
 * @author Sail
 */
@Configuration(proxyBeanMethods = false)
public class InfraWebConfiguration {

    /**
     * infra 模块的 API 分组
     */
    @Bean
    public GroupedOpenApi infraGroupedOpenApi() {
        return AgriledgerSwaggerAutoConfiguration.buildGroupedOpenApi("infra");
    }

}
