package com.agsccc.agriledger.module.infra.dal.dataobject.errorcode;

import com.agsccc.agriledger.framework.mybatis.core.dataobject.BaseDO;
import com.agsccc.agriledger.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 错误码文案表 DO
 *
 * 运行时按 code 覆盖代码中 ErrorCodeConstants 的默认错误提示，实现"改文案不发版"；
 * 表中不存在的错误码自动兜底使用代码默认文案
 *
 * @author Sail
 */
@TableName("infra_error_code")
@KeySequence("infra_error_code_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TenantIgnore // 错误码文案全局共享，不按租户隔离
public class ErrorCodeDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 错误码，与代码中 ErrorCodeConstants 定义的编码一致
     */
    private Integer code;
    /**
     * 模块名，标识错误码的业务归属
     */
    private String module;
    /**
     * 错误提示模板，支持 {} 占位符，占位符个数需与代码抛出时传参一致
     */
    private String message;
    /**
     * 备注
     */
    private String memo;

}
