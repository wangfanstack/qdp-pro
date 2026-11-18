package com.agsccc.agriledger.module.infra.api.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件信息 DTO
 *
 * <p>跨模块按编号引用 infra_file.id 时的存在性校验/轻量展示信息（如公证材料 file_id 绑定前校验）。
 *
 * @author Sail
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileRespDTO {

    /**
     * 文件编号（infra_file.id）
     */
    private Long id;

    /**
     * 原文件名
     */
    private String name;

    /**
     * 文件访问路径
     */
    private String url;

}
