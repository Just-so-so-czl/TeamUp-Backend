package com.czl.teamupbackend.model.dto;

import lombok.Data;

@Data
public class CollaborationDocumentAccessVerifyRequest {
    private String documentId;
    private String token;
}
