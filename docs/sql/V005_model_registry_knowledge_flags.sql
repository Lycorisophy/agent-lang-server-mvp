/*
 * version0.4 phase1: 模型能力位扩展（知识库）
 */

ALTER TABLE `model_registry`
    ADD COLUMN `is_embedding` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可用于向量嵌入' AFTER `is_multimodal`,
    ADD COLUMN `is_extraction` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可用于事件/事实抽取' AFTER `is_embedding`;

/*
-- 可选初始化（按环境自行调整）
-- UPDATE `model_registry` SET `is_embedding` = 1 WHERE `model_code` IN ('bge-m3', 'text-embedding-3-large');
-- UPDATE `model_registry` SET `is_extraction` = 1 WHERE `is_chat` = 1 AND `is_active` = 1;
*/
