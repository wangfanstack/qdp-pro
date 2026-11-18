package com.agsccc.agriledger.module.infra.framework.monitor.config;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.StringUtils;

/**
 * Spring Boot Admin Server 配置
 *
 * 包含 Admin Server 的启用配置和安全配置
 * 安全配置独立于 {@link com.agsccc.agriledger.framework.security.config.HftWebSecurityConfigurerAdapter}，
 * 使用 HTTP Basic 认证保护 Admin Server 端点，不影响现有的 Token 认证机制
 *
 * @author Sail
 */
@Configuration(proxyBeanMethods = false)
@EnableAdminServer
@ConditionalOnClass(name = "de.codecentric.boot.admin.server.config.AdminServerProperties") // 目的：按需启动 spring boot admin 监控服务
public class AdminServerConfiguration {

    @Value("${spring.boot.admin.context-path:''}")
    private String adminSeverContextPath;

    @Value("${management.endpoints.web.base-path:/actuator}")
    private String actuatorBasePath;

    @Value("${spring.boot.admin.client.username:admin}")
    private String username;

    @Value("${spring.boot.admin.client.password:admin}")
    private String password;

    @Value("${spring.boot.admin.frame-ancestors:'self'}")
    private String frameAncestors;

    /**
     * CSP script-src 追加来源，用于将 Scalar JS 等前端静态资源托管到独立静态域名。
     * <p>
     * 仅配置可信静态域名；多个来源使用空格分隔。
     */
    @Value("${hft.swagger.script-src-extra:}")
    private String scriptSrcExtra;

    /**
     * CSP font-src 追加来源，用于允许 Scalar UI 加载官方字体。
     * <p>
     * 仅配置可信静态资源域名；多个来源使用空格分隔。
     */
    @Value("${hft.swagger.font-src-extra:}")
    private String fontSrcExtra;

    /**
     * Spring Boot Admin 专用的 InMemoryUserDetailsManager
     * 使用内存存储，与系统用户隔离
     */
    @Bean("adminUserDetailsManager")
    public InMemoryUserDetailsManager adminUserDetailsManager(PasswordEncoder passwordEncoder) {
        UserDetails adminUser = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .roles("ADMIN_SERVER")
                .build();
        return new InMemoryUserDetailsManager(adminUser);
    }

    /**
     * Spring Boot Admin Server 的 SecurityFilterChain
     * 使用 @Order(1) 确保优先于默认的 SecurityFilterChain 匹配
     */
    @Bean("adminServerSecurityFilterChain")
    @Order(1)
    public SecurityFilterChain adminServerSecurityFilterChain(HttpSecurity httpSecurity,
                                                               InMemoryUserDetailsManager adminUserDetailsManager) throws Exception {
        // 登录成功后的处理器
        SavedRequestAwareAuthenticationSuccessHandler successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        successHandler.setTargetUrlParameter("redirectTo");
        successHandler.setDefaultTargetUrl(adminSeverContextPath + "/");

        // 配置 HttpSecurity 对象
        httpSecurity
                // 匹配 Admin Server、Actuator 与 API 文档的路径：三者同属运维端点，统一由本认证链保护，
                // 避免 loggers（动态改日志级别）、beans、mappings 等端点以及接口元数据被匿名访问
                .securityMatcher(
                        adminSeverContextPath + "/**",
                        actuatorBasePath,
                        actuatorBasePath + "/**",
                        "/scalar/**",
                        "/v3/api-docs/**",
                        "/favicon.svg")
                // 使用独立的 UserDetailsManager
                .userDetailsService(adminUserDetailsManager)
                // 授权配置
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(adminSeverContextPath + "/assets/**").permitAll() // 静态资源允许匿名访问
                        .requestMatchers(adminSeverContextPath + "/login").permitAll() // 登录页面允许匿名访问
                        .requestMatchers("/favicon.svg").permitAll() // Scalar UI 默认站点图标，无需登录
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll() // 异步请求允许
                        .anyRequest().authenticated() // 其他请求需要认证
                )
                // 表单登录配置（用于 Admin UI 访问）
                .formLogin(form -> form
                        .loginPage(adminSeverContextPath + "/login")
                        .successHandler(successHandler)
                        .permitAll()
                )
                // 登出配置
                .logout(logout -> logout
                        .logoutUrl(adminSeverContextPath + "/logout")
                        .logoutSuccessUrl(adminSeverContextPath + "/login")
                )
                // HTTP Basic 认证（用于 Admin Client 注册）
                .httpBasic(Customizer.withDefaults())
                // CSRF 配置
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(
                                adminSeverContextPath + "/instances", // Admin Client 注册端点忽略 CSRF
                                adminSeverContextPath + "/actuator/**", // Actuator 端点忽略 CSRF
                                actuatorBasePath + "/**" // 应用自身的 Actuator 端点忽略 CSRF（loggers 动态调级、SBA 回访均为非浏览器请求）
                        )
                )
                .headers(headers -> headers
                        // 特殊：Spring Boot Admin 前端基于 Vue，需 unsafe-inline ／ unsafe-eval 支持内联脚本与表达式
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + buildScriptSrcDirective()
                                        + buildFontSrcDirective()
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "frame-ancestors " + frameAncestors))
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin) // 显式设置 X-Frame-Options 为 SAMEORIGIN
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable) // 禁用缓存，避免旧配置生效
                );
        return httpSecurity.build();
    }

    private String buildScriptSrcDirective() {
        String sources = "'self'";
        if (StringUtils.hasText(scriptSrcExtra)) {
            sources += " " + scriptSrcExtra.trim();
        }
        return "script-src " + sources + " 'unsafe-inline' 'unsafe-eval'; ";
    }

    private String buildFontSrcDirective() {
        String sources = "'self'";
        if (StringUtils.hasText(fontSrcExtra)) {
            sources += " " + fontSrcExtra.trim();
        }
        return "font-src " + sources + "; ";
    }

}
