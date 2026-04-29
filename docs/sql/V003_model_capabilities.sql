/*
 * version0.3：model_registry 用途与多模态标志位。
 * 执行前请先备份；已有数据需在 ALTER 后执行 UPDATE（见文末示例）。
 */

ALTER TABLE `model_registry`
    ADD COLUMN `is_chat` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可用于对话' AFTER `is_active`,
    ADD COLUMN `is_compression` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可用于压缩（摘要等）' AFTER `is_chat`,
    ADD COLUMN `is_agent` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可用于智能体（工具调用等）' AFTER `is_compression`,
    ADD COLUMN `is_multimodal` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否具备多模态能力' AFTER `is_agent`;

/*
-- 可选：将已有启用模型标记为可用于对话（按实际裁剪）
UPDATE `model_registry` SET `is_chat` = 1 WHERE `is_active` = 1 AND `del_flag` = 0;

-- 如需某模型兼顾压缩摘要：
-- UPDATE `model_registry` SET `is_compression` = 1 WHERE `model_code` = 'xxx';
*/
