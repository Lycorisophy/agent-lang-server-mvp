ALTER TABLE `model_registry`
    ADD COLUMN `is_chat`       tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否可用于对话',
    ADD COLUMN `is_compression` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否可用于压缩（摘要等）',
    ADD COLUMN `is_agent`      tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否可用于智能体（工具调用等）',
    ADD COLUMN `is_multimodal` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否具备多模态能力（图片、音频等）';