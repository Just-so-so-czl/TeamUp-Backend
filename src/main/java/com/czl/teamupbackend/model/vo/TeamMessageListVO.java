package com.czl.teamupbackend.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 消息列表响应
 */
@Data
@Builder
@Schema(description = "消息列表响应")
public class TeamMessageListVO {

    @Schema(description = "全部消息")
    private List<TeamMessageItemVO> allMessages;

}
