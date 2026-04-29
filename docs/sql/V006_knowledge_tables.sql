/*
 * version0.4 phase1: 知识库基础表
 */

CREATE TABLE `knowledge_import` (
    `import_id` VARCHAR(50) PRIMARY KEY COMMENT '导入批次ID',
    `file_name` VARCHAR(255) COMMENT '原始文件名',
    `chunk_count` INT DEFAULT 0 COMMENT '分块数量',
    `status` VARCHAR(20) DEFAULT 'processing' COMMENT 'processing/completed/failed',
    `metadata` JSON COMMENT '附加元信息',
    `del_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `update_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX `idx_knowledge_import_status_create_at` (`status`, `create_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识导入记录表';

CREATE TABLE `knowledge_chunk_ref` (
    `chunk_id` VARCHAR(50) PRIMARY KEY COMMENT '块ID（Milvus/Neo4j memory_id）',
    `import_id` VARCHAR(50) NOT NULL COMMENT '所属导入批次',
    `chunk_index` INT COMMENT '块序号',
    `del_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `update_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    CONSTRAINT `fk_knowledge_chunk_import` FOREIGN KEY (`import_id`) REFERENCES `knowledge_import` (`import_id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX `idx_knowledge_chunk_ref_import_id` (`import_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识块与导入批次关联';

CREATE TABLE `permanent_memory` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户标识',
    `content` TEXT NOT NULL COMMENT '永驻记忆内容',
    `del_flag` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `update_at` DATETIME NULL DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    INDEX `idx_permanent_memory_user_create_at` (`user_id`, `create_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='永驻记忆表';
