package com.czl.teamupbackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.czl.teamupbackend.mapper.AiChatMemoryStateMapper;
import com.czl.teamupbackend.model.entity.AiChatMemoryState;
import com.czl.teamupbackend.service.IAiChatMemoryStateService;
import org.springframework.stereotype.Service;

@Service
public class AiChatMemoryStateServiceImpl extends ServiceImpl<AiChatMemoryStateMapper, AiChatMemoryState>
    implements IAiChatMemoryStateService {
}
