-- 同影厅排片互斥：业务层有 overlap 检查，本约束兜底并发双写。
-- 区间为 [start_time, end_time + 20min)，与应用层 20 分钟清场缓冲一致。

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 已有冲突数据：保留较早创建的一场，其余标为 cancelled，否则无法加约束
DO $$
DECLARE
  n INT;
BEGIN
  LOOP
    WITH victims AS (
      SELECT s2.show_id
      FROM show_schedule s1
      JOIN show_schedule s2
        ON s1.hall_id = s2.hall_id
       AND s1.show_id <> s2.show_id
       AND s1.status IS DISTINCT FROM 'cancelled'
       AND s2.status IS DISTINCT FROM 'cancelled'
       AND s1.start_time < (s2.end_time + INTERVAL '20 minutes')
       AND (s1.end_time + INTERVAL '20 minutes') > s2.start_time
       AND (
             s1.created_at < s2.created_at
          OR (s1.created_at = s2.created_at AND s1.show_id < s2.show_id)
       )
    )
    UPDATE show_schedule
       SET status = 'cancelled',
           updated_at = NOW()
     WHERE show_id IN (SELECT show_id FROM victims);
    GET DIAGNOSTICS n = ROW_COUNT;
    EXIT WHEN n = 0;
  END LOOP;
END $$;

ALTER TABLE show_schedule DROP CONSTRAINT IF EXISTS show_hall_time_excl;

ALTER TABLE show_schedule
  ADD CONSTRAINT show_hall_time_excl
  EXCLUDE USING gist (
    hall_id WITH =,
    tstzrange(start_time, end_time + INTERVAL '20 minutes', '[)') WITH &&
  ) WHERE (status IS DISTINCT FROM 'cancelled');
