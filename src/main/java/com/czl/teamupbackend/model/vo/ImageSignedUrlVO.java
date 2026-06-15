package com.czl.teamupbackend.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageSignedUrlVO {

    private String url;

    private Long expiresAt;
}
