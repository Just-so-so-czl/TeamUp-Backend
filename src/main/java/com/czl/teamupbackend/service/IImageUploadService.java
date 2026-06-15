package com.czl.teamupbackend.service;

import org.springframework.web.multipart.MultipartFile;

public interface IImageUploadService {

    String uploadImage(Long currentUserId, MultipartFile file);
}
