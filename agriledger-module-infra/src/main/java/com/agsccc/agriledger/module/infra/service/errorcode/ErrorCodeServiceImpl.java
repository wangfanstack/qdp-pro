package com.agsccc.agriledger.module.infra.service.errorcode;

import com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils;
import com.agsccc.agriledger.framework.common.pojo.PageResult;
import com.agsccc.agriledger.framework.common.util.cache.CacheUtils;
import com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo.ErrorCodePageReqVO;
import com.agsccc.agriledger.module.infra.controller.admin.errorcode.vo.ErrorCodeSaveReqVO;
import com.agsccc.agriledger.module.infra.convert.errorcode.ErrorCodeConvert;
import com.agsccc.agriledger.module.infra.dal.dataobject.errorcode.ErrorCodeDO;
import com.agsccc.agriledger.module.infra.dal.mysql.errorcode.ErrorCodeMapper;
import com.agsccc.agriledger.module.infra.service.errorcode.ErrorCodeService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

import static com.agsccc.agriledger.framework.common.exception.util.ServiceExceptionUtils.exception;
import static com.agsccc.agriledger.framework.common.util.collection.CollectionUtils.convertMap;
import static com.agsccc.agriledger.module.infra.enums.ErrorCodeConstants.ERROR_CODE_DUPLICATE;
import static com.agsccc.agriledger.module.infra.enums.ErrorCodeConstants.ERROR_CODE_NOT_EXISTS;

/**
 * 错误码文案 Service 实现类
 *
 * @author Sail
 */
@Service
@Slf4j
@Validated
public class ErrorCodeServiceImpl implements ErrorCodeService {

    /**
     * 本地缓存统一 key：错误码文案量级仅几百条，按全量加载为 Map，无需按 code 分键
     */
    private static final String CACHE_KEY_ALL = "ALL";

    @Resource
    private ErrorCodeMapper errorCodeMapper;

    /**
     * 错误码文案本地缓存：TTL 到期后异步回源，刷新期间读旧值不阻塞请求
     *
     * 文案在每次抛业务异常时读取，本地缓存的读取开销远低于 Redis 网络缓存，
     * 与 DictFrameworkUtils 的字典缓存保持同一模式；集群各节点最多滞后一个 TTL
     */
    private final LoadingCache<String, Map<Integer, String>> messageTemplateCache = CacheUtils.buildAsyncReloadingCache(
            Duration.ofMinutes(1L),
            CacheLoader.from(this::loadMessageTemplates));

    /**
     * 注册为全局错误码文案提供者，业务抛出异常时优先使用数据库中的模板
     */
    @PostConstruct
    public void registerMessageProvider() {
        ServiceExceptionUtils.init(this::getMessageTemplate);
    }

    @Override
    public Long createErrorCode(ErrorCodeSaveReqVO createReqVO) {
        // 校验错误码唯一性
        validateErrorCodeUnique(null, createReqVO.getCode());

        // 插入
        ErrorCodeDO errorCode = ErrorCodeConvert.INSTANCE.convert(createReqVO);
        errorCodeMapper.insert(errorCode);
        evictMessageTemplateCache();
        return errorCode.getId();
    }

    @Override
    public void updateErrorCode(ErrorCodeSaveReqVO updateReqVO) {
        // 校验存在
        validateErrorCodeExists(updateReqVO.getId());
        // 校验错误码唯一性
        validateErrorCodeUnique(updateReqVO.getId(), updateReqVO.getCode());

        // 更新
        ErrorCodeDO updateObj = ErrorCodeConvert.INSTANCE.convert(updateReqVO);
        errorCodeMapper.updateById(updateObj);
        evictMessageTemplateCache();
    }

    @Override
    public void deleteErrorCode(Long id) {
        // 校验存在
        validateErrorCodeExists(id);

        // 删除
        errorCodeMapper.deleteById(id);
        evictMessageTemplateCache();
    }

    @Override
    public ErrorCodeDO getErrorCode(Long id) {
        return errorCodeMapper.selectById(id);
    }

    @Override
    public PageResult<ErrorCodeDO> getErrorCodePage(ErrorCodePageReqVO pageReqVO) {
        return errorCodeMapper.selectPage(pageReqVO);
    }

    @VisibleForTesting
    ErrorCodeDO validateErrorCodeExists(Long id) {
        ErrorCodeDO errorCode = errorCodeMapper.selectById(id);
        if (errorCode == null) {
            throw exception(ERROR_CODE_NOT_EXISTS);
        }
        return errorCode;
    }

    @VisibleForTesting
    void validateErrorCodeUnique(Long id, Integer code) {
        ErrorCodeDO errorCode = errorCodeMapper.selectByCode(code);
        if (errorCode == null) {
            return;
        }
        // 如果 id 为空，说明不用比较是否为相同 id 的错误码文案
        if (id == null || !errorCode.getId().equals(id)) {
            throw exception(ERROR_CODE_DUPLICATE, code);
        }
    }

    @Override
    public String getMessageTemplate(Integer code) {
        return messageTemplateCache.getUnchecked(CACHE_KEY_ALL).get(code);
    }

    /**
     * 全量加载错误码文案；数据库异常时返回空 Map，让文案解析兜底代码默认值，绝不阻断业务异常抛出
     */
    private Map<Integer, String> loadMessageTemplates(String cacheKey) {
        try {
            return convertMap(errorCodeMapper.selectList(), ErrorCodeDO::getCode, ErrorCodeDO::getMessage);
        } catch (Exception e) {
            log.error("[loadMessageTemplates][加载错误码文案失败，本次使用代码默认文案]", e);
            return Collections.emptyMap();
        }
    }

    private void evictMessageTemplateCache() {
        // 本节点立即生效；集群其他节点等待 TTL 到期后自动生效
        messageTemplateCache.invalidateAll();
    }

}
