package com.agsccc.agriledger.module.system.dal.dataobject.account;

import com.agsccc.agriledger.framework.common.enums.CommonStatusEnum;
import com.agsccc.agriledger.framework.mybatis.core.dataobject.BaseDO;
import com.agsccc.agriledger.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 全局账号与租户管理用户关联 DO
 *
 * 一个全局账号可关联多个租户；同一账号在同一租户仅一条关联（uk_account_tenant）；
 * 一个 system_users 用户仅被一个全局账号绑定（uk_system_user）。本表为全局表，
 * tenant_id 为业务归属列而非隔离列；关联失效用 status 表达（不删行），恢复复用同一行
 *
 * @author 农E链源码
 */
@TableName(value = "system_account_user_rel", autoResultMap = true)
@KeySequence("system_account_user_rel_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TenantIgnore
public class SystemAccountUserRelDO extends BaseDO {

    /**
     * 关联编号
     */
    private Long id;
    /**
     * 全局账号编号（system_account.id）
     */
    private Long accountId;
    /**
     * 租户编号（system_tenant.id）
     */
    private Long tenantId;
    /**
     * 该租户下的管理用户编号（system_users.id，必须属于 tenant_id，由应用层校验）
     */
    private Long systemUserId;
    /**
     * 关联状态
     * <p>
     * 枚举 {@link CommonStatusEnum}
     */
    private Integer status;

}
