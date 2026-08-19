-- =============================================================================
-- 妙语购票票务中台 — 表结构 DDL（PostgreSQL 16）
-- 来源：docs/01-后端系分-票务中台与Agent.md §6
-- 前置：数据库 miaoyu 已存在（CREATE DATABASE miaoyu;）
-- 一键脚本：docs/sql/init-miaoyu.sql
-- =============================================================================

-- 1. 账号与鉴权
CREATE TABLE IF NOT EXISTS user_account (
  user_id        VARCHAR(40)    NOT NULL,
  nickname       VARCHAR(64)    NOT NULL,
  phone          VARCHAR(20)    NULL,
  password_hash  VARCHAR(128)   NOT NULL,
  role           VARCHAR(16)    NOT NULL,
  cinema_id      VARCHAR(32)    NULL,
  avatar_url     VARCHAR(512)   NULL,
  cinema_id      VARCHAR(32)    NULL,
  status         SMALLINT       NOT NULL DEFAULT 1,
  created_at     TIMESTAMPTZ(3) NOT NULL,
  updated_at     TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT uk_user_nickname UNIQUE (nickname),
  CONSTRAINT uk_user_phone UNIQUE (phone),
  CONSTRAINT chk_user_role CHECK (role IN ('user', 'staff', 'admin')),
  CONSTRAINT chk_staff_cinema CHECK (
    (role = 'staff' AND cinema_id IS NOT NULL)
    OR (role IN ('user', 'admin') AND cinema_id IS NULL)
  )
);
CREATE INDEX IF NOT EXISTS idx_user_cinema ON user_account (cinema_id);

-- Refresh 会话真相表（Redis auth:refresh:{sid} 为缓存）
CREATE TABLE IF NOT EXISTS auth_refresh_session (
  sid          VARCHAR(64)    NOT NULL,
  user_id      VARCHAR(40)    NOT NULL,
  role         VARCHAR(16)    NOT NULL,
  refresh_jti  VARCHAR(64)    NOT NULL,
  expire_at    TIMESTAMPTZ(3) NOT NULL,
  created_at   TIMESTAMPTZ(3) NOT NULL,
  updated_at   TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (sid)
);
CREATE INDEX IF NOT EXISTS idx_auth_refresh_user ON auth_refresh_session (user_id);

CREATE TABLE IF NOT EXISTS user_profile (
  user_id             VARCHAR(40)    NOT NULL,
  prefer_genres_json  JSONB          NOT NULL,
  prefer_row          VARCHAR(16)    NULL,
  prefer_side         VARCHAR(16)    NULL,
  updated_at          TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (user_id)
);

-- 1.5 影片类型标签字典：name 唯一；建片/改片时事务内同步填充，供类型列表接口
CREATE TABLE IF NOT EXISTS tag (
  tag_id       VARCHAR(32)    NOT NULL,
  name         VARCHAR(32)    NOT NULL,
  created_at   TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (tag_id),
  CONSTRAINT uk_tag_name UNIQUE (name)
);

-- 2. 目录
CREATE TABLE IF NOT EXISTS movie (
  movie_id        VARCHAR(32)    NOT NULL,
  title           VARCHAR(128)   NOT NULL,
  poster_url      VARCHAR(512)   NOT NULL,
  genres_json     JSONB          NOT NULL,
  rating          DECIMAL(3,1)   NULL,
  duration_min    INT            NOT NULL,
  release_date    DATE           NOT NULL,
  status          VARCHAR(32)    NOT NULL,
  description     TEXT           NOT NULL,
  cast_text       VARCHAR(512)   NULL,
  want_see_count  INT            NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ(3) NOT NULL,
  updated_at      TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (movie_id),
  CONSTRAINT chk_movie_status CHECK (status IN ('hot_showing', 'coming_soon', 'off'))
);

CREATE INDEX IF NOT EXISTS idx_movie_status ON movie (status);
CREATE INDEX IF NOT EXISTS idx_movie_title ON movie (title);
-- 到上映日自动上架扫描：status 等值 + release_date 范围（MovieStatusScheduler）
CREATE INDEX IF NOT EXISTS idx_movie_status_release ON movie (status, release_date);

