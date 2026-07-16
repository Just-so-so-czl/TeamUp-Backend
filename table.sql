CREATE TABLE `user` (
    -- 雪花 ID (64位整数)
                         `id` BIGINT NOT NULL COMMENT '雪花算法生成的分布式唯一ID',

    -- 邮箱字段：设置为唯一索引，不允许重复
                         `email` VARCHAR(100) NOT NULL COMMENT '用户邮箱',

    -- 用户名：不再设置唯一索引，允许重复
                         `username` VARCHAR(50) NOT NULL COMMENT '用户名',

                         `password` VARCHAR(255) NOT NULL COMMENT '加密后的密码',

    -- 性别枚举：1-男, 2-女
                          `gender` TINYINT NOT NULL DEFAULT 1 COMMENT '性别枚举: 1-男, 2-女',

    -- 头像枚举: 1-8
                         `avatar` TINYINT NOT NULL DEFAULT 1 COMMENT '头像枚举: 1-8 代表不同的预设头像',

                         `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                         PRIMARY KEY (`id`),
    -- 核心变更：将唯一约束从 username 转移到 email
                         UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户基础信息表';

CREATE TABLE `team` (
    -- 雪花 ID (64位整数)
                         `id` BIGINT NOT NULL COMMENT '雪花算法生成的分布式唯一ID',

    -- 小组名称
                         `name` VARCHAR(100) NOT NULL COMMENT '小组名称',

    -- 创建者 ID，关联 users 表的 id
                         `owner_id` BIGINT NOT NULL COMMENT '创建者ID(组长)',

    -- 邀请码：建议设置为唯一，方便用户通过邀请码直接加组
                         `invite_code` VARCHAR(20) NOT NULL COMMENT '小组邀请码',

    -- 小组描述：允许为空 (NULL)
                         `description` TEXT DEFAULT NULL COMMENT '小组简介',

                         `total_deadline` DATETIME NOT NULL COMMENT '小组作业最终截止时间（总DDL）',

                         `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                         PRIMARY KEY (`id`),
    -- 邀请码唯一索引，确保搜索时的精准性
                         UNIQUE KEY `uk_invite_code` (`invite_code`),
    -- 为 owner_id 建立索引，方便查询某个用户创建的所有小组
                         KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='小组/团队信息表';

-- 已有数据库迁移：请先为历史小组人工回填合理的总DDL，再执行 NOT NULL 收紧。
-- ALTER TABLE `team` ADD COLUMN `total_deadline` DATETIME NULL COMMENT '小组作业最终截止时间（总DDL）' AFTER `description`;
-- UPDATE `team` SET `total_deadline` = '2026-12-31 23:59:59' WHERE `total_deadline` IS NULL;
-- ALTER TABLE `team` MODIFY COLUMN `total_deadline` DATETIME NOT NULL COMMENT '小组作业最终截止时间（总DDL）';

CREATE TABLE `team_member` (
    -- 雪花 ID (64位整数)
                                `id` BIGINT NOT NULL COMMENT '唯一ID',

    -- 关联团队 ID
                                `team_id` BIGINT NOT NULL COMMENT '小组ID',

    -- 关联用户 ID
                                `user_id` BIGINT NOT NULL COMMENT '用户ID',

    -- 角色枚举：1-3 (例如：1-组长, 2-管理员, 3-普通组员)
    -- 使用 TINYINT 存储，所有字段不能为空
                                `role` TINYINT NOT NULL COMMENT '角色枚举: 1-组长, 2-管理员, 3-普通组员',

    -- 角色描述
                                `role_description` VARCHAR(100) DEFAULT NULL COMMENT '角色描述',

    -- 加入时间
                                `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入小组时间',

                                PRIMARY KEY (`id`),

    -- 复合唯一索引：防止同一个用户重复加入同一个小组（非常重要！）
                                UNIQUE KEY `uk_team_user` (`team_id`, `user_id`),

    -- 索引优化：方便查询某个用户参加的所有小组
                                KEY `idx_user_id` (`user_id`),

    -- 索引优化：方便查询某个小组的所有成员
                                KEY `idx_team_id` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='小组库成员关联表';

CREATE TABLE `task_list` (
    -- 雪花 ID (64位整数)
                              `id` BIGINT NOT NULL COMMENT '雪花算法生成的分布式唯一ID',

    -- 关联团队 ID
                              `team_id` BIGINT NOT NULL COMMENT '所属小组ID',

    -- 清单标题
                              `title` VARCHAR(150) NOT NULL COMMENT '任务清单标题',

    -- 清单描述：允许为空 (NULL)
                              `description` TEXT DEFAULT NULL COMMENT '任务清单详细描述',

    -- 创建者 ID，关联 users 表的 id
                              `creator_id` BIGINT NOT NULL COMMENT '创建者用户ID',

    -- 清单截止日期：记录该清单下所有任务中最晚的一个
                              `deadline` DATETIME NOT NULL COMMENT '清单最终截止日期',

    -- 创建时间
                              `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

                              PRIMARY KEY (`id`),

    -- 索引优化：方便查询某个小组的所有任务清单
                              KEY `idx_team_id` (`team_id`),

    -- 索引优化：方便查询某个用户创建的清单
                              KEY `idx_creator_id` (`creator_id`),

    -- 索引优化：方便 AI 进行 DDL 扫描提醒
                              KEY `idx_deadline` (`deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='小组项目任务清单表';

CREATE TABLE `task` (
    -- 雪花 ID (64位整数)
                         `id` BIGINT NOT NULL COMMENT '雪花算法生成的分布式唯一ID',

    -- 关联的任务清单 ID (一对多关系的关键)
                         `task_list_id` BIGINT NOT NULL COMMENT '所属任务清单ID',

    -- 任务具体内容/描述
                         `description` VARCHAR(500) NOT NULL COMMENT '任务具体描述',

    -- 任务状态：0-未完成, 1-已完成
    -- 使用 TINYINT 存储，节省空间
                         `status` TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态: 0-待办, 1-完成',

    -- 任务完成后的描述/备注
                         `completion_note` VARCHAR(100) DEFAULT NULL COMMENT '任务完成后的描述/备注',

    -- 任务项自己的 DDL
                         `deadline` DATETIME NOT NULL COMMENT '该任务项的截止日期',

    -- 扩展字段：建议增加创建和更新时间，方便排序和追踪
                         `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                         PRIMARY KEY (`id`),

    -- 索引优化：核心索引，用于加载某个清单下的所有任务
                         KEY `idx_task_list_id` (`task_list_id`),

    -- 索引优化：方便按状态和时间进行筛选（比如：查询所有未完成的紧急任务）
                         KEY `idx_status_deadline` (`status`, `deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='具体任务项表';

CREATE TABLE `task_assignment` (
    -- 雪花 ID (64位整数)
                                    `id` BIGINT NOT NULL COMMENT '雪花算法生成的分布式唯一ID',

    -- 关联的具体任务项 ID
                                    `task_id` BIGINT NOT NULL COMMENT '关联的任务项ID',

    -- 认领该任务的用户 ID
                                    `user_id` BIGINT NOT NULL COMMENT '认领人用户ID',

    -- 扩展字段：建议增加认领时间，方便记录谁先领的任务
                                    `assign_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '认领/分配时间',

                                    PRIMARY KEY (`id`),

    -- 复合唯一索引：防止同一个用户重复认领同一个任务
                                    UNIQUE KEY `uk_task_user` (`task_id`, `user_id`),

    -- 索引优化：方便查询某个任务的所有负责人
                                    KEY `idx_task_id` (`task_id`),

    -- 索引优化：核心索引，用于查询“我的任务”
                                    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='任务负责人分配表';

-- 加入小组请求表（id 由应用侧雪花算法生成，不使用自增）
CREATE TABLE `team_join_request` (
                                     `id` BIGINT NOT NULL COMMENT '雪花ID',
                                     `user_id` BIGINT NOT NULL COMMENT '申请用户ID',
                                     `team_id` BIGINT NOT NULL COMMENT '目标小组ID',
                                      `description` VARCHAR(500) default NULL COMMENT '申请描述',
                                     `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-待处理，1-已同意，2-已拒绝',
                                     `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     PRIMARY KEY (`id`),
                                     KEY `idx_team_id_status` (`team_id`, `status`),
                                     KEY `idx_user_id_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='加入小组请求表';

-- 在现有 team_join_request 表上新增约束
ALTER TABLE `team_join_request`
    ADD COLUMN `pending_key` TINYINT
        GENERATED ALWAYS AS (CASE WHEN `status` = 0 THEN 1 ELSE NULL END) STORED
        COMMENT '待处理唯一约束辅助列',
    ADD UNIQUE KEY `uk_user_team_pending` (`user_id`, `team_id`, `pending_key`);

CREATE TABLE `team_message` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                `title` VARCHAR(100) NOT NULL COMMENT '消息标题',
                                `content` TEXT NOT NULL COMMENT '消息内容',
                                `team_id` BIGINT NOT NULL COMMENT '所属小组ID',
                                `type` tinyint NOT NULL COMMENT '消息类型',
                                `user_id` BIGINT NOT NULL COMMENT '接收用户ID',
                                related_url VARCHAR(200) COMMENT '跳转URL',
                                `is_processed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已处理: 0-未处理, 1-已处理',
                                `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '处理时间',
                                PRIMARY KEY (`id`),
                                KEY `idx_team_id` (`team_id`),
                                KEY `idx_user_id` (`user_id`),
                                KEY `idx_is_processed` (`is_processed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- 小组文档元数据表（仅存信息，不存正文）
CREATE TABLE `document` (
                               `id` BIGINT UNSIGNED NOT NULL COMMENT '雪花ID',
                               `team_id` BIGINT UNSIGNED NOT NULL COMMENT '小组ID',
                               `title` VARCHAR(200) NOT NULL COMMENT '文档标题',
                               `type` TINYINT UNSIGNED NOT NULL COMMENT '文档业务类型：1-资料文档 2-协作文档',
                               `storage_path` VARCHAR(512) NOT NULL COMMENT '对象存储路径（OSS Key/URL）',
                               `file_type` VARCHAR(32) NOT NULL COMMENT '文件类型：pdf/docx/md/txt等',
                               `file_size` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '文件大小(字节)',
                               `creator_id` BIGINT UNSIGNED NOT NULL COMMENT '上传人ID',
                               `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),

                               KEY `idx_team_type_ctime` (`team_id`, `type`, `create_time`),
                               KEY `idx_team_ctime` (`team_id`, `create_time`),
                               KEY `idx_creator_ctime` (`creator_id`, `create_time`),
                               KEY `idx_storage_path` (`storage_path`(191)),
                               KEY `idx_file_type` (`file_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小组文档元数据表';

-- 1) 会话主表
CREATE TABLE IF NOT EXISTS ai_chat_session (
                                               id                BIGINT       NOT NULL COMMENT '会话ID(雪花ID)',
                                               team_id           BIGINT       NOT NULL COMMENT '小组ID',
                                               creator_user_id   BIGINT       NOT NULL COMMENT '创建人用户ID',
                                               title             VARCHAR(128) NOT NULL DEFAULT '' COMMENT '会话标题',
    session_type     VARCHAR(32)  NOT NULL DEFAULT 'TEAM_MENTOR' COMMENT '会话类型:TEAM_MENTOR=小组导师,COLLAB_DOC=协作文档助手',
    document_id      BIGINT       NULL COMMENT '关联协作文档ID',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1=进行中,2=已关闭',
    last_message_at   DATETIME     NULL COMMENT '最后一条消息时间',
    message_count     INT          NOT NULL DEFAULT 0 COMMENT '消息数量',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0=否,1=是',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_team_last_message (team_id, last_message_at),
    KEY idx_team_type_doc_last_message (team_id, session_type, document_id, last_message_at),
    KEY idx_creator_created_at (creator_user_id, created_at),
    KEY idx_status_deleted (status, deleted)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI导师对话会话表';


-- 2) 消息索引表（正文在 MongoDB）
CREATE TABLE IF NOT EXISTS ai_chat_message_index (
                                                     id                BIGINT       NOT NULL COMMENT '消息索引ID(雪花ID)',
                                                     session_id        BIGINT       NOT NULL COMMENT '会话ID',
                                                     team_id           BIGINT       NOT NULL COMMENT '小组ID',
                                                     user_id           BIGINT       NULL COMMENT '发送者用户ID(系统消息可为空)',
                                                     sender_type       VARCHAR(16)  NOT NULL COMMENT '发送者:USER/ASSISTANT/SYSTEM',
    message_type      VARCHAR(32)  NOT NULL DEFAULT 'TEXT' COMMENT '消息类型:TEXT/TASK_SUGGESTION/SUMMARY/MEMORY_REF',
    mongo_message_id  VARCHAR(64)  NOT NULL COMMENT 'Mongo文档ID',
    trace_id          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '链路追踪ID',
    token_count       INT          NOT NULL DEFAULT 0 COMMENT 'Mongo chat_messages.content 的文本token数（不含提示词和文档上下文）',
    short_term_active TINYINT      NOT NULL DEFAULT 1 COMMENT '是否仍属于当前短期记忆窗口:0=否,1=是',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:1=PENDING,2=DONE,3=FAILED',
    error_msg         VARCHAR(512) NOT NULL DEFAULT '' COMMENT '失败原因',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除:0=否,1=是',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_session_created_at (session_id, created_at),
    KEY idx_session_status_deleted (session_id, status, deleted),
    KEY idx_session_short_term_active (session_id, short_term_active),
    KEY idx_team_created_at (team_id, created_at),
    KEY idx_trace_id (trace_id),
    UNIQUE KEY uk_mongo_message_id (mongo_message_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI导师对话消息索引表';

CREATE TABLE IF NOT EXISTS ai_chat_memory_state (
                                                    id                      BIGINT       NOT NULL COMMENT '记忆状态ID(雪花ID)',
                                                    session_id              BIGINT       NOT NULL COMMENT '会话ID',
                                                    team_id                 BIGINT       NOT NULL COMMENT '小组ID',
                                                    short_term_token_count  INT          NOT NULL DEFAULT 0 COMMENT '当前短期窗口总token',
                                                    short_term_message_count INT         NOT NULL DEFAULT 0 COMMENT '当前短期窗口消息数',
                                                    mid_term_mongo_id       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '中期记忆Mongo文档ID',
                                                    mid_term_token_count    INT          NOT NULL DEFAULT 0 COMMENT '中期记忆总token',
                                                    last_mid_term_compress_at DATETIME   NULL COMMENT '最近一次中期压缩时间',
                                                    last_mid_term_source_token_count INT NOT NULL DEFAULT 0 COMMENT '最近一次中期压缩前短期token',
                                                    last_mid_term_target_token_count INT NOT NULL DEFAULT 0 COMMENT '最近一次中期压缩后短期token',
                                                    last_mid_term_summary_token_count INT NOT NULL DEFAULT 0 COMMENT '最近一次中期摘要token',
                                                    last_mid_term_removed_message_count INT NOT NULL DEFAULT 0 COMMENT '最近一次中期移除消息数',
                                                    last_mid_term_removed_message_ids VARCHAR(512) NOT NULL DEFAULT '' COMMENT '最近一次中期移除消息ID列表',
                                                    last_mid_term_status    VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '最近一次中期压缩状态',
                                                    last_mid_term_error_msg VARCHAR(512) NOT NULL DEFAULT '' COMMENT '最近一次中期压缩错误',
                                                    early_term_mongo_id     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '早期记忆Mongo文档ID',
                                                    early_term_token_count  INT          NOT NULL DEFAULT 0 COMMENT '早期记忆总token',
                                                    last_early_term_compress_at DATETIME NULL COMMENT '最近一次早期压缩时间',
                                                    last_early_term_source_token_count INT NOT NULL DEFAULT 0 COMMENT '最近一次早期压缩前中期token',
                                                    last_early_term_target_token_count INT NOT NULL DEFAULT 0 COMMENT '最近一次早期压缩后中期token',
                                                    last_early_term_summary_token_count INT NOT NULL DEFAULT 0 COMMENT '最近一次早期摘要token',
                                                    last_early_term_removed_segment_count INT NOT NULL DEFAULT 0 COMMENT '最近一次早期移除段数',
                                                    last_early_term_removed_token_count INT NOT NULL DEFAULT 0 COMMENT '最近一次早期移除token',
                                                    last_early_term_status  VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '最近一次早期压缩状态',
                                                    last_early_term_error_msg VARCHAR(512) NOT NULL DEFAULT '' COMMENT '最近一次早期压缩错误',
                                                    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                                    PRIMARY KEY (id),
                                                    UNIQUE KEY uk_session_id (session_id),
                                                    KEY idx_team_id (team_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI导师记忆生命周期状态表';

ALTER TABLE ai_chat_memory_state
    ADD COLUMN last_mid_term_compress_at DATETIME NULL COMMENT '最近一次中期压缩时间'
        AFTER mid_term_token_count;

-- 协作文档页 AI 会话类型隔离字段：已存在 ai_chat_session 表时执行
ALTER TABLE ai_chat_session
    ADD COLUMN session_type VARCHAR(32) NOT NULL DEFAULT 'TEAM_MENTOR' COMMENT '会话类型:TEAM_MENTOR=小组导师,COLLAB_DOC=协作文档助手'
        AFTER title,
    ADD COLUMN document_id BIGINT NULL COMMENT '关联协作文档ID'
        AFTER session_type,
    ADD KEY idx_team_type_doc_last_message (team_id, session_type, document_id, last_message_at);

-- Adaptive Plan + ReAct runtime audit. Execute once after the existing AI chat tables.
CREATE TABLE IF NOT EXISTS ai_agent_run (
    id BIGINT NOT NULL COMMENT 'Snowflake agent run ID',
    session_id BIGINT NOT NULL COMMENT 'AI chat session ID',
    team_id BIGINT NOT NULL COMMENT 'Team ID',
    user_id BIGINT NOT NULL COMMENT 'Operator user ID',
    trace_id VARCHAR(64) NOT NULL COMMENT 'Request trace ID',
    scene_type VARCHAR(32) NOT NULL COMMENT 'TEAM_MENTOR/COLLAB_DOC',
    goal VARCHAR(500) NOT NULL COMMENT 'User goal snapshot',
    plan_json JSON NULL COMMENT 'Structured adaptive plan snapshot',
    plan_version INT NOT NULL DEFAULT 1 COMMENT 'Plan revision version',
    status VARCHAR(32) NOT NULL COMMENT 'RUNNING/WAITING_CONFIRMATION/COMPLETED/FAILED/CANCELLED',
    step_count INT NOT NULL DEFAULT 0 COMMENT 'Persisted execution step count',
    prompt_tokens INT NOT NULL DEFAULT 0 COMMENT 'Prompt token consumption',
    completion_tokens INT NOT NULL DEFAULT 0 COMMENT 'Completion token consumption',
    error_msg VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'Terminal failure reason',
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Started time',
    finished_at DATETIME NULL COMMENT 'Finished time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    KEY idx_session_created_at (session_id, created_at),
    KEY idx_team_status_created_at (team_id, status, created_at),
    KEY idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI adaptive Plan and ReAct run audit';

CREATE TABLE IF NOT EXISTS ai_agent_step (
    id BIGINT NOT NULL COMMENT 'Snowflake agent step ID',
    run_id BIGINT NOT NULL COMMENT 'Agent run ID',
    step_no INT NOT NULL COMMENT 'Execution order starting at 1',
    step_type VARCHAR(32) NOT NULL COMMENT 'ANSWER/READ/ANALYZE/DRAFT/WRITE/VERIFY/FINISH',
    tool_name VARCHAR(100) NULL COMMENT 'Invoked tool name when applicable',
    status VARCHAR(32) NOT NULL COMMENT 'RUNNING/DONE/FAILED/WAITING_CONFIRMATION',
    decision_summary VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'Safe concise execution summary',
    observation_summary VARCHAR(1000) NOT NULL DEFAULT '' COMMENT 'Sanitized tool result summary',
    duration_ms INT NOT NULL DEFAULT 0 COMMENT 'Step duration',
    prompt_tokens INT NOT NULL DEFAULT 0 COMMENT 'Step prompt tokens',
    completion_tokens INT NOT NULL DEFAULT 0 COMMENT 'Step completion tokens',
    started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Started time',
    finished_at DATETIME NULL COMMENT 'Finished time',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_step_no (run_id, step_no),
    KEY idx_run_created_at (run_id, created_at),
    KEY idx_status_created_at (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI adaptive Plan and ReAct step audit';

CREATE TABLE IF NOT EXISTS ai_action_draft (
    id BIGINT NOT NULL COMMENT 'Snowflake draft ID',
    run_id BIGINT NOT NULL COMMENT 'Agent run ID',
    team_id BIGINT NOT NULL COMMENT 'Team ID',
    creator_user_id BIGINT NOT NULL COMMENT 'User who must confirm this action',
    action_type VARCHAR(32) NOT NULL COMMENT 'EMAIL_SEND',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING_CONFIRMATION/SENDING/EXECUTED/CANCELLED',
    payload_json JSON NOT NULL COMMENT 'Editable proposal payload',
    result_summary VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'Execution result',
    error_msg VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'Execution error',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    executed_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_run_action (run_id, action_type),
    KEY idx_creator_status (creator_user_id, status),
    KEY idx_team_created (team_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI controlled action proposal drafts';
