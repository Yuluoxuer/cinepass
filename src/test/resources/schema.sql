-- H2 test schema for auth / profile / want-see integration tests
CREATE TABLE IF NOT EXISTS user_account (
  user_id        VARCHAR(40)    NOT NULL,
  nickname       VARCHAR(64)    NOT NULL,
  phone          VARCHAR(20)    NULL,
  password_hash  VARCHAR(128)   NOT NULL,
  role           VARCHAR(16)    NOT NULL,
  avatar_url     VARCHAR(512)   NULL,
  cinema_id      VARCHAR(32)    NULL,
  status         SMALLINT       NOT NULL DEFAULT 1,
  created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT uk_user_nickname UNIQUE (nickname),
  CONSTRAINT uk_user_phone UNIQUE (phone)
);

CREATE TABLE IF NOT EXISTS auth_refresh_session (
  sid          VARCHAR(64)    NOT NULL,
  user_id      VARCHAR(40)    NOT NULL,
  role         VARCHAR(16)    NOT NULL,
  refresh_jti  VARCHAR(64)    NOT NULL,
  expire_at    TIMESTAMP WITH TIME ZONE NOT NULL,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (sid)
);

CREATE TABLE IF NOT EXISTS user_profile (
  user_id             VARCHAR(40)    NOT NULL,
  prefer_genres_json  VARCHAR(2048)  NOT NULL,
  prefer_row          VARCHAR(16)    NULL,
  prefer_side         VARCHAR(16)    NULL,
  updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (user_id)
);

