package com.agsccc.agriledger.module.infra.convert.codegen;

import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.common.util.collection.CollectionUtils;
import com.agsccc.agriledger.module.infra.controller.admin.codegen.vo.CodegenDetailRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.codegen.vo.CodegenPreviewRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.codegen.vo.column.CodegenColumnRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.codegen.vo.column.CodegenColumnSaveReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.codegen.vo.table.CodegenTableRespVO;
import com.agsccc.agriledger.module.infra.controller.admin.codegen.vo.table.CodegenTableSaveReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.codegen.vo.table.DatabaseTableRespVO;
import com.agsccc.agriledger.module.infra.dal.dataobject.codegen.CodegenColumnDO;
import com.agsccc.agriledger.module.infra.dal.dataobject.codegen.CodegenTableDO;
import com.baomidou.mybatisplus.generator.config.po.TableField;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import org.apache.ibatis.type.JdbcType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Map;

@Mapper
public interface CodegenConvert {

    CodegenConvert INSTANCE = Mappers.getMapper(CodegenConvert.class);

    // ========== TableInfo 相关 ==========

    @Mappings({
            @Mapping(source = "name", target = "tableName"),
            @Mapping(source = "comment", target = "tableComment"),
    })
    CodegenTableDO convert(TableInfo bean);

    List<CodegenColumnDO> convertList(List<TableField> list);

    @Mappings({
            @Mapping(source = "name", target = "columnName"),
            @Mapping(source = "metaInfo.jdbcType", target = "dataType", qualifiedByName = "getDataType"),
            @Mapping(source = "comment", target = "columnComment"),
            @Mapping(source = "metaInfo.nullable", target = "nullable"),
            @Mapping(source = "keyFlag", target = "primaryKey"),
            @Mapping(source = "columnType.type", target = "javaType"),
            @Mapping(source = "propertyName", target = "javaField"),
    })
    CodegenColumnDO convert(TableField bean);

    @Named("getDataType")
    default String getDataType(JdbcType jdbcType) {
        return jdbcType.name();
    }

    List<DatabaseTableRespVO> convertDatabaseTableList(List<TableInfo> list);

    DatabaseTableRespVO convertDatabaseTable(TableInfo bean);

    // ========== 表定义相关 ==========

    CodegenTableDO convert(CodegenTableSaveReqVO bean);

    CodegenTableRespVO convertTable(CodegenTableDO bean);

    List<CodegenTableRespVO> convertTableList(List<CodegenTableDO> list);

    default PageResult<CodegenTableRespVO> convertTablePage(PageResult<CodegenTableDO> pageResult) {
        return new PageResult<>(convertTableList(pageResult.getList()), pageResult.getTotal());
    }

    // ========== 字段定义相关 ==========

    List<CodegenColumnDO> convertColumnDOList(List<CodegenColumnSaveReqVO> list);

    CodegenColumnRespVO convertColumn(CodegenColumnDO bean);

    List<CodegenColumnRespVO> convertColumnList(List<CodegenColumnDO> list);

    // ========== 其它 ==========

    default CodegenDetailRespVO convert(CodegenTableDO table, List<CodegenColumnDO> columns) {
        CodegenDetailRespVO respVO = new CodegenDetailRespVO();
        respVO.setTable(convertTable(table));
        respVO.setColumns(convertColumnList(columns));
        return respVO;
    }

    default List<CodegenPreviewRespVO> convert(Map<String, String> codes) {
        return CollectionUtils.convertList(codes.entrySet(),
                entry -> new CodegenPreviewRespVO().setFilePath(entry.getKey()).setCode(entry.getValue()));
    }

}