CREATE TABLE IF NOT EXISTS cinema (
  cinema_id   VARCHAR(32)    NOT NULL,
  city_id     VARCHAR(32)    NOT NULL,
  city_name   VARCHAR(64)    NOT NULL DEFAULT U&'\4E0A\6D77\5E02',
  name        VARCHAR(128)   NOT NULL,
  address     VARCHAR(256)   NOT NULL,
  lat         DECIMAL(10,6)  NOT NULL,
  lng         DECIMAL(10,6)  NOT NULL,
  is_delete   BOOLEAN        NOT NULL DEFAULT FALSE,
  traffic_note VARCHAR(256)   NULL,
  tags_json    JSONB           NOT NULL DEFAULT '[]'::jsonb,
  created_at  TIMESTAMPTZ(3) NOT NULL,
  updated_at  TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (cinema_id)
);

CREATE INDEX IF NOT EXISTS idx_cinema_city ON cinema (city_id);

CREATE TABLE IF NOT EXISTS seat_map (
  seat_map_id   VARCHAR(32) NOT NULL,
  name          VARCHAR(64) NOT NULL,
  cinema_id     VARCHAR(32) NOT NULL,
  rows_n        INT         NOT NULL,
  cols_n        INT         NOT NULL,
  screen_label  VARCHAR(32) NOT NULL DEFAULT '银幕',
  mutable       BOOLEAN     NOT NULL DEFAULT TRUE,
  PRIMARY KEY (seat_map_id)
);
CREATE INDEX IF NOT EXISTS idx_seat_map_cinema ON seat_map (cinema_id);

CREATE INDEX IF NOT EXISTS idx_seat_map_cinema ON seat_map (cinema_id);

CREATE TABLE IF NOT EXISTS hall (
  hall_id      VARCHAR(32) NOT NULL,
  cinema_id    VARCHAR(32) NOT NULL,
  name         VARCHAR(64) NOT NULL,
  seat_map_id  VARCHAR(32) NOT NULL,
  PRIMARY KEY (hall_id)
);

CREATE INDEX IF NOT EXISTS idx_hall_cinema ON hall (cinema_id);
CREATE INDEX IF NOT EXISTS idx_hall_seat_map ON hall (seat_map_id);

CREATE TABLE IF NOT EXISTS seat (
  seat_id          VARCHAR(64) NOT NULL,
  seat_map_id      VARCHAR(32) NOT NULL,
  graph_row        INT         NOT NULL,
  graph_col        INT         NOT NULL,
  row_no           INT         NOT NULL,
  col_no           INT         NOT NULL,
  seat_name        VARCHAR(32) NOT NULL,
  seat_type        VARCHAR(16) NOT NULL,
  zone             VARCHAR(16) NOT NULL,
  couple_pair_id   VARCHAR(32) NULL,
  default_status   VARCHAR(16) NOT NULL DEFAULT 'available',
  PRIMARY KEY (seat_id),
  CONSTRAINT uk_map_graph UNIQUE (seat_map_id, graph_row, graph_col),
  CONSTRAINT uk_map_biz UNIQUE (seat_map_id, row_no, col_no),
  CONSTRAINT uk_map_seat_name UNIQUE (seat_map_id, seat_name),
  CONSTRAINT chk_seat_type CHECK (seat_type IN ('normal', 'couple', 'disabled')),
  CONSTRAINT chk_seat_default CHECK (default_status IN ('available', 'unavailable'))
);

CREATE INDEX IF NOT EXISTS idx_seat_map ON seat (seat_map_id);
CREATE INDEX IF NOT EXISTS idx_seat_couple ON seat (couple_pair_id);

-- 3. 排片
CREATE TABLE IF NOT EXISTS show_zone_price (
  show_id  VARCHAR(32)   NOT NULL,
  zone     VARCHAR(16)   NOT NULL,
  price    DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (show_id, zone),
  CONSTRAINT chk_show_zone_price CHECK (price > 0)
);

