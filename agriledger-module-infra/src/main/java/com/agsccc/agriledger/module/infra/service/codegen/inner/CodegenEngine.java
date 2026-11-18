package com.agsccc.agriledger.module.infra.service.codegen.inner;

import com.agsccc.agriledger.framework.apilog.core.annotation.ApiAccessLog;
import com.agsccc.agriledger.framework.apilog.core.enums.OperateTypeEnum;
import com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils;
import com.agsccc.agriledger.framework.common.pojo.CommonResult;
import com.agsccc.agriledger.framework.common.pojo.PageParam;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.common.util.collection.CollectionUtils;
import com.agsccc.agriledger.framework.common.util.date.DateUtils;
import com.agsccc.agriledger.framework.common.util.date.LocalDateTimeUtils;
import com.agsccc.agriledger.framework.common.util.object.ObjectUtils;
import com.agsccc.agriledger.framework.common.util.string.StrUtils;
import com.agsccc.agriledger.framework.excel.core.annotations.DictFormat;
import com.agsccc.agriledger.framework.excel.core.convert.DictConvert;
import com.agsccc.agriledger.framework.excel.core.util.ExcelUtils;
import com.agsccc.agriledger.framework.mybatis.core.dataobject.BaseDO;
import com.agsccc.agriledger.framework.mybatis.core.mapper.BaseMapperX;
import com.agsccc.agriledger.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.agsccc.agriledger.module.infra.dal.dataobject.codegen.CodegenColumnDO;
import com.agsccc.agriledger.module.infra.dal.dataobject.codegen.CodegenTableDO;
import com.agsccc.agriledger.module.infra.enums.codegen.CodegenFrontTypeEnum;
import com.agsccc.agriledger.module.infra.enums.codegen.CodegenSceneEnum;
import com.agsccc.agriledger.module.infra.enums.codegen.CodegenTemplateTypeEnum;
import com.agsccc.agriledger.module.infra.enums.codegen.CodegenVOTypeEnum;
import com.agsccc.agriledger.module.infra.framework.codegen.config.CodegenProperties;
import com.baomidou.mybatisplus.annotation.DbType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 代码生成的引擎，用于具体生成代码
 * 目前基于 {@link org.apache.velocity.app.Velocity} 模板引擎实现，模板从 classpath 的 codegen 目录加载
 *
 * @author Sail
 */
@Component
public class CodegenEngine {

    /**
     * 后端的模板配置
     *
     * key：模板在 resources 的地址
     * value：生成的路径
     */
    private static final Map<String, String> SERVER_TEMPLATES = buildServerTemplates(); // 有序

