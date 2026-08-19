-- Refresh 会话真相表：Redis auth:refresh:{sid} 为缓存，本表为兜底
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