CREATE TABLE IF NOT EXISTS show_schedule (
  show_id      VARCHAR(32)    NOT NULL,
  movie_id     VARCHAR(32)    NOT NULL,
  cinema_id    VARCHAR(32)    NOT NULL,
  hall_id      VARCHAR(32)    NOT NULL,
  seat_map_id  VARCHAR(32)    NOT NULL,
  start_time   TIMESTAMPTZ(3) NOT NULL,
  end_time     TIMESTAMPTZ(3) NOT NULL,
  price        DECIMAL(10,2)  NOT NULL,
  status       VARCHAR(16)    NOT NULL DEFAULT 'on_sale',
  created_at   TIMESTAMPTZ(3) NOT NULL,
  updated_at   TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (show_id),
  CONSTRAINT chk_show_status CHECK (status IN ('on_sale', 'cancelled'))
);

CREATE INDEX IF NOT EXISTS idx_show_cinema_movie_time ON show_schedule (cinema_id, movie_id, start_time);
CREATE INDEX IF NOT EXISTS idx_show_movie_time ON show_schedule (movie_id, start_time);

-- 同厅排片互斥（需 btree_gist；与 Flyway V5 对齐；新建库可执行）
-- CREATE EXTENSION IF NOT EXISTS btree_gist;
-- ALTER TABLE show_schedule ADD CONSTRAINT show_hall_time_excl
--   EXCLUDE USING gist (
--     hall_id WITH =,
--     tstzrange(start_time, end_time + INTERVAL '20 minutes', '[)') WITH &&
--   ) WHERE (status IS DISTINCT FROM 'cancelled');


-- 4. 库存
CREATE TABLE IF NOT EXISTS seat_status (
  show_id     VARCHAR(32)    NOT NULL,
  seat_id     VARCHAR(64)    NOT NULL,
  status      VARCHAR(16)    NOT NULL,
  lock_id     VARCHAR(32)    NULL,
  user_id     VARCHAR(40)    NULL,
  expire_at   TIMESTAMPTZ(3) NULL,
  updated_at  TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (show_id, seat_id),
  CONSTRAINT chk_seat_status CHECK (status IN ('available', 'locked', 'sold', 'unavailable'))
);

CREATE INDEX IF NOT EXISTS idx_seat_status_lock ON seat_status (lock_id);
CREATE INDEX IF NOT EXISTS idx_seat_status_expire ON seat_status (status, expire_at);
CREATE INDEX IF NOT EXISTS idx_seat_status_user ON seat_status (user_id, status);

CREATE TABLE IF NOT EXISTS seat_lock (
  lock_id         VARCHAR(32)    NOT NULL,
  show_id         VARCHAR(32)    NOT NULL,
  user_id         VARCHAR(40)    NOT NULL,
  seat_ids_json   JSONB          NOT NULL,
  status          VARCHAR(16)    NOT NULL,
  ttl_seconds     INT            NOT NULL,
  expire_at       TIMESTAMPTZ(3) NOT NULL,
  session_id      VARCHAR(64)    NULL,
  created_at      TIMESTAMPTZ(3) NOT NULL,
  updated_at      TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (lock_id),
  CONSTRAINT chk_lock_status CHECK (status IN ('active', 'expired', 'consumed', 'released'))
);

CREATE INDEX IF NOT EXISTS idx_lock_show ON seat_lock (show_id);
CREATE INDEX IF NOT EXISTS idx_lock_user ON seat_lock (user_id);
CREATE INDEX IF NOT EXISTS idx_lock_expire ON seat_lock (status, expire_at);

