-- 适用范围：已存在基础影院表的 PostgreSQL 开发库。
-- 本脚本仅补充影院模块新增列与索引，不删除或修改既有业务数据。

ALTER TABLE cinema
    ADD COLUMN IF NOT EXISTS traffic_note VARCHAR(256);

ALTER TABLE cinema
    ADD COLUMN IF NOT EXISTS city_name VARCHAR(64) NOT NULL DEFAULT U&'\4E0A\6D77\5E02';

ALTER TABLE cinema
    ADD COLUMN IF NOT EXISTS is_delete BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE cinema
    ADD COLUMN IF NOT EXISTS tags_json JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE seat_map
    ADD COLUMN IF NOT EXISTS mutable BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_cinema_city ON cinema (city_id);
CREATE INDEX IF NOT EXISTS idx_seat_map_cinema ON seat_map (cinema_id);
CREATE INDEX IF NOT EXISTS idx_hall_cinema ON hall (cinema_id);
CREATE INDEX IF NOT EXISTS idx_hall_seat_map ON hall (seat_map_id);
CREATE INDEX IF NOT EXISTS idx_seat_map ON seat (seat_map_id);
CREATE INDEX IF NOT EXISTS idx_seat_couple ON seat (couple_pair_id);
