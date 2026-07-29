-- TeamUp MySQL schema initialization
-- Target: MySQL 8.0+; execute this file against an EMPTY TeamUp database.
-- This is a final schema, not an incremental migration script. It intentionally
-- contains no ALTER TABLE, DROP TABLE, or seed data statements.

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `user` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake user ID',
    `email` VARCHAR(100) NOT NULL COMMENT 'User email',
    `username` VARCHAR(50) NOT NULL COMMENT 'Display name',
    `password` VARCHAR(255) NOT NULL COMMENT 'BCrypt password hash',
    `gender` TINYINT NOT NULL DEFAULT 1 COMMENT '1=male, 2=female',
    `avatar` TINYINT NOT NULL DEFAULT 1 COMMENT 'Preset avatar number',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User account';

CREATE TABLE `team` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake team ID',
    `name` VARCHAR(100) NOT NULL COMMENT 'Team name',
    `owner_id` BIGINT NOT NULL COMMENT 'Creator user ID',
    `invite_code` VARCHAR(20) NOT NULL COMMENT 'Unique invite code',
    `description` TEXT NULL COMMENT 'Team description',
    `total_deadline` DATETIME NOT NULL COMMENT 'Team final deadline',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_team_invite_code` (`invite_code`),
    KEY `idx_team_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Team';

