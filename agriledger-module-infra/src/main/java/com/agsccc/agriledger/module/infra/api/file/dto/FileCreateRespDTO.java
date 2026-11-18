package com.agsccc.agriledger.module.infra.api.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件保存结果 DTO
 *
 * <p>需要回写文件编号做关联时使用（如公证材料 notary_material.file_id），仅需访问路径时用 {@code FileApi#createFile}。
 *
 * @author Sail
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileCreateRespDTO {

    /**
     * 文件编号（infra_file.id）
     */
    private Long fileId;

    /**
     * 文件访问路径
     */
    private String url;

}
