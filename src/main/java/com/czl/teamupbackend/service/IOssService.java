package com.czl.teamupbackend.service;

import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

public interface IOssService {

    String upload(String objectKey, MultipartFile file);

    String upload(String objectKey, MultipartFile file, String contentType);

    /**
     * 下载 OSS 对象内容。调用方负责关闭返回的流。
     *
     * @param objectKeyOrUrl OSS Key 或对象 URL
     * @return 可读取的对象内容流
     */
    InputStream download(String objectKeyOrUrl);

    void delete(String objectKeyOrUrl);

    String generateDownloadUrl(String objectKeyOrUrl, String downloadFileName);

    String generateDownloadUrl(String objectKeyOrUrl, String downloadFileName, long expireMillis);
}
