package com.agsccc.agriledger.module.system.api.account.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 全局账号 Response DTO
 *
 * @author 农E链源码
 */
@Data
@Accessors(chain = true)
public class SystemAccountRespDTO implements Serializable {

    /**
     * 全局账号编号
     */
    private Long id;
    /**
     * 手机号
     */
    private String mobile;
    /**
     * 账号状态（0正常 1停用）
     */
    private Integer status;

}
