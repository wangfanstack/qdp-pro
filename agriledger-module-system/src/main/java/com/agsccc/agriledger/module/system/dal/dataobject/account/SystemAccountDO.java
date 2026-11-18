package com.agsccc.agriledger.module.system.dal.dataobject.account;

import com.agsccc.agriledger.framework.common.enums.CommonStatusEnum;
import com.agsccc.agriledger.framework.mybatis.core.dataobject.BaseDO;
import com.agsccc.agriledger.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 全局手机号账号 DO
 *
 * 全局唯一登录身份：一个手机号对应一个全局账号，一个全局账号可关联多个租户的管理用户
 * （关联关系见 {@link SystemAccountUserRelDO}）。本表为全局表，不携带 tenant_id，
 * 生命周期用 status 表达，禁用逻辑删除（DDL 见 sql/mysql/increment/20261106_01）
 *
 * @author 农E链源码
 */
@TableName(value = "system_account", autoResultMap = true)
@KeySequence("system_account_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TenantIgnore
public class SystemAccountDO extends BaseDO {

    /**
     * 全局账号编号
     */
    private Long id;
    /**
     * 手机号（全局唯一登录标识）
     */
    private String mobile;
    /**
     * 账号状态
     * <p>
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

}
