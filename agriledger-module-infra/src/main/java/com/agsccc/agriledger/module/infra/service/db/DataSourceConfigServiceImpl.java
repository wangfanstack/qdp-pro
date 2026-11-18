package com.agsccc.agriledger.module.infra.service.db;

import com.agsccc.agriledger.framework.mybatis.core.util.JdbcUtils;
import com.agsccc.agriledger.module.infra.controller.admin.db.vo.DataSourceConfigSaveReqVO;
import com.agsccc.agriledger.module.infra.convert.db.DataSourceConfigConvert;
import com.agsccc.agriledger.module.infra.dal.dataobject.db.DataSourceConfigDO;
import com.agsccc.agriledger.module.infra.dal.mysql.db.DataSourceConfigMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Objects;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.module.infra.enums.ErrorCodeConstants.DATA_SOURCE_CONFIG_NOT_EXISTS;
import static com.agsccc.agriledger.module.infra.enums.ErrorCodeConstants.DATA_SOURCE_CONFIG_NOT_OK;

/**
 * 数据源配置 Service 实现类
 *
 * @author Sail
 */
@Service
@Validated
public class DataSourceConfigServiceImpl implements DataSourceConfigService {

    @Resource
    private DataSourceConfigMapper dataSourceConfigMapper;

    /**
     * 主数据源配置（HikariCP 单体数据源，读取 spring.datasource 标准属性）
     */
    @Value("${spring.datasource.url}")
    private String masterUrl;
    @Value("${spring.datasource.username}")
    private String masterUsername;
    @Value("${spring.datasource.password}")
    private String masterPassword;

    @Override
    public Long createDataSourceConfig(DataSourceConfigSaveReqVO createReqVO) {
        DataSourceConfigDO config = DataSourceConfigConvert.INSTANCE.convert(createReqVO);
        validateConnectionOK(config);

        // 插入
        dataSourceConfigMapper.insert(config);
        // 返回
        return config.getId();
    }

    @Override
    public void updateDataSourceConfig(DataSourceConfigSaveReqVO updateReqVO) {
        // 校验存在
        validateDataSourceConfigExists(updateReqVO.getId());
        DataSourceConfigDO updateObj = DataSourceConfigConvert.INSTANCE.convert(updateReqVO);
        validateConnectionOK(updateObj);

        // 更新
        dataSourceConfigMapper.updateById(updateObj);
    }

    @Override
    public void deleteDataSourceConfig(Long id) {
        // 校验存在
        validateDataSourceConfigExists(id);
        // 删除
        dataSourceConfigMapper.deleteById(id);
    }

    @Override
    public void deleteDataSourceConfigList(List<Long> ids) {
        dataSourceConfigMapper.deleteByIds(ids);
    }

    private void validateDataSourceConfigExists(Long id) {
        if (dataSourceConfigMapper.selectById(id) == null) {
            throw exception(DATA_SOURCE_CONFIG_NOT_EXISTS);
        }
    }

    @Override
    public DataSourceConfigDO getDataSourceConfig(Long id) {
        // 如果 id 为 0，默认为 master 的数据源
        if (Objects.equals(id, DataSourceConfigDO.ID_MASTER)) {
            return buildMasterDataSourceConfig();
        }
        // 从 DB 中读取
        return dataSourceConfigMapper.selectById(id);
    }

    @Override
    public List<DataSourceConfigDO> getDataSourceConfigList() {
        List<DataSourceConfigDO> result = dataSourceConfigMapper.selectList();
        // 补充 master 数据源
        result.add(0, buildMasterDataSourceConfig());
        return result;
    }

    private void validateConnectionOK(DataSourceConfigDO config) {
        boolean success = JdbcUtils.isConnectionOK(config.getUrl(), config.getUsername(), config.getPassword());
        if (!success) {
            throw exception(DATA_SOURCE_CONFIG_NOT_OK);
        }
    }

    private DataSourceConfigDO buildMasterDataSourceConfig() {
        return new DataSourceConfigDO().setId(DataSourceConfigDO.ID_MASTER).setName("master")
                .setUrl(masterUrl)
                .setUsername(masterUsername)
                .setPassword(masterPassword);
    }

}
