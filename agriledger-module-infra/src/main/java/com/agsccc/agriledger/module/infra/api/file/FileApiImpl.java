package com.agsccc.agriledger.module.infra.api.file;

import com.agsccc.agriledger.module.infra.api.file.FileApi;
import com.agsccc.agriledger.module.infra.api.file.dto.FileCreateRespDTO;
import com.agsccc.agriledger.module.infra.api.file.dto.FileRespDTO;
import com.agsccc.agriledger.module.infra.dal.dataobject.file.FileDO;
import com.agsccc.agriledger.module.infra.service.file.FileService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Map;

/**
 * 文件 API 实现类
 *
 * @author Sail
 */
@Service
@Validated
public class FileApiImpl implements FileApi {

    @Resource
    private FileService fileService;

    @Override
    public String createFile(byte[] content, String name, String category, String type) {
        return fileService.createFile(content, name, category, type).get("url").toString();
    }

    @Override
    public FileCreateRespDTO createFileWithInfo(byte[] content, String name, String category, String type) {
        Map<String, Object> createResult = fileService.createFile(content, name, category, type);
        return new FileCreateRespDTO((Long) createResult.get("fileId"), createResult.get("url").toString());
    }

    @Override
    public Long createFileRecord(String name, String url) {
        return fileService.createFileRecord(name, url);
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        return fileService.presignGetUrl(url, expirationSeconds);
    }

    @Override
    public FileRespDTO getFile(Long id) {
        FileDO file = fileService.getFile(id);
        return file == null ? null : new FileRespDTO(file.getId(), file.getName(), file.getUrl());
    }

}
