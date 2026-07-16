package com.czl.teamupbackend.model.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class TeamCreateRequest {

    private String name;

    private String description;

    private LocalDateTime totalDeadline;
}
