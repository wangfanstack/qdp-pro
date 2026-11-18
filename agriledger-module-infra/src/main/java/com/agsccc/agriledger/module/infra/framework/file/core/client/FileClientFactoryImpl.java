package com.agsccc.agriledger.module.infra.framework.file.core.client;

import com.agsccc.agriledger.module.infra.framework.file.core.enums.FileStorageEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文件客户端的工厂实现类
 *
 * @author Sail
 */
@Slf4j
public class FileClientFactoryImpl implements FileClientFactory {

    /**
     * 文件客户端 Map
     * key：配置编号
     */
    private final ConcurrentMap<Long, AbstractFileClient<?>> clients = new ConcurrentHashMap<>();

    @Override
    public FileClient getFileClient(Long configId) {
        AbstractFileClient<?> client = clients.get(configId);
        if (client == null) {
            log.error("[getFileClient][配置编号({}) 找不到客户端]", configId);
        }
        return client;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Config extends FileClientConfig> void createOrUpdateFileClient(Long configId, Integer storage, Config config) {
        AbstractFileClient<Config> client = (AbstractFileClient<Config>) clients.get(configId);
        if (client == null) {
            client = this.createFileClient(configId, storage, config);
            client.init();
            clients.put(client.getId(), client);
        } else {
            client.refresh(config);
        }
    }

    @SuppressWarnings("unchecked")
    private <Config extends FileClientConfig> AbstractFileClient<Config> createFileClient(
            Long configId, Integer storage, Config config) {
        FileStorageEnum storageEnum = FileStorageEnum.getByStorage(storage);
        Assert.notNull(storageEnum, String.format("文件配置(%s) 不存在", storage));
        // 创建客户端
        return (AbstractFileClient<Config>) newFileClient(storageEnum, configId, config);
    }

    /**
     * 基于客户端类的 (configId, config) 构造方法，反射创建文件客户端
     */
    private <Config extends FileClientConfig> FileClient newFileClient(
            FileStorageEnum storageEnum, Long configId, Config config) {
        try {
            Class<? extends FileClientConfig> configClass = storageEnum.getConfigClass();
            Constructor<? extends FileClient> constructor = storageEnum.getClientClass()
                    .getConstructor(Long.class, configClass);
            return constructor.newInstance(configId, config);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(String.format("文件配置(%s) 创建客户端失败", storageEnum), e);
        }
    }

}
