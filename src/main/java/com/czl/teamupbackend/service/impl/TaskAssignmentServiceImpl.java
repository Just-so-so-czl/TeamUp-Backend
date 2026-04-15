package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.model.entity.TaskAssignment;
import com.czl.teamupbackend.mapper.TaskAssignmentMapper;
import com.czl.teamupbackend.service.ITaskAssignmentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 任务负责人分配表 服务实现类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Service
public class TaskAssignmentServiceImpl extends ServiceImpl<TaskAssignmentMapper, TaskAssignment> implements ITaskAssignmentService {

}
