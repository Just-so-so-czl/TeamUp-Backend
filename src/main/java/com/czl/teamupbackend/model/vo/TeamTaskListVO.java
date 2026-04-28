package com.czl.teamupbackend.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小组任务清单列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "小组任务清单列表")
public class TeamTaskListVO {

    @Schema(description = "当前用户是否可创建清单（Captain/Leader）")
    private Boolean currentUserCanCreate;

    @Schema(description = "任务清单列表")
    private List<TeamTaskListItemVO> taskLists;
}

