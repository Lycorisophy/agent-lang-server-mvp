/*
 * version0.3+: 外表历史瀑布流索引优化
 * 查询形态：
 *   1) 最近消息：WHERE session_id = ? ORDER BY id DESC LIMIT 10
 *   2) 向上翻页：WHERE session_id = ? AND id < ? ORDER BY id DESC LIMIT 10
 *   3) 兼容按 create_at 倒序展示：WHERE session_id = ? ORDER BY create_at DESC LIMIT 10
 */

ALTER TABLE `outer_message`
    ADD INDEX `idx_session_id_desc` (`session_id`, `id` DESC),
    ADD INDEX `idx_session_create_at_desc` (`session_id`, `create_at` DESC);
