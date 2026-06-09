package com.czl.teamupbackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * AI 对话记忆生命周期状态表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("ai_chat_memory_state")
public class AiChatMemoryState implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private Long teamId;

    private Integer shortTermTokenCount;

    private Integer shortTermMessageCount;

    private String midTermMongoId;

    private Integer midTermTokenCount;

    private LocalDateTime lastMidTermCompressAt;

    private Integer lastMidTermSourceTokenCount;

    private Integer lastMidTermTargetTokenCount;

    private Integer lastMidTermSummaryTokenCount;

    private Integer lastMidTermRemovedMessageCount;

    private String lastMidTermRemovedMessageIds;

    private String lastMidTermStatus;

    private String lastMidTermErrorMsg;

    private String earlyTermMongoId;

    private Integer earlyTermTokenCount;

    private LocalDateTime lastEarlyTermCompressAt;

    private Integer lastEarlyTermSourceTokenCount;

    private Integer lastEarlyTermTargetTokenCount;

    private Integer lastEarlyTermSummaryTokenCount;

    private Integer lastEarlyTermRemovedSegmentCount;

    private Integer lastEarlyTermRemovedTokenCount;

    private String lastEarlyTermStatus;

    private String lastEarlyTermErrorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
