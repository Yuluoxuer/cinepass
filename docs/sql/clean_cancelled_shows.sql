-- =============================================================================
-- 清理 cancelled 场次相关数据
-- 1. 将 cancelled 场次绑定的旧座位图 mutable 标记改回 true
-- 2. 清理 seat_status 表中 cancelled 场次的冗余数据
-- =============================================================================

-- 执行时间：2026-08-05
-- 执行结果：无需执行任何修改，数据已经是正确的状态

-- =============================================================================
-- 检查结果报告
-- =============================================================================

-- 1. cancelled 场次及其座位图情况：
--    - 共 5 条 cancelled 场次
--    - 4 条使用座位图：sm019fcce5e8de79d68b4a71c8d1a5ef（名称："银幕"），mutable=true（已正确）
--    - 1 条使用座位图：sm019fcd42ab8770ccbc15e0a8015b08（名称："标准1号厅"），mutable=false
--      该座位图被 1 条正常场次（on_sale）使用，不应修改 mutable 标记

-- 2. seat_status 表情况：
--    - cancelled 场次：0 条座位状态记录（无需清理）
--    - on_sale 场次：74 条座位状态记录（正常）

-- =============================================================================
-- 验证查询（已执行）
-- =============================================================================

-- 查询 cancelled 场次及其座位图信息
-- SELECT ss.show_id, ss.seat_map_id, sm.name as seat_map_name, sm.mutable
-- FROM show_schedule ss
-- JOIN seat_map sm ON ss.seat_map_id = sm.seat_map_id
-- WHERE ss.status = 'cancelled'
-- ORDER BY ss.show_id;

-- 结果：
--              show_id              |           seat_map_id            | seat_map_name | mutable
-- ----------------------------------+----------------------------------+---------------+---------
--  s019fcce5ead678b9a853b2e0a349d8e | sm019fcce5e8de79d68b4a71c8d1a5ef | 银幕          | t
--  s019fcce5ebf476f198ad7229dfc9949 | sm019fcce5e8de79d68b4a71c8d1a5ef | 银幕          | t
--  s019fcfc6914c78bc8e0944d45c0d2a1 | sm019fcce5e8de79d68b4a71c8d1a5ef | 银幕          | t
--  s019fcfccd5d877f19a5d6d8ebfe463e | sm019fcce5e8de79d68b4a71c8d1a5ef | 银幕          | t
--  s019fcfd62b06787599ec1b99a778981 | sm019fcd42ab8770ccbc15e0a8015b08 | 标准1号厅     | f
-- (5 行记录)

-- 查看座位状态统计
-- SELECT sh.status,
--        COUNT(DISTINCT ss.show_id) as shows_with_seats,
--        COUNT(ss.seat_id) as total_seat_status_records
-- FROM show_schedule sh
-- LEFT JOIN seat_status ss ON sh.show_id = ss.show_id
-- GROUP BY sh.status
-- ORDER BY sh.status;

-- 结果：
--   status   | shows_with_seats | total_seat_status_records
-- -----------+------------------+---------------------------
--  cancelled |                0 |                         0
--  on_sale   |                2 |                        74
-- (2 行记录)