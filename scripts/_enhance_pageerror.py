"""Layer PageError onto upstream admin pages; extend getDashboardStats for silent."""
from pathlib import Path

Path("src/pages/admin/dashboard.tsx").write_text("""import React, { useCallback, useEffect, useState } from react;
import { Card, Col, Row, Spin, Statistic } from antd;
import * as adminApi from @/api/admin;
import PageError from @/components/PageError;

function getToday() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, 0);
  const day = String(now.getDate()).padStart(2, 0);
  return `${now.getFullYear()}-${month}-${day}`;
}

const DashboardPage: React.FC = () => {
  const [stats, setStats] = useState({ total: 0, pending: 0, issued: 0, shows: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const today = getToday();

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      // silent：由本页 PageError 呈现，避免与全局 toast 重复
      const dashboardStats = await adminApi.getDashboardStats(today, { silent: true });
      setStats({
        total: dashboardStats.totalOrderCount,
        pending: dashboardStats.pendingPayOrderCount,
        issued: dashboardStats.issuedOrderCount,
        shows: dashboardStats.onSaleShowCount,
      });
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, [today]);

  useEffect(() => {
    void load();
  }, [load]);

  if (error) {
    return <PageError title="运营概览加载失败" error={error} onRetry={() => void load()} />;
  }

  if (loading) {
    return (
      <div style={{ padding: 64, textAlign: center }}>
        <Spin tip="加载中…" />
      </div>
    );
  }

  return (
    <div>
      <h2>运营概览</h2>
      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={6}><Card><Statistic title="累计订单" value={stats.total} /></Card></Col>
        <Col span={6}><Card><Statistic title="累计待支付" value={stats.pending} /></Card></Col>
        <Col span={6}><Card><Statistic title="累计已出票" value={stats.issued} /></Card></Col>
        <Col span={6}><Card><Statistic title={`今日在售场次（${today}）`} value={stats.shows} /></Card></Col>
      </Row>
    </div>
  );
};

export default DashboardPage;
""")

admin = Path("src/api/admin.ts")
text = admin.read_text()
if "RequestOptions" not in text.split("from ./client")[0]:
    text = text.replace(
        "import { get, post, put, del } from ./client;",
        "import { get, post, put, del, type RequestOptions } from ./client;",
        1,
    )

old = """export function getDashboardStats(date: string) {
  return get<AdminDashboardStatsVO>(/admin/dashboard/stats, { date });
}"""
new = """export function getDashboardStats(date: string, opts?: RequestOptions) {
  return get<AdminDashboardStatsVO>(/admin/dashboard/stats, { date }, opts);
}"""
if old not in text:
    raise SystemExit("getDashboardStats block not found")
admin.write_text(text.replace(old, new, 1))
print("enhanced dashboard + admin getDashboardStats")
