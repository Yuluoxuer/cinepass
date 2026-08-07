-- 订单状态扩展：核销(已出票→已核销) 与 超时(待支付→已过期) 新增两个状态
-- 旧约束仅允许 pending_pay/issued/cancelled；先 DROP 再以五值重建（幂等，可重复执行）
ALTER TABLE order_ticket DROP CONSTRAINT IF EXISTS chk_order_status;
ALTER TABLE order_ticket ADD CONSTRAINT chk_order_status
    CHECK (status IN ('pending_pay', 'issued', 'cancelled', 'redeemed', 'expired'));
