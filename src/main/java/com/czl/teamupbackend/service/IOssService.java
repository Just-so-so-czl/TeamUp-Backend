package com.czl.teamupbackend.service;

import org.springframework.web.multipart.MultipartFile;

public interface IOssService {

    String upload(String objectKey, MultipartFile file);

    void delete(String objectKeyOrUrl);

    String generateDownloadUrl(String objectKeyOrUrl, String downloadFileName);
}