CREATE TABLE `team_member` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake membership ID',
    `team_id` BIGINT NOT NULL COMMENT 'Team ID',
    `user_id` BIGINT NOT NULL COMMENT 'User ID',
    `role` TINYINT NOT NULL COMMENT '1=Captain, 2=Leader, 3=Member',
    `role_description` VARCHAR(100) NULL COMMENT 'Member role description',
    `join_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Joined time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_team_member_team_user` (`team_id`, `user_id`),
    KEY `idx_team_member_user_join` (`user_id`, `join_time`),
    KEY `idx_team_member_team_role_join` (`team_id`, `role`, `join_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Team membership';

CREATE TABLE `team_join_request` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake join request ID',
    `user_id` BIGINT NOT NULL COMMENT 'Applicant user ID',
    `team_id` BIGINT NOT NULL COMMENT 'Target team ID',
    `description` VARCHAR(500) NULL COMMENT 'Application description',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=pending, 1=approved, 2=rejected',
    `pending_key` TINYINT GENERATED ALWAYS AS (CASE WHEN `status` = 0 THEN 1 ELSE NULL END) STORED COMMENT 'Pending uniqueness helper',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_join_request_pending` (`user_id`, `team_id`, `pending_key`),
    KEY `idx_join_request_team_status_created` (`team_id`, `status`, `create_time`),
    KEY `idx_join_request_team_status_updated` (`team_id`, `status`, `update_time`),
    KEY `idx_join_request_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Team join request';

CREATE TABLE `team_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment message ID',
    `title` VARCHAR(100) NOT NULL COMMENT 'Message title',
    `content` TEXT NOT NULL COMMENT 'Message content',
    `team_id` BIGINT NOT NULL COMMENT 'Related team ID',
    `type` TINYINT NOT NULL COMMENT 'Message type',
    `user_id` BIGINT NOT NULL COMMENT 'Recipient user ID',
    `related_url` VARCHAR(200) NULL COMMENT 'Related frontend path',
    `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '0=unread, 1=read',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_team_message_team_id` (`team_id`),
    KEY `idx_team_message_user_created` (`user_id`, `create_time`),
    KEY `idx_team_message_user_read` (`user_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User notification';

CREATE TABLE `task_list` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake task list ID',
    `team_id` BIGINT NOT NULL COMMENT 'Team ID',
    `title` VARCHAR(150) NOT NULL COMMENT 'Task list title',
    `description` TEXT NULL COMMENT 'Task list description',
    `creator_id` BIGINT NOT NULL COMMENT 'Creator user ID',
    `deadline` DATETIME NOT NULL COMMENT 'Task list deadline',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    PRIMARY KEY (`id`),
    KEY `idx_task_list_team_deadline_created` (`team_id`, `deadline`, `create_time`),
    KEY `idx_task_list_creator_id` (`creator_id`),
    KEY `idx_task_list_deadline` (`deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Task list';

CREATE TABLE `task` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake task ID',
    `task_list_id` BIGINT NOT NULL COMMENT 'Task list ID',
    `description` VARCHAR(500) NOT NULL COMMENT 'Task description',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=todo, 1=completed',
    `completion_note` VARCHAR(100) NULL COMMENT 'Completion note',
    `deadline` DATETIME NOT NULL COMMENT 'Task deadline',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_task_list_status_deadline_created` (`task_list_id`, `status`, `deadline`, `create_time`),
    KEY `idx_task_status_deadline` (`status`, `deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Task item';

CREATE TABLE `task_assignment` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake assignment ID',
    `task_id` BIGINT NOT NULL COMMENT 'Task ID',
    `user_id` BIGINT NOT NULL COMMENT 'Assignee user ID',
    `assign_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Assigned time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_assignment_task_user` (`task_id`, `user_id`),
    KEY `idx_task_assignment_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Task assignment';

CREATE TABLE `document` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake document ID',
    `team_id` BIGINT NOT NULL COMMENT 'Team ID',
    `title` VARCHAR(200) NOT NULL COMMENT 'Document title',
    `type` TINYINT NOT NULL COMMENT '1=resource document, 2=collaboration document',
    `storage_path` VARCHAR(512) NOT NULL COMMENT 'OSS path or URL; empty for collaboration documents',
    `file_type` VARCHAR(32) NOT NULL COMMENT 'File extension or collaboration type',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT 'File size in bytes',
    `creator_id` BIGINT NOT NULL COMMENT 'Creator user ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_document_team_type_created` (`team_id`, `type`, `create_time`),
    KEY `idx_document_team_updated` (`team_id`, `update_time`),
    KEY `idx_document_creator_created` (`creator_id`, `create_time`),
    KEY `idx_document_storage_path` (`storage_path`(191)),
    KEY `idx_document_file_type` (`file_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Document metadata';

CREATE TABLE `ai_chat_session` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake session ID',
    `team_id` BIGINT NOT NULL COMMENT 'Team ID',
    `creator_user_id` BIGINT NOT NULL COMMENT 'Creator user ID',
    `title` VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'Session title',
    `session_type` VARCHAR(32) NOT NULL DEFAULT 'TEAM_MENTOR' COMMENT 'TEAM_MENTOR or COLLAB_DOC',
    `document_id` BIGINT NULL COMMENT 'Collaboration document ID for COLLAB_DOC sessions',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=active, 2=closed',
    `last_message_at` DATETIME NULL COMMENT 'Last message time',
    `message_count` INT NOT NULL DEFAULT 0 COMMENT 'Message count',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0=active, 1=logically deleted',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_chat_session_team_last_message` (`team_id`, `last_message_at`),
    KEY `idx_chat_session_creator_team_type_doc` (`creator_user_id`, `team_id`, `session_type`, `document_id`, `deleted`),
    KEY `idx_chat_session_team_type_doc_last_message` (`team_id`, `session_type`, `document_id`, `last_message_at`),
    KEY `idx_chat_session_status_deleted` (`status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI chat session';

CREATE TABLE `ai_chat_message_index` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake message index ID',
    `session_id` BIGINT NOT NULL COMMENT 'Session ID',
    `team_id` BIGINT NOT NULL COMMENT 'Team ID',
    `user_id` BIGINT NULL COMMENT 'Sender user ID; null for system message',
    `sender_type` VARCHAR(16) NOT NULL COMMENT 'USER, ASSISTANT, or SYSTEM',
    `message_type` VARCHAR(32) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT, TASK_SUGGESTION, SUMMARY, or MEMORY_REF',
    `mongo_message_id` VARCHAR(64) NOT NULL COMMENT 'MongoDB message document ID',
    `trace_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Request trace ID',
    `token_count` INT NOT NULL DEFAULT 0 COMMENT 'Content token count',
    `short_term_active` TINYINT NOT NULL DEFAULT 1 COMMENT '0=not in short-term memory, 1=active',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1=pending, 2=done, 3=failed',
    `error_msg` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Failure reason',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '0=active, 1=logically deleted',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_message_mongo_id` (`mongo_message_id`),
    KEY `idx_chat_message_session_created` (`session_id`, `created_at`),
    KEY `idx_chat_message_session_status_deleted` (`session_id`, `status`, `deleted`),
    KEY `idx_chat_message_session_short_term` (`session_id`, `short_term_active`),
    KEY `idx_chat_message_session_trace_sender_created` (`session_id`, `trace_id`, `sender_type`, `created_at`),
    KEY `idx_chat_message_team_created` (`team_id`, `created_at`),
    KEY `idx_chat_message_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI chat message index';

CREATE TABLE `ai_chat_memory_state` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake memory state ID',
    `session_id` BIGINT NOT NULL COMMENT 'Session ID',
    `team_id` BIGINT NOT NULL COMMENT 'Team ID',
    `short_term_token_count` INT NOT NULL DEFAULT 0 COMMENT 'Short-term token count',
    `short_term_message_count` INT NOT NULL DEFAULT 0 COMMENT 'Short-term message count',
    `mid_term_mongo_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Mid-term MongoDB memory ID',
    `mid_term_token_count` INT NOT NULL DEFAULT 0 COMMENT 'Mid-term token count',
    `last_mid_term_compress_at` DATETIME NULL COMMENT 'Last mid-term compression time',
    `last_mid_term_source_token_count` INT NOT NULL DEFAULT 0 COMMENT 'Mid-term source token count',
    `last_mid_term_target_token_count` INT NOT NULL DEFAULT 0 COMMENT 'Mid-term target token count',
    `last_mid_term_summary_token_count` INT NOT NULL DEFAULT 0 COMMENT 'Mid-term summary token count',
    `last_mid_term_removed_message_count` INT NOT NULL DEFAULT 0 COMMENT 'Mid-term removed message count',
    `last_mid_term_removed_message_ids` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Mid-term removed message IDs',
    `last_mid_term_status` VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'Last mid-term compression status',
    `last_mid_term_error_msg` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Last mid-term compression error',
    `early_term_mongo_id` VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'Early-term MongoDB memory ID',
    `early_term_token_count` INT NOT NULL DEFAULT 0 COMMENT 'Early-term token count',
    `last_early_term_compress_at` DATETIME NULL COMMENT 'Last early-term compression time',
    `last_early_term_source_token_count` INT NOT NULL DEFAULT 0 COMMENT 'Early-term source token count',
    `last_early_term_target_token_count` INT NOT NULL DEFAULT 0 COMMENT 'Early-term target token count',
    `last_early_term_summary_token_count` INT NOT NULL DEFAULT 0 COMMENT 'Early-term summary token count',
    `last_early_term_removed_segment_count` INT NOT NULL DEFAULT 0 COMMENT 'Early-term removed segment count',
    `last_early_term_removed_token_count` INT NOT NULL DEFAULT 0 COMMENT 'Early-term removed token count',
    `last_early_term_status` VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'Last early-term compression status',
    `last_early_term_error_msg` VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'Last early-term compression error',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_memory_session_id` (`session_id`),
    KEY `idx_chat_memory_team_id` (`team_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI chat memory lifecycle state';

CREATE TABLE `ai_agent_run` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake agent run ID',
    `session_id` BIGINT NOT NULL COMMENT 'AI chat session ID',
    `team_id` BIGINT NOT NULL COMMENT 'Team ID',
    `user_id` BIGINT NOT NULL COMMENT 'Operator user ID',
    `trace_id` VARCHAR(64) NOT NULL COMMENT 'Request trace ID',
    `scene_type` VARCHAR(32) NOT NULL COMMENT 'TEAM_MENTOR or COLLAB_DOC',
    `goal` VARCHAR(500) NOT NULL COMMENT 'User goal snapshot',
    `plan_json` JSON NULL COMMENT 'Structured adaptive plan snapshot',
    `plan_version` INT NOT NULL DEFAULT 1 COMMENT 'Plan revision version',
    `status` VARCHAR(32) NOT NULL COMMENT 'RUNNING, WAITING_CONFIRMATION, COMPLETED, FAILED, or CANCELLED',
    `step_count` INT NOT NULL DEFAULT 0 COMMENT 'Persisted step count',
    `prompt_tokens` INT NOT NULL DEFAULT 0 COMMENT 'Prompt token count',
    `completion_tokens` INT NOT NULL DEFAULT 0 COMMENT 'Completion token count',
    `error_msg` VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'Terminal failure reason',
    `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Started time',
    `finished_at` DATETIME NULL COMMENT 'Finished time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_agent_run_session_created` (`session_id`, `created_at`),
    KEY `idx_agent_run_team_status_created` (`team_id`, `status`, `created_at`),
    KEY `idx_agent_run_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI agent run audit';

CREATE TABLE `ai_agent_step` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake agent step ID',
    `run_id` BIGINT NOT NULL COMMENT 'Agent run ID',
    `step_no` INT NOT NULL COMMENT 'Execution order starting at 1',
    `step_type` VARCHAR(32) NOT NULL COMMENT 'ANSWER, READ, ANALYZE, DRAFT, WRITE, VERIFY, or FINISH',
    `tool_name` VARCHAR(100) NULL COMMENT 'Tool name when applicable',
    `status` VARCHAR(32) NOT NULL COMMENT 'RUNNING, DONE, FAILED, or WAITING_CONFIRMATION',
    `decision_summary` VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'Sanitized decision summary',
    `observation_summary` VARCHAR(1000) NOT NULL DEFAULT '' COMMENT 'Sanitized tool result summary',
    `duration_ms` INT NOT NULL DEFAULT 0 COMMENT 'Duration in milliseconds',
    `prompt_tokens` INT NOT NULL DEFAULT 0 COMMENT 'Prompt token count',
    `completion_tokens` INT NOT NULL DEFAULT 0 COMMENT 'Completion token count',
    `started_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Started time',
    `finished_at` DATETIME NULL COMMENT 'Finished time',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_step_run_step_no` (`run_id`, `step_no`),
    KEY `idx_agent_step_run_created` (`run_id`, `created_at`),
    KEY `idx_agent_step_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI agent step audit';

CREATE TABLE `ai_action_draft` (
    `id` BIGINT NOT NULL COMMENT 'Snowflake action draft ID',
    `run_id` BIGINT NOT NULL COMMENT 'Agent run ID',
    `team_id` BIGINT NOT NULL COMMENT 'Team ID',
    `creator_user_id` BIGINT NOT NULL COMMENT 'User who must confirm the draft',
    `action_type` VARCHAR(32) NOT NULL COMMENT 'EMAIL_SEND, TASK_LIST_CREATE, or COLLAB_DOCUMENT_PATCH',
    `status` VARCHAR(32) NOT NULL COMMENT 'PENDING_CONFIRMATION, SENDING, APPLYING, EXECUTED, REJECTED, or CONFLICTED',
    `payload_json` JSON NOT NULL COMMENT 'Editable proposal payload',
    `result_summary` VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'Execution or rejection result',
    `error_msg` VARCHAR(500) NOT NULL DEFAULT '' COMMENT 'Execution error',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created time',
    `executed_at` DATETIME NULL COMMENT 'Confirmed, rejected, or completed time',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated time',
    PRIMARY KEY (`id`),
    KEY `idx_action_draft_run_action` (`run_id`, `action_type`),
    KEY `idx_action_draft_run_creator_action_status_created` (`run_id`, `creator_user_id`, `action_type`, `status`, `created_at`),
    KEY `idx_action_draft_creator_status` (`creator_user_id`, `status`),
    KEY `idx_action_draft_team_created` (`team_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI controlled action draft';

SET FOREIGN_KEY_CHECKS = 1;
