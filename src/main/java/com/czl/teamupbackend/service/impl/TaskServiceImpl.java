package com.czl.teamupbackend.service.impl;

import com.czl.teamupbackend.model.entity.Task;
import com.czl.teamupbackend.mapper.TaskMapper;
import com.czl.teamupbackend.service.ITaskService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 具体任务项表 服务实现类
 * </p>
 *
 * @author czl
 * @since 2026-04-15
 */
@Service
public class TaskServiceImpl extends ServiceImpl<TaskMapper, Task> implements ITaskService {

}
