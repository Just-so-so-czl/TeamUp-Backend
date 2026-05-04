package com.czl.teamupbackend.mapper;

import com.czl.teamupbackend.model.entity.TaskAssignment;
import com.czl.teamupbackend.model.vo.TaskDeadlineReminderCandidateVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 任务负责人分配表 Mapper 接口
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
public interface TaskAssignmentMapper extends BaseMapper<TaskAssignment> {

    List<TaskDeadlineReminderCandidateVO> selectDeadlineReminderCandidates(
        @Param("startDeadline") LocalDateTime startDeadline,
        @Param("endDeadline") LocalDateTime endDeadline
    );
}
