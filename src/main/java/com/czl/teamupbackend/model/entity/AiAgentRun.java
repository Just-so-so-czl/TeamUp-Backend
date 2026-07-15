package com.czl.teamupbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** One adaptive Plan + ReAct execution, linked to a mentor chat turn. */
@Data
@Accessors(chain = true)
@TableName("ai_agent_run")
public class AiAgentRun implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long sessionId;
    private Long teamId;
    private Long userId;
    private String traceId;
    private String sceneType;
    private String goal;
    private String planJson;
    private Integer planVersion;
    private String status;
    private Integer stepCount;
    private Integer promptTokens;
    private Integer completionTokens;
    private String errorMsg;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
