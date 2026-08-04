import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic } from 'antd';
import * as adminApi from '@/api/admin';

function getToday() {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

const DashboardPage: React.FC = () => {
  const [stats, setStats] = useState({ total: 0, pending: 0, issued: 0, shows: 0 });
  const today = getToday();

  useEffect(() => {
    (async () => {
      try {
        const dashboardStats = await adminApi.getDashboardStats(today);
        setStats({
          total: dashboardStats.totalOrderCount,
          pending: dashboardStats.pendingPayOrderCount,
          issued: dashboardStats.issuedOrderCount,
          shows: dashboardStats.onSaleShowCount,
        });
      } catch {
        // 请求层已处理，保留零值统计。
      }
    })();
  }, [today]);

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
