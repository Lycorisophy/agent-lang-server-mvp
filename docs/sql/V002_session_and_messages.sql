-- 二期：会话与双表消息（外表用户可见，内表为大模型上下文）
-- 执行前请确保已执行 V001_model.sql（model_registry 等）

CREATE TABLE `session` (
    `id`            VARCHAR(36) PRIMARY KEY COMMENT 'UUID',
    `user_id`       VARCHAR(64) COMMENT '用户标识',
    `title`         VARCHAR(255) COMMENT '会话标题',
    `del_flag`      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删除 1-已删除',
    `create_by`     VARCHAR(64) COMMENT '创建人',
    `update_by`     VARCHAR(64) COMMENT '更新人',
    `update_at`     DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

CREATE TABLE `outer_message` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `session_id`      VARCHAR(36) NOT NULL COMMENT '会话ID',
    `role`            VARCHAR(20) NOT NULL COMMENT 'user/assistant',
    `content`         TEXT NOT NULL COMMENT '用户可见内容',
    `content_length`  INT NOT NULL DEFAULT 0 COMMENT '内容长度（字符数）',
    `del_flag`        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_by`       VARCHAR(64) COMMENT '创建人',
    `update_by`       VARCHAR(64) COMMENT '更新人',
    `update_at`       DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    `create_at`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '精确到毫秒，保证排序',
    INDEX `idx_session` (`session_id`, `create_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外表消息，用户可见';

CREATE TABLE `inner_message` (
    `id`                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    `session_id`          VARCHAR(36) NOT NULL COMMENT '会话ID',
    `role`                VARCHAR(20) NOT NULL COMMENT 'user/assistant/system（压缩摘要）',
    `content`             TEXT NOT NULL COMMENT '实际传给大模型的内容',
    `content_length`      INT NOT NULL DEFAULT 0 COMMENT '当前内容长度',
    `compressed_length`   INT DEFAULT NULL COMMENT '压缩前原始长度（仅压缩消息有意义）',
    `compress_method`     VARCHAR(20) DEFAULT NULL COMMENT '压缩方式：summary-摘要 truncation-截断',
    `token_count`         INT DEFAULT 0 COMMENT 'token数估算，用于配额统计',
    `del_flag`            TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_by`           VARCHAR(64) COMMENT '创建人',
    `update_by`           VARCHAR(64) COMMENT '更新人',
    `update_at`           DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    `create_at`           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX `idx_session` (`session_id`, `create_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内表消息，大模型上下文';
