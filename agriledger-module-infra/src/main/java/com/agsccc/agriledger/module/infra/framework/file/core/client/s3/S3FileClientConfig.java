package com.agsccc.agriledger.module.infra.framework.file.core.client.s3;

import com.agsccc.agriledger.module.infra.framework.file.core.client.FileClientConfig;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.URL;

/**
 * S3 文件客户端的配置类
 *
 * @author Sail
 */
@Data
public class S3FileClientConfig implements FileClientConfig {

    public static final String ENDPOINT_QINIU = "qiniucs.com";
    public static final String ENDPOINT_ALIYUN = "aliyuncs.com";
    public static final String ENDPOINT_TENCENT = "myqcloud.com";
    public static final String ENDPOINT_VOLCES = "volces.com"; // 火山云（字节）

    /**
     * 节点地址
     * 1. MinIO：<a href="https://www.hcdigi.cn/Spring-Boot/MinIO">...</a> 。例如说，http://127.0.0.1:9000
     * 2. 阿里云：<a href="https://help.aliyun.com/document_detail/31837.html">...</a>
     * 3. 腾讯云：<a href="https://cloud.tencent.com/document/product/436/6224">...</a>
     * 4. 七牛云：<a href="https://developer.qiniu.com/kodo/4088/s3-access-domainname">...</a>
     * 5. 华为云：<a href="https://console.huaweicloud.com/apiexplorer/#/endpoint/OBS">...</a>
     * 6. 火山云：<a href="https://www.volcengine.com/docs/6349/107356">...</a>
     */
    @NotNull(message = "endpoint 不能为空")
    private String endpoint;
    /**
     * 自定义域名
     * 1. MinIO：通过 Nginx 配置
     * 2. 阿里云：<a href="https://help.aliyun.com/document_detail/31836.html">...</a>
     * 3. 腾讯云：<a href="https://cloud.tencent.com/document/product/436/11142">...</a>
     * 4. 七牛云：<a href="https://developer.qiniu.com/kodo/8556/set-the-custom-source-domain-name">...</a>
     * 5. 华为云：<a href="https://support.huaweicloud.com/usermanual-obs/obs_03_0032.html">...</a>
     * 6. 火山云：<a href="https://www.volcengine.com/docs/6349/128983">...</a>
     */
    @URL(message = "domain 必须是 URL 格式")
    private String domain;
    /**
     * 存储 Bucket
     */
    @NotNull(message = "bucket 不能为空")
    private String bucket;

    /**
     * 访问 Key
     * 1. MinIO：<a href="https://www.hcdigi.cn/Spring-Boot/MinIO">...</a>
     * 2. 阿里云：<a href="https://ram.console.aliyun.com/manage/ak">...</a>
     * 3. 腾讯云：<a href="https://console.cloud.tencent.com/cam/capi">...</a>
     * 4. 七牛云：<a href="https://portal.qiniu.com/user/key">...</a>
     * 5. 华为云：<a href="https://support.huaweicloud.com/qs-obs/obs_qs_0005.html">...</a>
     * 6. 火山云：<a href="https://console.volcengine.com/iam/keymanage/">...</a>
     */
    @NotNull(message = "accessKey 不能为空")
    private String accessKey;
    /**
     * 访问 Secret
     */
    @NotNull(message = "accessSecret 不能为空")
    private String accessSecret;

    /**
     * 是否启用 PathStyle 访问
     */
    @NotNull(message = "enablePathStyleAccess 不能为空")
    private Boolean enablePathStyleAccess;

    /**
     * 是否公开访问
     *
     * true：公开访问，所有人都可以访问
     * false：私有访问，只有配置的 accessKey 才可以访问
     */
    @NotNull(message = "是否公开访问不能为空")
    private Boolean enablePublicAccess;

    /**
     * 区域
     * 1. AWS S3：<a href="https://docs.aws.amazon.com/general/latest/gr/s3.html">...</a> 例如说，us-east-1、us-west-2
     * 2. MinIO：可以填任意值，通常使用 us-east-1
     * 3. 阿里云：不需要填写，会自动识别
     * 4. 腾讯云：不需要填写，会自动识别
     * 5. 七牛云：不需要填写，会自动识别
     * 6. 华为云：不需要填写，会自动识别
     * 7. 火山云：不需要填写，会自动识别
     */
    private String region;

    @SuppressWarnings("RedundantIfStatement")
    @AssertTrue(message = "domain 不能为空")
    @JsonIgnore
    public boolean isDomainValid() {
        // 如果是七牛，必须带有 domain
        if (StringUtils.contains(endpoint, ENDPOINT_QINIU) && StringUtils.isEmpty(domain)) {
            return false;
        }
        return true;
    }

}
