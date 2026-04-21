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

                         `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

                         PRIMARY KEY (`id`),
    -- 邀请码唯一索引，确保搜索时的精准性
                         UNIQUE KEY `uk_invite_code` (`invite_code`),
    -- 为 owner_id 建立索引，方便查询某个用户创建的所有小组
                         KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='小组/团队信息表';

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
