package com.czl.teamupbackend.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * AI导师对话消息索引表
 * </p>
 *
 * @author czl
 * @since 2026-05-27
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("ai_chat_message_index")
public class AiChatMessageIndex implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息索引ID(雪花ID)
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 小组ID
     */
    private Long teamId;

    /**
     * 发送者用户ID(系统消息可为空)
     */
    private Long userId;

    /**
     * 发送者:USER/ASSISTANT/SYSTEM
     */
    private String senderType;

    /**
     * 消息类型:TEXT/TASK_SUGGESTION/SUMMARY/MEMORY_REF
     */
    private String messageType;

    /**
     * Mongo文档ID
     */
    private String mongoMessageId;

    /**
     * 链路追踪ID
     */
    private String traceId;

    /**
     * 输入token数
     */
    private Integer tokenInput;

    /**
     * 输出token数
     */
    private Integer tokenOutput;

    /**
     * 状态:1=PENDING,2=DONE,3=FAILED
     */
    private Integer status;

    /**
     * 失败原因
     */
    private String errorMsg;

    /**
     * 逻辑删除:0=否,1=是
     */
    private Integer deleted;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;


}
