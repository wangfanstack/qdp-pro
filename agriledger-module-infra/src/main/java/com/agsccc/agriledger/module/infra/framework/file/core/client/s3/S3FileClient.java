package com.agsccc.agriledger.module.infra.framework.file.core.client.s3;

import com.agsccc.agriledger.framework.common.util.http.HttpUtils;
import com.agsccc.agriledger.module.infra.framework.file.core.client.AbstractFileClient;
import com.agsccc.agriledger.module.infra.framework.file.core.client.InputStreamProvider;
import com.agsccc.agriledger.module.infra.framework.file.core.client.s3.S3FileClientConfig;
import com.agsccc.agriledger.module.infra.framework.file.core.utils.FileTypeUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.util.StreamUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

/**
 * 基于 S3 协议的文件客户端，实现 MinIO、阿里云、腾讯云、七牛云、华为云等云服务
 *
 * @author Sail
 */
@Slf4j
public class S3FileClient extends AbstractFileClient<S3FileClientConfig> {

    private static final Duration EXPIRATION_DEFAULT = Duration.ofHours(24);

    private S3Client client;
    private S3Presigner presigner;
    // 下载直链专用签名器：绑定对象存储原生端点（endpoint + 虚拟主机寻址）而非自定义域名，
    // 保证直链形如 https://{bucket}.cos.{region}.myqcloud.com/{path}，自定义域名可能未与桶绑定导致直链不可用
    private S3Presigner nativePresigner;

    public S3FileClient(Long id, S3FileClientConfig config) {
        super(id, config);
    }

