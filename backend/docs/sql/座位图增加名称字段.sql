-- 适用范围：PostgreSQL 开发/联调库（miaoyu）。
-- 为 seat_map 增加运营侧展示名称 name；已有行用银幕文案回填，空则「未命名座位图」。

ALTER TABLE seat_map
    ADD COLUMN IF NOT EXISTS name VARCHAR(64);

UPDATE seat_map
SET name = COALESCE(NULLIF(TRIM(screen_label), ''), U&'\672A\547D\540D\5EA7\4F4D\56FE')
WHERE name IS NULL OR TRIM(name) = '';

ALTER TABLE seat_map
    ALTER COLUMN name SET NOT NULL;

COMMENT ON COLUMN seat_map.name IS '座位图名称（运营展示用）';
