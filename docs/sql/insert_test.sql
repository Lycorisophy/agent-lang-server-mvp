-- 插入示例数据
INSERT INTO `model_registry` (`model_code`, `provider`, `model_name`, `api_key`, `base_url`) VALUES
                                                                                                 ('deepseek-v3', 'deepseek', 'deepseek-chat', 'sk-your-deepseek-key', NULL),
                                                                                                 ('qwen-max', 'qwen', 'qwen-max', 'sk-your-qwen-key', NULL),
                                                                                                 ('minimax-abab6', 'minimax', 'abab6.5-chat', 'your-minimax-key', NULL),
                                                                                                 ('ollama-llama3', 'ollama', 'llama3.2', 'ollama', 'http://localhost:11434');

-- 插入示例数据
INSERT INTO `token_usage_plan` (`model_code`, `user_id`, `monthly_limit`, `billing_cycle`) VALUES
                                                                                               ('deepseek-v3', NULL, 1000000, '2026-04'),
                                                                                               ('qwen-max', NULL, 500000, '2026-04');