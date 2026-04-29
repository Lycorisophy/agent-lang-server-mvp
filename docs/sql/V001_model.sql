/*
模型表
*/

CREATE TABLE `model_registry` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `model_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型代码，如 deepseek-v3',
                                  `provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '提供商, qwen/deepseek/minimax/ollama',
                                  `model_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '模型全名，如 qwen-max',
                                  `api_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'API密钥',
                                  `base_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '自定义API地址，Ollama必填',
                                  `is_active` tinyint(1) DEFAULT '1' COMMENT '是否启用',
                                  `del_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
                                  `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
                                  `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
                                  `update_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
                                  `create_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                  PRIMARY KEY (`id`) USING BTREE,
                                  UNIQUE KEY `uk_model_code` (`model_code`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='大模型信息注册表';

CREATE TABLE `token_usage_plan` (
                                    `id` bigint NOT NULL AUTO_INCREMENT,
                                    `model_code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '关联的模型代码',
                                    `user_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '用户或租户标识，NULL表示模型全局配额',
                                    `monthly_limit` bigint NOT NULL COMMENT '月度Token上限',
                                    `used_tokens` bigint DEFAULT '0' COMMENT '本月已使用Token数',
                                    `billing_cycle` varchar(7) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '计费周期, yyyy-MM',
                                    `del_flag` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
                                    `create_by` varchar(64) DEFAULT NULL COMMENT '创建人',
                                    `update_by` varchar(64) DEFAULT NULL COMMENT '更新人',
                                    `update_at` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
                                    `create_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                    PRIMARY KEY (`id`) USING BTREE,
                                    UNIQUE KEY `uk_model_user_cycle` (`model_code`,`user_id`,`billing_cycle`) USING BTREE,
                                    CONSTRAINT `token_usage_plan_ibfk_1` FOREIGN KEY (`model_code`) REFERENCES `model_registry` (`model_code`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Token使用计划表';