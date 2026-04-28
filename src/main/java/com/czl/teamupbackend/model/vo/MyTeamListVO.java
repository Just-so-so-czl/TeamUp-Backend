package com.czl.teamupbackend.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 我的小组列表响应
 */
@Data
@Builder
@Schema(description = "我的小组列表响应")
public class MyTeamListVO {

    @Schema(description = "小组列表")
    private List<MyTeamVO> teams;
}