    @Override
    protected void doInit() {
        // 补全 domain
        if (StringUtils.isEmpty(config.getDomain())) {
            config.setDomain(buildDomain());
        }
        // 初始化 S3 客户端
        // 优先级：配置的 region > 从 endpoint 解析的 region > 默认值 us-east-1
        String regionStr = resolveRegion();
        Region region = Region.of(regionStr);
        AwsCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.getAccessKey(), config.getAccessSecret()));
        URI endpoint = URI.create(buildEndpoint());
        boolean pathStyleAccessEnabled = resolvePathStyleAccessEnabled();
        URI presignerEndpoint = URI.create(buildPresignerEndpoint(pathStyleAccessEnabled));
        S3Configuration serviceConfiguration = S3Configuration.builder() // Path-style 访问
                .pathStyleAccessEnabled(pathStyleAccessEnabled)
                .chunkedEncodingEnabled(false) // 禁用分块编码，参见 https://t.zsxq.com/kBy57
                .build();
        client = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .endpointOverride(endpoint)
                .serviceConfiguration(serviceConfiguration)
                .build();
        presigner = S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .endpointOverride(presignerEndpoint)
                .serviceConfiguration(serviceConfiguration)
                .build();
        nativePresigner = S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .endpointOverride(endpoint)
                .serviceConfiguration(serviceConfiguration)
                .build();
    }

    @Override
    public String upload(byte[] content, String path, String type) {
        // 构造 PutObjectRequest
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(config.getBucket())
                .key(path)
                .contentType(type)
                .contentLength((long) content.length)
                .build();
        // 上传文件
        client.putObject(putRequest, RequestBody.fromBytes(content));
        // 拼接返回路径
        return presignGetUrl(path, null);
    }

    @Override
    public String upload(InputStreamProvider content, long size, String path, String type) {
        // 构造 PutObjectRequest
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(config.getBucket())
                .key(path)
                .contentType(type)
                .contentLength(size)
                .build();
        // S3 SDK 支持流式上传，大文件无需读入内存
        // 签名（HTTP 端点默认 payload signing 需读流计算 SHA-256）或失败重试都会重新读取内容，
        // 单一输入流会触发 ResetException，必须通过 ContentStreamProvider 支持重新打开
        ContentStreamProvider contentStreamProvider = ContentStreamProvider.fromInputStreamSupplier(() -> {
            try {
                return content.openStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        client.putObject(putRequest, RequestBody.fromContentProvider(contentStreamProvider, size, type));
        // 拼接返回路径
        return presignGetUrl(path, null);
    }

    @Override
    public void delete(String path) {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(config.getBucket())
                .key(path)
                .build();
        client.deleteObject(deleteRequest);
    }

    @Override
    public byte[] getContent(String path) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(config.getBucket())
                .key(path)
                .build();
        try {
            return StreamUtils.copyToByteArray(client.getObject(getRequest));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public InputStream getContentStream(String path) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(config.getBucket())
                .key(path)
                .build();
        // SDK 原生返回流式响应，大文件下载无需读入内存
        return client.getObject(getRequest);
    }

    @Override
    public String presignPutUrl(String path) {
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(EXPIRATION_DEFAULT)
                .putObjectRequest(b -> b.bucket(config.getBucket()).key(path)).build())
                .url().toString();
    }

    @Override
    public String presignGetUrl(String url, Integer expirationSeconds) {
        // 1. 将 url 转换为 path
        // 完整 Domain URL 会解码还原为原始对象名；裸 path 约定已经是未编码的对象名
        boolean domainUrl = Strings.CS.startsWith(url, config.getDomain() + "/");
        String path = domainUrl ? Strings.CS.removeStart(url, config.getDomain() + "/") : url;
        if (domainUrl) {
            path = HttpUtils.removeUrlPathQueryAndFragment(path);
            path = HttpUtils.decodeUrlPath(path);
        }

        // 2.1 情况一：公开访问：无需签名
        // 考虑到老版本的兼容，所以必须是 config.getEnablePublicAccess() 为 false 时，才进行签名
        if (!Boolean.FALSE.equals(config.getEnablePublicAccess())) {
            return config.getDomain() + "/" + HttpUtils.encodeUrlPath(path);
        }

        // 2.2 情况二：私有访问：生成 GET 预签名 URL
        String finalPath = path;
        Duration expiration = expirationSeconds != null ? Duration.ofSeconds(expirationSeconds) : EXPIRATION_DEFAULT;
        URL signedUrl = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(b -> b.bucket(config.getBucket()).key(finalPath)).build())
                .url();
        return signedUrl.toString();
    }

    @Override
    public String presignDownloadUrl(String path, String filename, Integer expirationSeconds) {
        // 通过预签名响应头指定附件文件名，浏览器直连下载时按 filename 展示与保存
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(config.getBucket())
                .key(path)
                .responseContentDisposition(FileTypeUtils.buildContentDisposition("attachment", filename))
                .build();
        Duration expiration = expirationSeconds != null ? Duration.ofSeconds(expirationSeconds) : EXPIRATION_DEFAULT;
        // 使用原生端点签名器，直链挂对象存储原生域名（如 https://{bucket}.cos.{region}.myqcloud.com/{path}）
        URL signedUrl = nativePresigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getRequest).build())
                .url();
        return signedUrl.toString();
    }

    /**
     * 基于 bucket + endpoint 构建访问的 Domain 地址
     *
     * @return Domain 地址
     */
    private String buildDomain() {
        // 如果已经是 http 或者 https，则不进行拼接.主要适配 MinIO
        if (config.getEndpoint().startsWith("http://") || config.getEndpoint().startsWith("https://")) {
            return String.format("%s/%s", config.getEndpoint(), config.getBucket());
        }
        // 阿里云、腾讯云、华为云都适合。七牛云比较特殊，必须有自定义域名
        return String.format("https://%s.%s", config.getBucket(), config.getEndpoint());
    }

    /**
     * 节点地址补全协议头
     *
     * @return 节点地址
     */
    private String buildEndpoint() {
        // 如果已经是 http 或者 https，则不进行拼接
        if (config.getEndpoint().startsWith("http://") || config.getEndpoint().startsWith("https://")) {
            return config.getEndpoint();
        }
        return String.format("https://%s", config.getEndpoint());
    }

    /**
     * presigner 节点地址
     *
     * @param pathStyleAccessEnabled 是否启用 Path-style 访问（使用 {@link #resolvePathStyleAccessEnabled()} 矫正后的值，保证与 S3Client 一致）
     * @return 节点地址
     */
    private String buildPresignerEndpoint(boolean pathStyleAccessEnabled) {
        // 补全 domain
        if (StringUtils.isEmpty(config.getDomain())) {
            config.setDomain(buildDomain());
        }

        if (pathStyleAccessEnabled) {
            return Strings.CS.removeEnd(config.getDomain(), String.format("/%s", config.getBucket()));
        }
        return Strings.CS.replace(config.getDomain(), String.format("://%s.", config.getBucket()), "://");
    }

    /**
     * 解析 Path-style 访问配置
     * 腾讯云 COS 仅支持虚拟主机寻址，Path-style 会被服务端拒绝并返回 403：
     * The bucket you are attempting to access must be addressed using COS virtual-styled domain
     *
     * @return 是否启用 Path-style 访问
     */
    private boolean resolvePathStyleAccessEnabled() {
        boolean enabled = Boolean.TRUE.equals(config.getEnablePathStyleAccess());
        if (enabled && Strings.CS.contains(config.getEndpoint(), S3FileClientConfig.ENDPOINT_TENCENT)) {
            log.warn("[resolvePathStyleAccessEnabled][endpoint({}) 为腾讯云 COS，不支持 Path-style 访问，强制使用虚拟主机寻址]",
                    config.getEndpoint());
            return false;
        }
        return enabled;
    }

    /**
     * 解析 AWS 区域
     * 优先级：配置的 region > 从 endpoint 解析的 region > 默认值 us-east-1
     *
     * @return 区域字符串
     */
    private String resolveRegion() {
        // 1. 如果配置了 region，直接使用
        if (StringUtils.isNotEmpty(config.getRegion())) {
            return config.getRegion();
        }

        // 2.1 尝试从 endpoint 中解析 region
        String endpoint = config.getEndpoint();
        if (StringUtils.isEmpty(endpoint)) {
            return "us-east-1";
        }

        // 2.2 移除协议头（http:// 或 https://）
        String host = endpoint;
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            try {
                host = URI.create(endpoint).getHost();
            } catch (Exception e) {
                // 解析失败，使用默认值
                return "us-east-1";
            }
        }
        if (StringUtils.isEmpty(host)) {
            return "us-east-1";
        }

        // 3.1 AWS S3 格式：s3.us-west-2.amazonaws.com 或 s3.amazonaws.com
        if (host.contains("amazonaws.com")) {
            // 匹配 s3.{region}.amazonaws.com 格式
            if (host.startsWith("s3.") && host.contains(".amazonaws.com")) {
                String regionPart = host.substring(3, host.indexOf(".amazonaws.com"));
                if (StringUtils.isNotEmpty(regionPart) && !"accelerate".equals(regionPart)) {
                    return regionPart;
                }
            }
            // s3.amazonaws.com 或 s3-accelerate.amazonaws.com 使用默认值
            return "us-east-1";
        }
        // 3.2 阿里云 OSS 格式：oss-cn-beijing.aliyuncs.com
        if (host.contains(S3FileClientConfig.ENDPOINT_ALIYUN)) {
            // 匹配 oss-{region}.aliyuncs.com 格式
            if (host.startsWith("oss-") && host.contains("." + S3FileClientConfig.ENDPOINT_ALIYUN)) {
                String regionPart = host.substring(4, host.indexOf("." + S3FileClientConfig.ENDPOINT_ALIYUN));
                if (StringUtils.isNotEmpty(regionPart)) {
                    return regionPart;
                }
            }
        }
        // 3.3 腾讯云 COS 格式：cos.ap-shanghai.myqcloud.com
        if (host.contains(S3FileClientConfig.ENDPOINT_TENCENT)) {
            // 匹配 cos.{region}.myqcloud.com 格式
            if (host.startsWith("cos.") && host.contains("." + S3FileClientConfig.ENDPOINT_TENCENT)) {
                String regionPart = host.substring(4, host.indexOf("." + S3FileClientConfig.ENDPOINT_TENCENT));
                if (StringUtils.isNotEmpty(regionPart)) {
                    return regionPart;
                }
            }
        }

        // 3.4 其他情况（MinIO、七牛云等）使用默认值
        return "us-east-1";
    }

}
