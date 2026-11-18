package com.agsccc.agriledger.framework.apilog.config;

import com.agsccc.agriledger.framework.apilog.core.filter.ApiAccessLogFilter;
import com.agsccc.agriledger.framework.apilog.core.interceptor.ApiAccessLogInterceptor;
import com.agsccc.agriledger.framework.common.biz.infra.logger.ApiAccessLogCommonApi;
import com.agsccc.agriledger.framework.common.enums.WebFilterOrderEnum;
import com.agsccc.agriledger.framework.web.config.WebProperties;
import com.agsccc.agriledger.framework.web.config.AgriledgerWebAutoConfiguration;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration(after = AgriledgerWebAutoConfiguration.class)
public class AgriledgerApiLogAutoConfiguration implements WebMvcConfigurer {

    /**
     * 创建 ApiAccessLogFilter Bean，记录 API 请求日志
     */
    @Bean
    @ConditionalOnProperty(prefix = "agriledger.access-log", value = "enable", matchIfMissing = true)
    // 允许使用 agriledger.access-log.enable=false 禁用访问日志
    public FilterRegistrationBean<ApiAccessLogFilter> apiAccessLogFilter(WebProperties webProperties,
                                                                         @Value("${spring.application.name}") String applicationName,
                                                                         ApiAccessLogCommonApi apiAccessLogApi) {
        ApiAccessLogFilter filter = new ApiAccessLogFilter(webProperties, applicationName, apiAccessLogApi);
        return createFilterBean(filter, WebFilterOrderEnum.API_ACCESS_LOG_FILTER);
    }

    private static <T extends Filter> FilterRegistrationBean<T> createFilterBean(T filter, Integer order) {
        FilterRegistrationBean<T> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(order);
        return bean;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiAccessLogInterceptor());
    }

}
