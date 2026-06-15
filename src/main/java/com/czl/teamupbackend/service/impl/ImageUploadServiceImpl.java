package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.commen.exception.BizException;
import com.czl.teamupbackend.service.IImageUploadService;
import com.czl.teamupbackend.service.IOssService;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class ImageUploadServiceImpl implements IImageUploadService {

    private static final long IMAGE_SIGNED_URL_EXPIRE_MILLIS = 60 * 60 * 1000L;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "image/jpg",
        "image/gif",
        "image/webp",
        "image/svg+xml"
    );

    private final IOssService ossService;

    @Override
    public String uploadImage(Long currentUserId, MultipartFile file) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "图片文件不能为空");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BizException(400, "仅支持 png/jpg/jpeg/gif/webp/svg 图片");
        }

        String fileExt = resolveImageExtension(file.getOriginalFilename(), contentType);
        String objectKey = buildObjectKey(currentUserId, fileExt);
        ossService.upload(objectKey, file, contentType);
        log.info("Image uploaded, userId={}, objectKey={}", currentUserId, objectKey);
        return objectKey;
    }

    @Override
    public String generateSignedImageUrl(Long currentUserId, String objectKey) {
        if (currentUserId == null || currentUserId <= 0) {
            throw new BizException(401, "未登录");
        }
        if (objectKey == null || objectKey.trim().isEmpty()) {
            throw new BizException(400, "图片ObjectKey不能为空");
        }
        String normalizedObjectKey = objectKey.trim();
        String signedUrl = ossService.generateDownloadUrl(normalizedObjectKey, null, IMAGE_SIGNED_URL_EXPIRE_MILLIS);
        log.info("Image signed url generated, userId={}, objectKey={}", currentUserId, normalizedObjectKey);
        return signedUrl;
    }

    private String buildObjectKey(Long currentUserId, String fileExt) {
        return "editor-images/" + currentUserId + "/" + UUID.randomUUID() + "." + fileExt;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveImageExtension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < originalFilename.length() - 1) {
                String ext = originalFilename.substring(dotIndex + 1).trim().toLowerCase(Locale.ROOT);
                if (!ext.isEmpty()) {
                    return ext.equals("jpeg") ? "jpg" : ext;
                }
            }
        }

        return switch (contentType) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "image/svg+xml" -> "svg";
            default -> "png";
        };
    }
}
