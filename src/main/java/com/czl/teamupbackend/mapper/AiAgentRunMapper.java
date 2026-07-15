package com.czl.teamupbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.czl.teamupbackend.model.entity.AiAgentRun;
import org.apache.ibatis.annotations.Select;

public interface AiAgentRunMapper extends BaseMapper<AiAgentRun> {

    /** Locks one run while a step number is allocated. */
    @Select("SELECT * FROM ai_agent_run WHERE id = #{runId} FOR UPDATE")
    AiAgentRun selectByIdForUpdate(Long runId);
}
