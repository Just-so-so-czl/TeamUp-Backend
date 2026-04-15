package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.model.entity.TaskList;
import com.czl.teamupbackend.mapper.TaskListMapper;
import com.czl.teamupbackend.service.ITaskListService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 小组项目任务清单表 服务实现类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Service
public class TaskListServiceImpl extends ServiceImpl<TaskListMapper, TaskList> implements ITaskListService {

}
