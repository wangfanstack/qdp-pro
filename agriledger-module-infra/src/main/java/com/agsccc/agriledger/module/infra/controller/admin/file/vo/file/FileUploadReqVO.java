package com.agsccc.agriledger.module.infra.controller.admin.file.vo.file;

import com.agsccc.agriledger.module.infra.framework.file.core.enums.FileCategoryEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "管理后台 - 上传文件 Request VO")
@Data
public class FileUploadReqVO {

    @Schema(description = "文件附件", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "文件附件不能为空")
    private MultipartFile file;

    @Schema(description = "文件分类（video/document/image/attachment/export/temp），允许空：空时按文件类型自动归类",
            example = "document")
    private String category;

    @AssertTrue(message = "文件分类不正确")
    @JsonIgnore
    public boolean isCategoryValid() {
        return FileCategoryEnum.isValid(category);
    }

}
