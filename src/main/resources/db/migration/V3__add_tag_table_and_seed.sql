-- 影片类型标签字典：name 唯一；建片/改片时事务内同步填充，供类型列表接口
CREATE TABLE IF NOT EXISTS tag (
  tag_id       VARCHAR(32)    NOT NULL,
  name         VARCHAR(32)    NOT NULL,
  created_at   TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (tag_id),
  CONSTRAINT uk_tag_name UNIQUE (name)
);

-- 存量影片已有类型回填（幂等，可重复执行）
INSERT INTO tag(tag_id, name, created_at)
SELECT 't' || substr(md5(random()::text || g), 1, 31), trim(g), now()
FROM movie, jsonb_array_elements_text(genres_json) AS g
WHERE trim(g) <> ''
ON CONFLICT (name) DO NOTHING;