CREATE TABLE IF NOT EXISTS tag (
  tag_id       VARCHAR(32)    NOT NULL,
  name         VARCHAR(32)    NOT NULL,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (tag_id),
  CONSTRAINT uk_tag_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS movie (
  movie_id        VARCHAR(32)    NOT NULL,
  title           VARCHAR(128)   NOT NULL,
  poster_url      VARCHAR(512)   NOT NULL,
  genres_json     VARCHAR(2048)  NOT NULL,
  rating          DECIMAL(3,1)   NULL,
  duration_min    INT            NOT NULL,
  release_date    DATE           NOT NULL,
  status          VARCHAR(32)    NOT NULL,
  description     CLOB           NOT NULL,
  cast_text       VARCHAR(512)   NULL,
  want_see_count  INT            NOT NULL DEFAULT 0,
  created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (movie_id)
);

CREATE TABLE IF NOT EXISTS want_see (
  user_id     VARCHAR(40) NOT NULL,
  movie_id    VARCHAR(32) NOT NULL,
  created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (user_id, movie_id)
);

CREATE TABLE IF NOT EXISTS cinema (
  cinema_id    VARCHAR(32) NOT NULL,
  city_id      VARCHAR(32) NOT NULL,
  city_name    VARCHAR(64) NOT NULL DEFAULT '上海市',
  name         VARCHAR(128) NOT NULL,
  address      VARCHAR(256) NOT NULL,
  lat          DECIMAL(10,6) NOT NULL,
  lng          DECIMAL(10,6) NOT NULL,
  is_delete    BOOLEAN NOT NULL DEFAULT FALSE,
  traffic_note VARCHAR(256) NULL,
  tags_json    VARCHAR(2048) NOT NULL DEFAULT '[]',
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (cinema_id)
);

CREATE INDEX IF NOT EXISTS idx_cinema_city ON cinema (city_id);

CREATE TABLE IF NOT EXISTS seat_map (
  seat_map_id VARCHAR(32) NOT NULL,
  name        VARCHAR(64) NOT NULL,
  cinema_id   VARCHAR(32) NOT NULL,
  rows_n      INT NOT NULL,
  cols_n      INT NOT NULL,
  screen_label VARCHAR(32) NOT NULL DEFAULT '银幕',
  mutable     BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (seat_map_id)
);

CREATE INDEX IF NOT EXISTS idx_seat_map_cinema ON seat_map (cinema_id);

CREATE TABLE IF NOT EXISTS hall (
  hall_id     VARCHAR(32) NOT NULL,
  cinema_id   VARCHAR(32) NOT NULL,
  name        VARCHAR(64) NOT NULL,
  seat_map_id VARCHAR(32) NOT NULL,
  PRIMARY KEY (hall_id)
);

CREATE INDEX IF NOT EXISTS idx_hall_cinema ON hall (cinema_id);
CREATE INDEX IF NOT EXISTS idx_hall_seat_map ON hall (seat_map_id);

CREATE TABLE IF NOT EXISTS seat (
  seat_id        VARCHAR(64) NOT NULL,
  seat_map_id    VARCHAR(32) NOT NULL,
  graph_row      INT NOT NULL,
  graph_col      INT NOT NULL,
  row_no         INT NOT NULL,
  col_no         INT NOT NULL,
  seat_name      VARCHAR(32) NOT NULL,
  seat_type      VARCHAR(16) NOT NULL,
  zone           VARCHAR(16) NOT NULL,
  couple_pair_id VARCHAR(32) NULL,
  default_status VARCHAR(16) NOT NULL DEFAULT 'available',
  PRIMARY KEY (seat_id),
  CONSTRAINT uk_map_graph UNIQUE (seat_map_id, graph_row, graph_col),
  CONSTRAINT uk_map_biz UNIQUE (seat_map_id, row_no, col_no),
  CONSTRAINT uk_map_seat_name UNIQUE (seat_map_id, seat_name)
);

CREATE INDEX IF NOT EXISTS idx_seat_map ON seat (seat_map_id);

CREATE TABLE IF NOT EXISTS show_zone_price (
  show_id  VARCHAR(32)   NOT NULL,
  zone     VARCHAR(16)   NOT NULL,
  price    DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (show_id, zone),
  CONSTRAINT chk_show_zone_price CHECK (price > 0)
);

CREATE TABLE IF NOT EXISTS show_schedule (
  show_id      VARCHAR(32) NOT NULL,
  movie_id     VARCHAR(32) NOT NULL,
  cinema_id    VARCHAR(32) NOT NULL,
  hall_id      VARCHAR(32) NOT NULL,
  seat_map_id  VARCHAR(32) NOT NULL,
  start_time   TIMESTAMP WITH TIME ZONE NOT NULL,
  end_time     TIMESTAMP WITH TIME ZONE NOT NULL,
  price        DECIMAL(10,2) NOT NULL,
  status       VARCHAR(16) NOT NULL DEFAULT 'on_sale',
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (show_id)
);

CREATE INDEX IF NOT EXISTS idx_show_cinema_movie_time ON show_schedule (cinema_id, movie_id, start_time);

CREATE TABLE IF NOT EXISTS seat_status (
  show_id     VARCHAR(32)    NOT NULL,
  seat_id     VARCHAR(64)    NOT NULL,
  status      VARCHAR(16)    NOT NULL,
  lock_id     VARCHAR(32)    NULL,
  user_id     VARCHAR(40)    NULL,
  expire_at   TIMESTAMP WITH TIME ZONE NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (show_id, seat_id)
);

CREATE TABLE IF NOT EXISTS seat_lock (
  lock_id         VARCHAR(32)    NOT NULL,
  show_id         VARCHAR(32)    NOT NULL,
  user_id         VARCHAR(40)    NOT NULL,
  seat_ids_json   VARCHAR(2048)  NOT NULL,
  status          VARCHAR(16)    NOT NULL,
  ttl_seconds     INT            NOT NULL,
  expire_at       TIMESTAMP WITH TIME ZONE NOT NULL,
  session_id      VARCHAR(64)    NULL,
  created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (lock_id)
);

CREATE TABLE IF NOT EXISTS order_ticket (
  order_id              VARCHAR(40)    NOT NULL,
  user_id               VARCHAR(40)    NOT NULL,
  show_id               VARCHAR(32)    NOT NULL,
  lock_id               VARCHAR(32)    NOT NULL,
  movie_title           VARCHAR(128)   NOT NULL,
  cinema_name           VARCHAR(128)   NOT NULL,
  hall_name             VARCHAR(64)    NOT NULL,
  start_time            TIMESTAMP WITH TIME ZONE NOT NULL,
  seat_ids_json         VARCHAR(2048)  NOT NULL,
  unit_price            DECIMAL(10,2)  NOT NULL,
  amount                DECIMAL(10,2)  NOT NULL,
  seat_price_snapshot   VARCHAR(4096)  NOT NULL,
  status                VARCHAR(16)    NOT NULL,
  ticket_code           VARCHAR(64)    NULL,
  qr_payload            VARCHAR(256)   NULL,
  pay_channel           VARCHAR(16)    NULL,
  expire_at             TIMESTAMP WITH TIME ZONE NULL,
  pay_at                TIMESTAMP WITH TIME ZONE NULL,
  cancel_reason         VARCHAR(64)    NULL,
  session_id            VARCHAR(64)    NULL,
  created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (order_id),
  CONSTRAINT uk_order_lock UNIQUE (lock_id)
);

CREATE TABLE IF NOT EXISTS agent_session (
  session_id   VARCHAR(64)    NOT NULL,
  user_id      VARCHAR(40)    NULL,
  source       VARCHAR(16)    NOT NULL,
  state        VARCHAR(32)    NOT NULL,
  draft_json   VARCHAR(8192)  NOT NULL,
  version      BIGINT         NOT NULL DEFAULT 0,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (session_id)
);

CREATE TABLE IF NOT EXISTS reco_stats (
  movie_id      VARCHAR(32)     NOT NULL,
  week_orders   INT             NOT NULL DEFAULT 0,
  week_clicks   INT             NOT NULL DEFAULT 0,
  rating_norm   DECIMAL(6,4)    NOT NULL DEFAULT 0,
  freshness     DECIMAL(6,4)    NOT NULL DEFAULT 0,
  hot_score     DECIMAL(12,4)   NOT NULL DEFAULT 0,
  computed_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (movie_id)
);

CREATE TABLE IF NOT EXISTS reco_weight (
  city_id    VARCHAR(32)    NOT NULL,
  w_orders   DECIMAL(6,4)   NOT NULL DEFAULT 0.4500,
  w_clicks   DECIMAL(6,4)   NOT NULL DEFAULT 0.2500,
  w_rating   DECIMAL(6,4)   NOT NULL DEFAULT 0.1500,
  w_fresh    DECIMAL(6,4)   NOT NULL DEFAULT 0.1500,
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (city_id)
);

CREATE TABLE IF NOT EXISTS reco_clicks (
  movie_id    VARCHAR(32) NOT NULL,
  click_date  DATE        NOT NULL,
  cnt         INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (movie_id, click_date)
);
