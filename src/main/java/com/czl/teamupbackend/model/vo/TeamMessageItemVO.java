package com.czl.teamupbackend.model.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 消息项
 */
@Data
@Builder
@Schema(description = "消息项")
public class TeamMessageItemVO {

    @Schema(description = "消息ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "内容")
    private String content;

    @Schema(description = "小组名称")
    private String teamName;

    @Schema(description = "消息类型")
    private Integer type;

    @Schema(description = "相关跳转URL")
    private String relatedUrl;

    @Schema(description = "消息时间")
    private LocalDateTime messageTime;

    @Schema(description = "是否已处理: 0-未处理 1-已处理")
    private Integer isProcessed;
}
