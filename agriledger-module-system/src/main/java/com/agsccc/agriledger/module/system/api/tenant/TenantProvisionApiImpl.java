package com.agsccc.agriledger.module.system.api.tenant;

import com.agsccc.agriledger.framework.common.enums.CommonStatusEnum;
import com.agsccc.agriledger.module.system.api.tenant.dto.TenantProvisionReqDTO;
import com.agsccc.agriledger.module.system.api.tenant.dto.TenantProvisionRespDTO;
import com.agsccc.agriledger.module.system.controller.admin.tenant.vo.tenant.TenantSaveReqVO;
import com.agsccc.agriledger.module.system.dal.dataobject.tenant.TenantDO;
import com.agsccc.agriledger.module.system.service.account.SystemAccountService;
import com.agsccc.agriledger.module.system.service.tenant.TenantService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 商户租户开通（聚合）API 实现类
 *
 * @author 农E链源码
 */
@Service
@Validated
@Slf4j
public class TenantProvisionApiImpl implements TenantProvisionApi {

    /**
     * 商户租户默认有效期（年）
     */
    private static final int DEFAULT_TENANT_EXPIRE_YEARS = 100;

    @Resource
    private TenantService tenantService;

    @Resource
    private SystemAccountService systemAccountService;

    @Override
    @Transactional(rollbackFor = Exception.class) // 聚合流程保持同一本地事务，任一步失败整体回滚
    public TenantProvisionRespDTO provisionMerchantTenant(TenantProvisionReqDTO reqDTO) {
        // 1. 创建租户（内部：校验名称/套餐 → 建租户 → tenant_admin 角色并绑定套餐菜单 → 管理员用户并分配角色 → 回写联系人）
        TenantSaveReqVO createReqVO = new TenantSaveReqVO();
        createReqVO.setName(reqDTO.getTenantName());
        createReqVO.setPackageId(reqDTO.getPackageId());
        createReqVO.setAccountCount(reqDTO.getAccountCount());
        createReqVO.setContactName(reqDTO.getContactName());
        createReqVO.setContactMobile(reqDTO.getContactMobile());
        createReqVO.setUsername(reqDTO.getUsername());
        createReqVO.setPassword(reqDTO.getPassword());
        createReqVO.setStatus(CommonStatusEnum.ENABLE.getStatus());
        createReqVO.setExpireTime(LocalDateTime.now().plusYears(DEFAULT_TENANT_EXPIRE_YEARS));
        Long tenantId = tenantService.createTenant(createReqVO);

        // 2. 获得租户管理员用户编号（createTenant 已回写 contactUserId）
        TenantDO tenant = tenantService.getTenant(tenantId);
        if (tenant == null || tenant.getContactUserId() == null) {
            throw new IllegalStateException("商户租户开通异常：未生成管理员用户，tenantId=" + tenantId);
        }

        // 3. 绑定全局账号与租户管理用户关联（幂等）
        systemAccountService.bindAccountTenant(reqDTO.getAccountId(), tenantId, tenant.getContactUserId());
        log.info("[provisionMerchantTenant][租户({}) 管理员用户({}) 全局账号({}) 开通完成]",
                tenantId, tenant.getContactUserId(), reqDTO.getAccountId());
        return new TenantProvisionRespDTO().setTenantId(tenantId).setSystemUserId(tenant.getContactUserId());
    }

}