-- 5. 订单
CREATE TABLE IF NOT EXISTS order_ticket (
  order_id              VARCHAR(40)    NOT NULL,
  user_id               VARCHAR(40)    NOT NULL,
  show_id               VARCHAR(32)    NOT NULL,
  lock_id               VARCHAR(32)    NOT NULL,
  movie_title           VARCHAR(128)   NOT NULL,
  cinema_name           VARCHAR(128)   NOT NULL,
  hall_name             VARCHAR(64)    NOT NULL,
  start_time            TIMESTAMPTZ(3) NOT NULL,
  seat_ids_json         JSONB          NOT NULL,
  unit_price            DECIMAL(10,2)  NOT NULL,
  amount                DECIMAL(10,2)  NOT NULL,
  seat_price_snapshot   JSONB          NOT NULL,
  status                VARCHAR(16)    NOT NULL,
  ticket_code           VARCHAR(64)    NULL,
  qr_payload            VARCHAR(256)   NULL,
  pay_channel           VARCHAR(16)    NULL,
  expire_at             TIMESTAMPTZ(3) NULL,
  pay_at                TIMESTAMPTZ(3) NULL,
  cancel_reason         VARCHAR(64)    NULL,
  session_id            VARCHAR(64)    NULL,
  created_at            TIMESTAMPTZ(3) NOT NULL,
  updated_at            TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (order_id),
  CONSTRAINT uk_order_lock UNIQUE (lock_id),
  CONSTRAINT chk_order_status CHECK (status IN ('pending_pay', 'issued', 'cancelled')),
  CONSTRAINT chk_pay_channel CHECK (
    pay_channel IS NULL OR pay_channel IN ('desktop_button', 'mobile_qr')
  )
);

CREATE INDEX IF NOT EXISTS idx_order_user_status ON order_ticket (user_id, status, created_at);
CREATE INDEX IF NOT EXISTS idx_order_expire ON order_ticket (status, expire_at);

-- 6. 推荐 / 想看
CREATE TABLE IF NOT EXISTS want_see (
  user_id     VARCHAR(40)    NOT NULL,
  movie_id    VARCHAR(32)    NOT NULL,
  created_at  TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (user_id, movie_id)
);

CREATE INDEX IF NOT EXISTS idx_want_movie ON want_see (movie_id);

CREATE TABLE IF NOT EXISTS reco_stats (
  movie_id      VARCHAR(32)     NOT NULL,
  week_orders   INT             NOT NULL DEFAULT 0,
  week_clicks   INT             NOT NULL DEFAULT 0,
  rating_norm   DECIMAL(6,4)    NOT NULL DEFAULT 0,
  freshness     DECIMAL(6,4)    NOT NULL DEFAULT 0,
  hot_score     DECIMAL(12,4)   NOT NULL DEFAULT 0,
  computed_at   TIMESTAMPTZ(3)  NOT NULL,
  PRIMARY KEY (movie_id)
);

CREATE INDEX IF NOT EXISTS idx_reco_hot ON reco_stats (hot_score DESC);

CREATE TABLE IF NOT EXISTS reco_weight (
  city_id    VARCHAR(32)    NOT NULL,
  w_orders   DECIMAL(6,4)   NOT NULL DEFAULT 0.4500,
  w_clicks   DECIMAL(6,4)   NOT NULL DEFAULT 0.2500,
  w_rating   DECIMAL(6,4)   NOT NULL DEFAULT 0.1500,
  w_fresh    DECIMAL(6,4)   NOT NULL DEFAULT 0.1500,
  updated_at TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (city_id)
);

-- 影片点击日计数：影片详情页每访问一次 UPSERT 当日 cnt+1（系分 reco_stats.week_clicks 数据源）
CREATE TABLE IF NOT EXISTS reco_clicks (
  movie_id    VARCHAR(32) NOT NULL,
  click_date  DATE        NOT NULL,
  cnt         INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (movie_id, click_date)
);

-- 7. BookingDraft（中台 /booking-drafts → agent_session.draft_json）
CREATE TABLE IF NOT EXISTS agent_session (
  session_id   VARCHAR(64)    NOT NULL,
  user_id      VARCHAR(40)    NULL,
  source       VARCHAR(16)    NOT NULL,
  state        VARCHAR(32)    NOT NULL,
  draft_json   JSONB          NOT NULL,
  version      BIGINT         NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ(3) NOT NULL,
  updated_at   TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (session_id),
  CONSTRAINT chk_session_source CHECK (source IN ('manual', 'agent', 'hybrid'))
);

CREATE INDEX IF NOT EXISTS idx_session_user ON agent_session (user_id);
CREATE INDEX IF NOT EXISTS idx_session_updated ON agent_session (updated_at);