    /**
     * 前端的配置模版
     *
     * key1：UI 模版的类型 {@link CodegenFrontTypeEnum#getType()}
     * key2：模板在 resources 的地址
     * value：生成的路径
     */
    private static final Table<Integer, String, String> FRONT_TEMPLATES = ImmutableTable.<Integer, String, String>builder()
            // VUE2_ELEMENT_UI
            .put(CodegenFrontTypeEnum.VUE2_ELEMENT_UI.getType(), vueTemplatePath("views/index.vue"),
                    vueFilePath("views/${table.moduleName}/${table.businessName}/index.vue"))
            .put(CodegenFrontTypeEnum.VUE2_ELEMENT_UI.getType(), vueTemplatePath("api/api.js"),
                    vueFilePath("api/${table.moduleName}/${table.businessName}/index.js"))
            .put(CodegenFrontTypeEnum.VUE2_ELEMENT_UI.getType(), vueTemplatePath("views/form.vue"),
                    vueFilePath("views/${table.moduleName}/${table.businessName}/${simpleClassName}Form.vue"))
            .put(CodegenFrontTypeEnum.VUE2_ELEMENT_UI.getType(), vueTemplatePath("views/components/form_sub_normal.vue"),  // 特殊：主子表专属逻辑
                    vueFilePath("views/${table.moduleName}/${table.businessName}/components/${subSimpleClassName}Form.vue"))
            .put(CodegenFrontTypeEnum.VUE2_ELEMENT_UI.getType(), vueTemplatePath("views/components/form_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vueFilePath("views/${table.moduleName}/${table.businessName}/components/${subSimpleClassName}Form.vue"))
            .put(CodegenFrontTypeEnum.VUE2_ELEMENT_UI.getType(), vueTemplatePath("views/components/form_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vueFilePath("views/${table.moduleName}/${table.businessName}/components/${subSimpleClassName}Form.vue"))
            .put(CodegenFrontTypeEnum.VUE2_ELEMENT_UI.getType(), vueTemplatePath("views/components/list_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vueFilePath("views/${table.moduleName}/${table.businessName}/components/${subSimpleClassName}List.vue"))
            .put(CodegenFrontTypeEnum.VUE2_ELEMENT_UI.getType(), vueTemplatePath("views/components/list_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vueFilePath("views/${table.moduleName}/${table.businessName}/components/${subSimpleClassName}List.vue"))
            // VUE3_ELEMENT_PLUS
            .put(CodegenFrontTypeEnum.VUE3_ELEMENT_PLUS.getType(), vue3TemplatePath("views/index.vue"),
                    vue3FilePath("views/${table.moduleName}/${table.businessName}/index.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ELEMENT_PLUS.getType(), vue3TemplatePath("views/form.vue"),
                    vue3FilePath("views/${table.moduleName}/${table.businessName}/${simpleClassName}Form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ELEMENT_PLUS.getType(), vue3TemplatePath("views/import.vue"),
                    vue3FilePath("views/${table.moduleName}/${table.businessName}/${simpleClassName}ImportForm.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ELEMENT_PLUS.getType(), vue3TemplatePath("views/components/form_sub_normal.vue"),  // 特殊：主子表专属逻辑
                    vue3FilePath("views/${table.moduleName}/${table.businessName}/components/${subSimpleClassName}Form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ELEMENT_PLUS.getType(), vue3TemplatePath("views/components/form_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3FilePath("views/${table.moduleName}/${table.businessName}/components/${subSimpleClassName}Form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ELEMENT_PLUS.getType(), vue3TemplatePath("views/components/form_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3FilePath("views/${table.moduleName}/${table.businessName}/components/${subSimpleClassName}Form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ELEMENT_PLUS.getType(), vue3TemplatePath("views/components/list_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3FilePath("views/${table.moduleName}/${table.businessName}/components/${subSimpleClassName}List.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ELEMENT_PLUS.getType(), vue3TemplatePath("views/components/list_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3FilePath("views/${table.moduleName}/${table.businessName}/components/${subSimpleClassName}List.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ELEMENT_PLUS.getType(), vue3TemplatePath("api/api.ts"),
                    vue3FilePath("api/${table.moduleName}/${table.businessName}/index.ts"))
            .put(CodegenFrontTypeEnum.VUE3_ADMIN_UNIAPP_WOT.getType(), vue3AdminUniappTemplatePath("api/api.ts"),
                    vue3UniappFilePath("api/${table.moduleName}/${table.businessName}/index.ts"))
            .put(CodegenFrontTypeEnum.VUE3_ADMIN_UNIAPP_WOT.getType(), vue3AdminUniappTemplatePath("views/index.vue"),
                    vue3UniappFilePath("pages-${table.moduleName}/${table.businessName}/index.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ADMIN_UNIAPP_WOT.getType(), vue3AdminUniappTemplatePath("components/search-form.vue"),
                    vue3UniappFilePath("pages-${table.moduleName}/${table.businessName}/components/search-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ADMIN_UNIAPP_WOT.getType(), vue3AdminUniappTemplatePath("views/form/index.vue"),
                    vue3UniappFilePath("pages-${table.moduleName}/${table.businessName}/form/index.vue"))
            .put(CodegenFrontTypeEnum.VUE3_ADMIN_UNIAPP_WOT.getType(), vue3AdminUniappTemplatePath("views/detail/index.vue"),
                    vue3UniappFilePath("pages-${table.moduleName}/${table.businessName}/detail/index.vue"))
            // VUE3_VBEN2_ANTD_SCHEMA
            .put(CodegenFrontTypeEnum.VUE3_VBEN2_ANTD_SCHEMA.getType(), vue3VbenTemplatePath("views/data.ts"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/${classNameVar}.data.ts"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN2_ANTD_SCHEMA.getType(), vue3VbenTemplatePath("views/index.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/index.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN2_ANTD_SCHEMA.getType(), vue3VbenTemplatePath("views/form.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/${simpleClassName}Modal.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN2_ANTD_SCHEMA.getType(), vue3VbenTemplatePath("api/api.ts"),
                    vue3VbenFilePath("api/${table.moduleName}/${table.businessName}/index.ts"))
            // VUE3_VBEN5_ANTD_SCHEMA
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_SCHEMA.getType(), vue3Vben5AntdSchemaTemplatePath("views/data.ts"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/data.ts"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_SCHEMA.getType(), vue3Vben5AntdSchemaTemplatePath("views/index.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/index.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_SCHEMA.getType(), vue3Vben5AntdSchemaTemplatePath("views/form.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_SCHEMA.getType(), vue3Vben5AntdSchemaTemplatePath("views/import.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/import-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_SCHEMA.getType(), vue3Vben5AntdSchemaTemplatePath("api/api.ts"),
                    vue3VbenFilePath("api/${table.moduleName}/${table.businessName}/index.ts"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_SCHEMA.getType(), vue3Vben5AntdSchemaTemplatePath("views/modules/form_sub_normal.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_SCHEMA.getType(), vue3Vben5AntdSchemaTemplatePath("views/modules/form_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_SCHEMA.getType(), vue3Vben5AntdSchemaTemplatePath("views/modules/form_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_SCHEMA.getType(), vue3Vben5AntdSchemaTemplatePath("views/modules/list_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_SCHEMA.getType(), vue3Vben5AntdSchemaTemplatePath("views/modules/list_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            // VUE3_VBEN5_ANTD
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_GENERAL.getType(), vue3Vben5AntdGeneralTemplatePath("views/index.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/index.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_GENERAL.getType(), vue3Vben5AntdGeneralTemplatePath("views/form.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_GENERAL.getType(), vue3Vben5AntdGeneralTemplatePath("views/import.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/import-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_GENERAL.getType(), vue3Vben5AntdGeneralTemplatePath("api/api.ts"),
                    vue3VbenFilePath("api/${table.moduleName}/${table.businessName}/index.ts"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_GENERAL.getType(), vue3Vben5AntdGeneralTemplatePath("views/modules/form_sub_normal.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_GENERAL.getType(), vue3Vben5AntdGeneralTemplatePath("views/modules/form_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_GENERAL.getType(), vue3Vben5AntdGeneralTemplatePath("views/modules/form_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_GENERAL.getType(), vue3Vben5AntdGeneralTemplatePath("views/modules/list_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTD_GENERAL.getType(), vue3Vben5AntdGeneralTemplatePath("views/modules/list_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            // VUE3_VBEN5_EP_SCHEMA
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_SCHEMA.getType(), vue3Vben5EpSchemaTemplatePath("views/data.ts"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/data.ts"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_SCHEMA.getType(), vue3Vben5EpSchemaTemplatePath("views/index.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/index.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_SCHEMA.getType(), vue3Vben5EpSchemaTemplatePath("views/form.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_SCHEMA.getType(), vue3Vben5EpSchemaTemplatePath("views/import.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/import-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_SCHEMA.getType(), vue3Vben5EpSchemaTemplatePath("api/api.ts"),
                    vue3VbenFilePath("api/${table.moduleName}/${table.businessName}/index.ts"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_SCHEMA.getType(), vue3Vben5EpSchemaTemplatePath("views/modules/form_sub_normal.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_SCHEMA.getType(), vue3Vben5EpSchemaTemplatePath("views/modules/form_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_SCHEMA.getType(), vue3Vben5EpSchemaTemplatePath("views/modules/form_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_SCHEMA.getType(), vue3Vben5EpSchemaTemplatePath("views/modules/list_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_SCHEMA.getType(), vue3Vben5EpSchemaTemplatePath("views/modules/list_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            // VUE3_VBEN5_EP
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_GENERAL.getType(), vue3Vben5EpGeneralTemplatePath("views/index.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/index.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_GENERAL.getType(), vue3Vben5EpGeneralTemplatePath("views/form.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_GENERAL.getType(), vue3Vben5EpGeneralTemplatePath("views/import.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/import-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_GENERAL.getType(), vue3Vben5EpGeneralTemplatePath("api/api.ts"),
                    vue3VbenFilePath("api/${table.moduleName}/${table.businessName}/index.ts"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_GENERAL.getType(), vue3Vben5EpGeneralTemplatePath("views/modules/form_sub_normal.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_GENERAL.getType(), vue3Vben5EpGeneralTemplatePath("views/modules/form_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_GENERAL.getType(), vue3Vben5EpGeneralTemplatePath("views/modules/form_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_GENERAL.getType(), vue3Vben5EpGeneralTemplatePath("views/modules/list_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_EP_GENERAL.getType(), vue3Vben5EpGeneralTemplatePath("views/modules/list_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            // VUE3_VBEN5_ANTDV_NEXT_SCHEMA
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_SCHEMA.getType(), vue3Vben5AntdvNextSchemaTemplatePath("views/data.ts"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/data.ts"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_SCHEMA.getType(), vue3Vben5AntdvNextSchemaTemplatePath("views/index.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/index.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_SCHEMA.getType(), vue3Vben5AntdvNextSchemaTemplatePath("views/form.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_SCHEMA.getType(), vue3Vben5AntdvNextSchemaTemplatePath("views/import.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/import-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_SCHEMA.getType(), vue3Vben5AntdvNextSchemaTemplatePath("api/api.ts"),
                    vue3VbenFilePath("api/${table.moduleName}/${table.businessName}/index.ts"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_SCHEMA.getType(), vue3Vben5AntdvNextSchemaTemplatePath("views/modules/form_sub_normal.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_SCHEMA.getType(), vue3Vben5AntdvNextSchemaTemplatePath("views/modules/form_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_SCHEMA.getType(), vue3Vben5AntdvNextSchemaTemplatePath("views/modules/form_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_SCHEMA.getType(), vue3Vben5AntdvNextSchemaTemplatePath("views/modules/list_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_SCHEMA.getType(), vue3Vben5AntdvNextSchemaTemplatePath("views/modules/list_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            // VUE3_VBEN5_ANTDV_NEXT_GENERAL
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_GENERAL.getType(), vue3Vben5AntdvNextGeneralTemplatePath("views/index.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/index.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_GENERAL.getType(), vue3Vben5AntdvNextGeneralTemplatePath("views/form.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_GENERAL.getType(), vue3Vben5AntdvNextGeneralTemplatePath("views/import.vue"),
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/import-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_GENERAL.getType(), vue3Vben5AntdvNextGeneralTemplatePath("api/api.ts"),
                    vue3VbenFilePath("api/${table.moduleName}/${table.businessName}/index.ts"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_GENERAL.getType(), vue3Vben5AntdvNextGeneralTemplatePath("views/modules/form_sub_normal.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_GENERAL.getType(), vue3Vben5AntdvNextGeneralTemplatePath("views/modules/form_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_GENERAL.getType(), vue3Vben5AntdvNextGeneralTemplatePath("views/modules/form_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-form.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_GENERAL.getType(), vue3Vben5AntdvNextGeneralTemplatePath("views/modules/list_sub_inner.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            .put(CodegenFrontTypeEnum.VUE3_VBEN5_ANTDV_NEXT_GENERAL.getType(), vue3Vben5AntdvNextGeneralTemplatePath("views/modules/list_sub_erp.vue"),  // 特殊：主子表专属逻辑
                    vue3VbenFilePath("views/${table.moduleName}/${table.businessName}/modules/${subSimpleClassName_strikeCase}-list.vue"))
            .build();

    @Resource
    private CodegenProperties codegenProperties;

    /**
     * 是否使用 jakarta 包，用于解决 Spring Boot 2.X 和 3.X 的兼容性问题
     *
     * true  - 使用 jakarta.validation.constraints.*
     * false - 使用 javax.validation.constraints.*
     */
    @Setter // 允许设置的原因，是因为单测需要手动改变
    private Boolean jakartaEnable;

    /**
     * 是否为 hft-cloud 项目，用于解决 Boot 和 Cloud 的 api 模块兼容性问题
     *
     * true  - 需要有 hft-module-xxx-api 模块
     * false - 不需要有，使用 api、enum 包即可
     */
    @Setter
    private Boolean cloudEnable;

    /**
     * 模板引擎，基于 Velocity 的 classpath 加载实现
     */
    private final VelocityEngine templateEngine;
    /**
     * 全局通用变量映射
     */
    private final Map<String, Object> globalBindingMap = new HashMap<>();

    public CodegenEngine() {
        // 初始化 VelocityEngine，从 classpath 加载模板，与模板文件的实际存储保持一致
        Properties properties = new Properties();
        properties.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        properties.setProperty(RuntimeConstants.RESOURCE_LOADER + ".classpath.class",
                ClasspathResourceLoader.class.getName());
        properties.setProperty(RuntimeConstants.INPUT_ENCODING, StandardCharsets.UTF_8.name());
        this.templateEngine = new VelocityEngine(properties);
        // 设置 jakartaEnable，按照是否使用 JDK17 来判断
        this.jakartaEnable = Runtime.version().feature() >= 17
                && ClassUtils.isPresent("jakarta.annotation.Resource", ClassUtils.getDefaultClassLoader());
        // 设置 cloudEnable，按照是否使用 Spring Cloud 来判断
        this.cloudEnable = ClassUtils.isPresent("com.agsccc.agriledger.module.infra.framework.rpc.config.RpcConfiguration",
                ClassUtils.getDefaultClassLoader());
    }

    @PostConstruct
    @VisibleForTesting
    void initGlobalBindingMap() {
        // 全局配置
        globalBindingMap.put("basePackage", codegenProperties.getBasePackage());
        globalBindingMap.put("baseFrameworkPackage", codegenProperties.getBasePackage()
                + '.' + "framework"); // 用于后续获取测试类的 package 地址
        globalBindingMap.put("jakartaPackage", jakartaEnable ? "jakarta" : "javax");
        globalBindingMap.put("voType", codegenProperties.getVoType());
        globalBindingMap.put("deleteBatchEnable", codegenProperties.getDeleteBatchEnable());
        globalBindingMap.put("importEnable", codegenProperties.getImportEnable());
        // 全局 Java Bean
        globalBindingMap.put("CommonResultClassName", CommonResult.class.getName());
        globalBindingMap.put("PageResultClassName", PageResult.class.getName());
        // VO 类，独有字段
        globalBindingMap.put("PageParamClassName", PageParam.class.getName());
        globalBindingMap.put("DictFormatClassName", DictFormat.class.getName());
        // DO 类，独有字段
        globalBindingMap.put("BaseDOClassName", BaseDO.class.getName());
        globalBindingMap.put("baseDOFields", CodegenBuilder.BASE_DO_FIELDS);
        globalBindingMap.put("QueryWrapperClassName", LambdaQueryWrapperX.class.getName());
        globalBindingMap.put("BaseMapperClassName", BaseMapperX.class.getName());
        // Util 工具类
        globalBindingMap.put("ServiceExceptionUtilClassName", ServiceExceptionUtils.class.getName());
        globalBindingMap.put("DateUtilsClassName", DateUtils.class.getName());
        globalBindingMap.put("ExcelUtilsClassName", ExcelUtils.class.getName());
        globalBindingMap.put("LocalDateTimeUtilsClassName", LocalDateTimeUtils.class.getName());
        globalBindingMap.put("ObjectUtilsClassName", ObjectUtils.class.getName());
        globalBindingMap.put("DictConvertClassName", DictConvert.class.getName());
        globalBindingMap.put("ApiAccessLogClassName", ApiAccessLog.class.getName());
        globalBindingMap.put("OperateTypeEnumClassName", OperateTypeEnum.class.getName());
        globalBindingMap.put("CollectionUtilsClassName", CollectionUtils.class.getName());
    }

    /**
     * 生成代码
     *
     * @param dbType         数据库类型
     * @param table          表定义
     * @param columns        table 的字段定义数组
     * @param subTables      子表数组，当且仅当主子表时使用
     * @param subColumnsList subTables 的字段定义数组
     * @return 生成的代码，key 是路径，value 是对应代码
     */
    public Map<String, String> execute(DbType dbType, CodegenTableDO table, List<CodegenColumnDO> columns,
                                       List<CodegenTableDO> subTables, List<List<CodegenColumnDO>> subColumnsList) {
        // 1.1 初始化 bindMap 上下文
        Map<String, Object> bindingMap = initBindingMap(dbType, table, columns, subTables, subColumnsList);
        // 1.2 获得模版
        Map<String, String> templates = getTemplates(table.getFrontType());

        // 2. 执行生成
        Map<String, String> result = Maps.newLinkedHashMapWithExpectedSize(templates.size()); // 有序
        templates.forEach((vmPath, filePath) -> {
            // 2.1 特殊：主子表专属逻辑
            if (isSubTemplate(vmPath)) {
                generateSubCode(table, subTables, result, vmPath, filePath, bindingMap);
                return;
                // 2.2 特殊：树表专属逻辑
            } else if (isPageReqVOTemplate(vmPath)) {
                // 减少多余的类生成，例如说 PageVO.java 类
                if (CodegenTemplateTypeEnum.isTree(table.getTemplateType())) {
                    return;
                }
            } else if (isListReqVOTemplate(vmPath)) {
                // 减少多余的类生成，例如说 ListVO.java 类
                if (!CodegenTemplateTypeEnum.isTree(table.getTemplateType())) {
                    return;
                }
            } else if (isImportTemplate(vmPath)) {
                // 关闭 import 时，跳过 ImportExcelVO / ImportRespVO 的生成
                if (!Boolean.TRUE.equals(codegenProperties.getImportEnable())) {
                    return;
                }
            }
            // 2.3 默认生成
            generateCode(result, vmPath, filePath, bindingMap);
        });
        return result;
    }

    private void generateCode(Map<String, String> result, String vmPath,
                              String filePath, Map<String, Object> bindingMap) {
        filePath = formatFilePath(filePath, bindingMap);
        String content = renderTemplate(vmPath, bindingMap);
        // 格式化代码
        content = prettyCode(content, vmPath);
        result.put(filePath, content);
    }

    /**
     * 使用 Velocity 渲染 classpath 下的模板
     */
    private String renderTemplate(String vmPath, Map<String, Object> bindingMap) {
        Template template = templateEngine.getTemplate(vmPath, StandardCharsets.UTF_8.name());
        StringWriter writer = new StringWriter();
        template.merge(new VelocityContext(bindingMap), writer);
        return writer.toString();
    }

    private void generateSubCode(CodegenTableDO table, List<CodegenTableDO> subTables,
                                 Map<String, String> result, String vmPath,
                                 String filePath, Map<String, Object> bindingMap) {
        // 没有子表，所以不生成
        if (subTables == null || subTables.isEmpty()) {
            return;
        }
        // 主子表的模式匹配。目的：过滤掉个性化的模版
        if (vmPath.contains("_normal")
                && !Objects.equals(table.getTemplateType(), CodegenTemplateTypeEnum.MASTER_NORMAL.getType())) {
            return;
        }
        if (vmPath.contains("_erp")
                && !Objects.equals(table.getTemplateType(), CodegenTemplateTypeEnum.MASTER_ERP.getType())) {
            return;
        }
        if (vmPath.contains("_inner")
                && !Objects.equals(table.getTemplateType(), CodegenTemplateTypeEnum.MASTER_INNER.getType())) {
            return;
        }

        // 逐个生成
        for (int i = 0; i < subTables.size(); i++) {
            bindingMap.put("subIndex", i);
            generateCode(result, vmPath, filePath, bindingMap);
        }
        bindingMap.remove("subIndex");
    }

    /**
     * 格式化生成后的代码
     *
     * 因为尽量让 vm 模版简单，所以统一的处理都在这个方法。
     * 如果不处理，Vue 的 Pretty 格式校验可能会报错
     *
     * @param content 格式化前的代码
     * @param vmPath 模板路径
     * @return 格式化后的代码
     */
    private String prettyCode(String content, String vmPath) {
        // Vue 界面：去除字段后面多余的 , 逗号，解决前端的 Pretty 代码格式检查的报错（需要排除 vben5、vue3_admin_uniapp）
        if (!StringUtils.containsAny(vmPath, "vben5", "vue3_admin_uniapp")) {
            content = content.replaceAll(",\\r?\\n}", "\n}").replaceAll(",\\r?\\n  }", "\n  }");
        }
        // Vue 界面：去除多的 dateFormatter，只有一个的情况下，说明没使用到
        if (StringUtils.countMatches(content, "dateFormatter") == 1) {
            content = StrUtils.removeLineContains(content, "dateFormatter");
        }
        // Vue2 界面：修正 $refs
        if (StringUtils.countMatches(content, "this.refs") >= 1) {
            content = content.replace("this.refs", "this.$refs");
        }
        // Vue 界面：去除多的 dict 相关，只有一个的情况下，说明没使用到
        if (StringUtils.countMatches(content, "getIntDictOptions") == 1) {
            content = content.replace("getIntDictOptions, ", "");
        }
        if (StringUtils.countMatches(content, "getStrDictOptions") == 1) {
            content = content.replace("getStrDictOptions, ", "");
        }
        if (StringUtils.countMatches(content, "getBoolDictOptions") == 1) {
            content = content.replace("getBoolDictOptions, ", "");
        }
        if (StringUtils.countMatches(content, "DICT_TYPE.") == 0) {
            content = StrUtils.removeLineContains(content, "DICT_TYPE");
        }
        return content;
    }

    private Map<String, Object> initBindingMap(DbType dbType, CodegenTableDO table, List<CodegenColumnDO> columns,
                                               List<CodegenTableDO> subTables, List<List<CodegenColumnDO>> subColumnsList) {
        // 创建 bindingMap
        Map<String, Object> bindingMap = new HashMap<>(globalBindingMap);
        bindingMap.put("dbType", dbType);
        bindingMap.put("table", table);
        bindingMap.put("columns", columns);
        bindingMap.put("primaryColumn", CollectionUtils.findFirst(columns, CodegenColumnDO::getPrimaryKey)); // 主键字段
        bindingMap.put("sceneEnum", CodegenSceneEnum.valueOf(table.getScene()));
        // className 相关
        // 去掉指定前缀，将 TestDictType 转换成 DictType. 因为在 create 等方法后，不需要带上 Test 前缀
        String className = table.getClassName();
        String simpleClassName = StringUtils.equalsAnyIgnoreCase(table.getClassName(), table.getModuleName()) ? table.getClassName()
                : StringUtils.removeStart(table.getClassName(), StringUtils.capitalize(table.getModuleName()));
        String classNameVar = StringUtils.uncapitalize(simpleClassName);
        bindingMap.put("simpleClassName", simpleClassName);
        bindingMap.put("simpleClassName_underlineCase", toUnderlineCase(simpleClassName)); // 将 DictType 转换成 dict_type
        bindingMap.put("classNameVar", classNameVar); // 将 DictType 转换成 dictType，用于变量
        // 将 DictType 转换成 dict-type
        String simpleClassNameStrikeCase = toSymbolCase(simpleClassName);
        bindingMap.put("simpleClassName_strikeCase", simpleClassNameStrikeCase);
        // permission 前缀
        bindingMap.put("permissionPrefix", table.getModuleName() + ":" + simpleClassNameStrikeCase);

        // 特殊：树表专属逻辑
        if (CodegenTemplateTypeEnum.isTree(table.getTemplateType())) {
            CodegenColumnDO treeParentColumn = CollectionUtils.findFirst(columns,
                    column -> Objects.equals(column.getId(), table.getTreeParentColumnId()));
            bindingMap.put("treeParentColumn", treeParentColumn);
            bindingMap.put("treeParentColumn_javaField_underlineCase", toUnderlineCase(treeParentColumn.getJavaField()));
            CodegenColumnDO treeNameColumn = CollectionUtils.findFirst(columns,
                    column -> Objects.equals(column.getId(), table.getTreeNameColumnId()));
            bindingMap.put("treeNameColumn", treeNameColumn);
            bindingMap.put("treeNameColumn_javaField_underlineCase", toUnderlineCase(treeNameColumn.getJavaField()));
        }

        // 特殊：主子表专属逻辑
        if (subTables != null && !subTables.isEmpty()) {
            // 创建 bindingMap
            bindingMap.put("subTables", subTables);
            bindingMap.put("subColumnsList", subColumnsList);
            List<CodegenColumnDO> subPrimaryColumns = new ArrayList<>();
            List<CodegenColumnDO> subJoinColumns = new ArrayList<>();
            List<String> subJoinColumnStrikeCases = new ArrayList<>();
            List<String> subSimpleClassNames = new ArrayList<>();
            List<String> subClassNameVars = new ArrayList<>();
            List<String> simpleClassNameUnderlineCases = new ArrayList<>();
            List<String> subSimpleClassNameStrikeCases = new ArrayList<>();
            for (int i = 0; i < subTables.size(); i++) {
                CodegenTableDO subTable = subTables.get(i);
                List<CodegenColumnDO> subColumns = subColumnsList.get(i);
                subPrimaryColumns.add(CollectionUtils.findFirst(subColumns, CodegenColumnDO::getPrimaryKey)); //
                CodegenColumnDO subColumn = CollectionUtils.findFirst(subColumns, // 关联的字段
                        column -> Objects.equals(column.getId(), subTable.getSubJoinColumnId()));
                subJoinColumns.add(subColumn);
                subJoinColumnStrikeCases.add(toSymbolCase(subColumn.getJavaField())); // 将 DictType 转换成 dict-type
                // className 相关
                String subSimpleClassName = StringUtils.removeStart(subTable.getClassName(), StringUtils.capitalize(subTable.getModuleName()));
                subSimpleClassNames.add(subSimpleClassName);
                simpleClassNameUnderlineCases.add(toUnderlineCase(subSimpleClassName)); // 将 DictType 转换成 dict_type
                subClassNameVars.add(StringUtils.uncapitalize(subSimpleClassName)); // 将 DictType 转换成 dictType，用于变量
                subSimpleClassNameStrikeCases.add(toSymbolCase(subSimpleClassName)); // 将 DictType 转换成 dict-type
            }
            bindingMap.put("subPrimaryColumns", subPrimaryColumns);
            bindingMap.put("subJoinColumns", subJoinColumns);
            bindingMap.put("subJoinColumn_strikeCases", subJoinColumnStrikeCases);
            bindingMap.put("subSimpleClassNames", subSimpleClassNames);
            bindingMap.put("simpleClassNameUnderlineCases", simpleClassNameUnderlineCases);
            bindingMap.put("subClassNameVars", subClassNameVars);
            bindingMap.put("subSimpleClassName_strikeCases", subSimpleClassNameStrikeCases);
        }

        // 多个 vm 公用的 VO 变量
        if (Objects.equals(codegenProperties.getVoType(), CodegenVOTypeEnum.VO.getType())) {
            String prefixClass = CodegenSceneEnum.valueOf(table.getScene()).getPrefixClass();
            bindingMap.put("saveReqVOClass", prefixClass + className + "SaveReqVO");
            bindingMap.put("updateReqVOClass", prefixClass + className + "SaveReqVO");
            bindingMap.put("respVOClass", prefixClass + className + "RespVO");
            bindingMap.put("saveReqVOVar", "createReqVO");
            bindingMap.put("updateReqVOVar", "updateReqVO");
        } else if (Objects.equals(codegenProperties.getVoType(), CodegenVOTypeEnum.DO.getType())) {
            bindingMap.put("saveReqVOClass", className + "DO");
            bindingMap.put("updateReqVOClass", className + "DO");
            bindingMap.put("respVOClass", className + "DO");
            bindingMap.put("saveReqVOVar", classNameVar);
            bindingMap.put("updateReqVOVar", classNameVar);
        }
        return bindingMap;
    }

    private Map<String, String> getTemplates(Integer frontType) {
        Map<String, String> templates = new LinkedHashMap<>();
        templates.putAll(SERVER_TEMPLATES);
        templates.putAll(FRONT_TEMPLATES.row(frontType));
        // 如果是 Boot 项目，则不使用 api/server 模块
        if (Boolean.FALSE.equals(cloudEnable)) {
            SERVER_TEMPLATES.forEach((templatePath, filePath) -> {
                filePath = StringUtils.replace(filePath, "/hft-module-${table.moduleName}-api", "");
                filePath = StringUtils.replace(filePath, "/hft-module-${table.moduleName}-server", "");
                templates.put(templatePath, filePath);
            });
        }
        // 如果禁用单元测试，则移除对应的模版
        if (Boolean.FALSE.equals(codegenProperties.getUnitTestEnable())) {
            templates.remove(javaTemplatePath("test/serviceTest"));
            templates.remove("codegen/sql/h2.vm");
        }
        // 如果禁用 VO 类型，则移除对应的模版
        if (!Objects.equals(codegenProperties.getVoType(), CodegenVOTypeEnum.VO.getType())) {
            templates.remove(javaTemplatePath("controller/vo/respVO"));
            templates.remove(javaTemplatePath("controller/vo/saveReqVO"));
        }
        return templates;
    }

    @SuppressWarnings("unchecked")
    private String formatFilePath(String filePath, Map<String, Object> bindingMap) {
        filePath = StringUtils.replace(filePath, "${basePackage}",
                ((String) bindingMap.get("basePackage")).replaceAll("\\.", "/"));
        filePath = StringUtils.replace(filePath, "${classNameVar}",
                (String) bindingMap.get("classNameVar"));
        filePath = StringUtils.replace(filePath, "${simpleClassName}",
                (String) bindingMap.get("simpleClassName"));
        // sceneEnum 包含的字段
        CodegenSceneEnum sceneEnum = (CodegenSceneEnum) bindingMap.get("sceneEnum");
        filePath = StringUtils.replace(filePath, "${sceneEnum.prefixClass}", sceneEnum.getPrefixClass());
        filePath = StringUtils.replace(filePath, "${sceneEnum.basePackage}", sceneEnum.getBasePackage());
        // table 包含的字段
        CodegenTableDO table = (CodegenTableDO) bindingMap.get("table");
        filePath = StringUtils.replace(filePath, "${table.moduleName}", table.getModuleName());
        filePath = StringUtils.replace(filePath, "${table.businessName}", table.getBusinessName());
        filePath = StringUtils.replace(filePath, "${table.className}", table.getClassName());
        // 特殊：主子表专属逻辑
        Integer subIndex = (Integer) bindingMap.get("subIndex");
        if (subIndex != null) {
            CodegenTableDO subTable = ((List<CodegenTableDO>) bindingMap.get("subTables")).get(subIndex);
            filePath = StringUtils.replace(filePath, "${subTable.moduleName}", subTable.getModuleName());
            filePath = StringUtils.replace(filePath, "${subTable.businessName}", subTable.getBusinessName());
            filePath = StringUtils.replace(filePath, "${subTable.className}", subTable.getClassName());
            filePath = StringUtils.replace(filePath, "${subSimpleClassName}",
                    ((List<String>) bindingMap.get("subSimpleClassNames")).get(subIndex));
            filePath = StringUtils.replace(filePath, "${subSimpleClassName_strikeCase}",
                    ((List<String>) bindingMap.get("subSimpleClassName_strikeCases")).get(subIndex));
        }
        return filePath;
    }

    /**
     * 构建后端的模板配置，保持 LinkedHashMap 以维持生成顺序
     */
    private static Map<String, String> buildServerTemplates() {
        Map<String, String> templates = new LinkedHashMap<>();
        // Java module-biz(server) Main
        templates.put(javaTemplatePath("controller/vo/pageReqVO"), javaModuleImplVOFilePath("PageReqVO"));
        templates.put(javaTemplatePath("controller/vo/listReqVO"), javaModuleImplVOFilePath("ListReqVO"));
        templates.put(javaTemplatePath("controller/vo/respVO"), javaModuleImplVOFilePath("RespVO"));
        templates.put(javaTemplatePath("controller/vo/saveReqVO"), javaModuleImplVOFilePath("SaveReqVO"));
        templates.put(javaTemplatePath("controller/vo/importExcelVO"), javaModuleImplVOFilePath("ImportExcelVO"));
        templates.put(javaTemplatePath("controller/vo/importRespVO"), javaModuleImplVOFilePath("ImportRespVO"));
        templates.put(javaTemplatePath("controller/controller"), javaModuleImplControllerFilePath());
        templates.put(javaTemplatePath("convert"), // MapStruct 转换器
                javaModuleImplMainFilePath("convert/${table.businessName}/${table.className}Convert"));
        templates.put(javaTemplatePath("dal/do"),
                javaModuleImplMainFilePath("dal/dataobject/${table.businessName}/${table.className}DO"));
        templates.put(javaTemplatePath("dal/do_sub"), // 特殊：主子表专属逻辑
                javaModuleImplMainFilePath("dal/dataobject/${table.businessName}/${subTable.className}DO"));
        templates.put(javaTemplatePath("dal/mapper"),
                javaModuleImplMainFilePath("dal/mysql/${table.businessName}/${table.className}Mapper"));
        templates.put(javaTemplatePath("dal/mapper_sub"), // 特殊：主子表专属逻辑
                javaModuleImplMainFilePath("dal/mysql/${table.businessName}/${subTable.className}Mapper"));
        templates.put(javaTemplatePath("dal/mapper.xml"), mapperXmlFilePath());
        templates.put(javaTemplatePath("service/serviceImpl"),
                javaModuleImplMainFilePath("service/${table.businessName}/${table.className}ServiceImpl"));
        templates.put(javaTemplatePath("service/service"),
                javaModuleImplMainFilePath("service/${table.businessName}/${table.className}Service"));
        // Java module-biz(server) Test
        templates.put(javaTemplatePath("test/serviceTest"),
                javaModuleImplTestFilePath("service/${table.businessName}/${table.className}ServiceImplTest"));
        // Java module-api Main
        templates.put(javaTemplatePath("enums/errorcode"), javaModuleApiMainFilePath("enums/ErrorCodeConstants_手动操作"));
        // SQL
        templates.put("codegen/sql/sql.vm", "sql/sql.sql");
        templates.put("codegen/sql/h2.vm", "sql/h2.sql");
        return templates;
    }

    /**
     * 小驼峰命名转换成下划线命名，例如 DictType => dict_type
     */
    private static String toUnderlineCase(String str) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, str);
    }

    /**
     * 小驼峰命名转换成中划线命名，例如 DictType => dict-type
     */
    private static String toSymbolCase(String str) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, str);
    }

    private static String javaTemplatePath(String path) {
        return "codegen/java/" + path + ".vm";
    }

    private static String javaModuleImplVOFilePath(String path) {
        return javaModuleFilePath("controller/${sceneEnum.basePackage}/${table.businessName}/" +
                "vo/${sceneEnum.prefixClass}${table.className}" + path, "server", "main");
    }

    private static String javaModuleImplControllerFilePath() {
        return javaModuleFilePath("controller/${sceneEnum.basePackage}/${table.businessName}/" +
                "${sceneEnum.prefixClass}${table.className}Controller", "server", "main");
    }

    private static String javaModuleImplMainFilePath(String path) {
        return javaModuleFilePath(path, "server", "main");
    }

    private static String javaModuleApiMainFilePath(String path) {
        return javaModuleFilePath(path, "api", "main");
    }

    private static String javaModuleImplTestFilePath(String path) {
        return javaModuleFilePath(path, "server", "test");
    }

    private static String javaModuleFilePath(String path, String module, String src) {
        return "hft-module-${table.moduleName}/" + // 顶级模块
                "hft-module-${table.moduleName}-" + module + "/" + // 子模块
                "src/" + src + "/java/${basePackage}/module/${table.moduleName}/" + path + ".java";
    }

    private static String mapperXmlFilePath() {
        return "hft-module-${table.moduleName}/" + // 顶级模块
                "hft-module-${table.moduleName}-server/" + // 子模块
                "src/main/resources/mapper/${table.businessName}/${table.className}Mapper.xml";
    }

    private static String vueTemplatePath(String path) {
        return "codegen/vue/" + path + ".vm";
    }

    private static String vueFilePath(String path) {
        return "hft-ui-${sceneEnum.basePackage}-vue2/" + // 顶级目录
                "src/" + path;
    }

    private static String vue3TemplatePath(String path) {
        return "codegen/vue3/" + path + ".vm";
    }

    private static String vue3FilePath(String path) {
        return "hft-ui-${sceneEnum.basePackage}-vue3/" + // 顶级目录
                "src/" + path;
    }

    private static String vue3AdminUniappTemplatePath(String path) {
        return "codegen/vue3_admin_uniapp/" + path + ".vm";
    }

    private static String vue3UniappFilePath(String path) {
        return "hft-ui-${sceneEnum.basePackage}-uniapp/" + // 顶级目录
                "src/" + path;
    }

    private static String vue3VbenFilePath(String path) {
        return "hft-ui-${sceneEnum.basePackage}-vben/" + // 顶级目录
                "src/" + path;
    }

    private static String vue3VbenTemplatePath(String path) {
        return "codegen/vue3_vben/" + path + ".vm";
    }

    private static String vue3Vben5AntdSchemaTemplatePath(String path) {
        return "codegen/vue3_vben5_antd/schema/" + path + ".vm";
    }

    private static String vue3Vben5AntdGeneralTemplatePath(String path) {
        return "codegen/vue3_vben5_antd/general/" + path + ".vm";
    }

    private static String vue3Vben5EpSchemaTemplatePath(String path) {
        return "codegen/vue3_vben5_ele/schema/" + path + ".vm";
    }

    private static String vue3Vben5EpGeneralTemplatePath(String path) {
        return "codegen/vue3_vben5_ele/general/" + path + ".vm";
    }

    private static String vue3Vben5AntdvNextSchemaTemplatePath(String path) {
        return "codegen/vue3_vben5_antdv_next/schema/" + path + ".vm";
    }

    private static String vue3Vben5AntdvNextGeneralTemplatePath(String path) {
        return "codegen/vue3_vben5_antdv_next/general/" + path + ".vm";
    }

    private static boolean isSubTemplate(String path) {
        return path.contains("_sub");
    }

    private static boolean isPageReqVOTemplate(String path) {
        return path.contains("pageReqVO");
    }

    private static boolean isListReqVOTemplate(String path) {
        return path.contains("listReqVO");
    }

    private static boolean isImportTemplate(String path) {
        return path.contains("importExcelVO") || path.contains("importRespVO")
                || path.contains("views/import.vue");
    }

}
