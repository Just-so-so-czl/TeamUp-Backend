package com.czl.teamupbackend.model.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TeamUpdateRequest {

    private Long teamId;

    private String name;

    private String description;

    private LocalDateTime totalDeadline;
}

