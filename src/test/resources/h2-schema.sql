-- 单元测试用 H2（MySQL 兼容模式）最小表结构；与生产 DDL 语义对齐，便于 Spring 上下文启动。

CREATE TABLE IF NOT EXISTS model_registry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_code VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    api_key VARCHAR(255) NOT NULL,
    base_url VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    is_chat BOOLEAN NOT NULL DEFAULT FALSE,
    is_compression BOOLEAN NOT NULL DEFAULT FALSE,
    is_agent BOOLEAN NOT NULL DEFAULT FALSE,
    is_multimodal BOOLEAN NOT NULL DEFAULT FALSE,
    del_flag TINYINT NOT NULL DEFAULT 0,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    update_at TIMESTAMP,
    create_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);

CREATE TABLE IF NOT EXISTS `session` (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(64),
    title VARCHAR(255),
    del_flag TINYINT NOT NULL DEFAULT 0,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    update_at TIMESTAMP,
    create_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS outer_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    content_length INT NOT NULL DEFAULT 0,
    del_flag TINYINT NOT NULL DEFAULT 0,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    update_at TIMESTAMP,
    create_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
CREATE INDEX IF NOT EXISTS idx_outer_session_id ON outer_message(session_id, id DESC);
CREATE INDEX IF NOT EXISTS idx_outer_session_create_at_desc ON outer_message(session_id, create_at DESC);

CREATE TABLE IF NOT EXISTS inner_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    content_length INT NOT NULL DEFAULT 0,
    compressed_length INT,
    compress_method VARCHAR(20),
    token_count INT DEFAULT 0,
    del_flag TINYINT NOT NULL DEFAULT 0,
    create_by VARCHAR(64),
    update_by VARCHAR(64),
    update_at TIMESTAMP,
    create_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
