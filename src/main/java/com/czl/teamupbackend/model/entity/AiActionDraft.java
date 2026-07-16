package com.czl.teamupbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@TableName("ai_action_draft")
public class AiActionDraft {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long runId;
    private Long teamId;
    private Long creatorUserId;
    private String actionType;
    private String status;
    private String payloadJson;
    private String resultSummary;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime executedAt;
    private LocalDateTime updatedAt;
}
