package com.czl.teamupbackend.controller;

import com.czl.teamupbackend.commen.context.UserContext;
import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.commen.result.Result;
import com.czl.teamupbackend.model.dto.ImageSignedUrlRequest;
import com.czl.teamupbackend.model.vo.ImageSignedUrlVO;
import com.czl.teamupbackend.model.vo.ImageUploadVO;
import com.czl.teamupbackend.service.IImageUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/upload")
@Slf4j
@RequiredArgsConstructor
public class UploadController {

    private final IImageUploadService imageUploadService;

    @PostMapping("/image")
    public Result<ImageUploadVO> uploadImage(@RequestParam("file") MultipartFile file) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }

        String objectKey = imageUploadService.uploadImage(userId, file);
        return Result.success("上传成功", ImageUploadVO.builder().objectKey(objectKey).build());
    }

    @PostMapping("/image/signed-url")
    public Result<ImageSignedUrlVO> generateImageSignedUrl(@RequestBody ImageSignedUrlRequest request) {
        Long userId = UserContext.getCurrentUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }

        String signedUrl = imageUploadService.generateSignedImageUrl(userId, request == null ? null : request.getObjectKey());
        return Result.success(
            "查询成功",
            ImageSignedUrlVO.builder()
                .url(signedUrl)
                .expiresAt(System.currentTimeMillis() + 60 * 60 * 1000L)
                .build()
        );
    }
}
