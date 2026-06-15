package com.czl.teamupbackend.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.czl.teamupbackend.commen.properties.OssProperties;
import com.czl.teamupbackend.service.IOssService;
import java.net.URI;
import java.net.URL;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class OssServiceImpl implements IOssService {

    private final OssProperties ossProperties;

    @Override
    public String upload(String objectKey, MultipartFile file) {
        return upload(objectKey, file, file == null ? null : file.getContentType());
    }

    @Override
    public String upload(String objectKey, MultipartFile file, String contentType) {
        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
            );
            PutObjectRequest request = new PutObjectRequest(
                ossProperties.getBucketName(),
                objectKey,
                file.getInputStream()
            );
            if (contentType != null && !contentType.trim().isEmpty()) {
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType(contentType.trim());
                request.setMetadata(metadata);
            }
            ossClient.putObject(request);
            return buildObjectUrl(objectKey);
        } catch (Exception e) {
            log.error("OSS upload failed, objectKey={}", objectKey, e);
            throw new RuntimeException("上传文件到OSS失败");
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    @Override
    public void delete(String objectKeyOrUrl) {
        String objectKey = toObjectKey(objectKeyOrUrl);
        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
            );
            ossClient.deleteObject(ossProperties.getBucketName(), objectKey);
        } catch (Exception e) {
            log.error("OSS delete failed, objectKey={}", objectKey, e);
            throw new RuntimeException("删除OSS文件失败");
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    @Override
    public String generateDownloadUrl(String objectKeyOrUrl, String downloadFileName) {
        String objectKey = toObjectKey(objectKeyOrUrl);
        OSS ossClient = null;
        try {
            ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
            );
            Date expiration = new Date(System.currentTimeMillis() + 5 * 60 * 1000L);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                ossProperties.getBucketName(),
                objectKey,
                HttpMethod.GET
            );
            request.setExpiration(expiration);
            if (downloadFileName != null && !downloadFileName.trim().isEmpty()) {
                ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
                headers.setContentDisposition("attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(downloadFileName.trim(), java.nio.charset.StandardCharsets.UTF_8));
                request.setResponseHeaders(headers);
            }
            URL url = ossClient.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            log.error("OSS generate download url failed, objectKey={}", objectKey, e);
            throw new RuntimeException("生成下载链接失败");
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private String buildObjectUrl(String objectKey) {
        return "https://" + ossProperties.getBucketName() + "." + ossProperties.getEndpoint() + "/" + objectKey;
    }

    private String toObjectKey(String objectKeyOrUrl) {
        if (objectKeyOrUrl == null) {
            return "";
        }
        String value = objectKeyOrUrl.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return value;
        }
        try {
            URI uri = URI.create(value);
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                return "";
            }
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (Exception e) {
            return value;
        }
    }
}
