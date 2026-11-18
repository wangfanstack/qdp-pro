package com.agsccc.agriledger.module.system.enums.oauth2;

/**
 * OAuth2.0 客户端的通用枚举
 *
 * @author 农E链源码
 */
public interface OAuth2ClientConstants {

    String CLIENT_ID_DEFAULT = "default";

    /**
     * APP 管理端专用 client：Web/APP 双端会话隔离的载体（access 2h / refresh 30d，
     * 有效期由 system_oauth2_client 表动态配置，种子见 sql/mysql/increment/20261106_06）
     */
    String CLIENT_ID_APP = "app";

}
