package com.czl.teamupbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** Auditable state of one agent step. Raw tool payloads stay in MongoDB when needed. */
@Data
@Accessors(chain = true)
@TableName("ai_agent_step")
public class AiAgentStep implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long runId;
    private Integer stepNo;
    private String stepType;
    private String toolName;
    private String status;
    private String decisionSummary;
    private String observationSummary;
    private Integer durationMs;
    private Integer promptTokens;
    private Integer completionTokens;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
