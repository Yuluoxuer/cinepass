import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic } from 'antd';
import * as adminApi from '@/api/admin';
import * as catalogApi from '@/api/catalog';

const DashboardPage: React.FC = () => {
  const [stats, setStats] = useState({ total: 0, pending: 0, issued: 0, shows: 0 });

  useEffect(() => {
    (async () => {
      const [all, pending, issued, movies] = await Promise.all([
        adminApi.adminListOrders({ page: 1, size: 50 }),
        adminApi.adminListOrders({ status: 'pending_pay', page: 1, size: 50 }),
        adminApi.adminListOrders({ status: 'issued', page: 1, size: 50 }),
        catalogApi.listMovies({ status: 'hot_showing', page: 1, size: 1 }),
      ]);
      setStats({
        total: all.total,
        pending: pending.total,
        issued: issued.total,
        shows: movies.total,
      });
    })();
  }, []);

  return (
    <div>
      <h2>运营概览</h2>
      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={6}><Card><Statistic title="订单总数" value={stats.total} /></Card></Col>
        <Col span={6}><Card><Statistic title="待支付" value={stats.pending} /></Card></Col>
        <Col span={6}><Card><Statistic title="已出票" value={stats.issued} /></Card></Col>
        <Col span={6}><Card><Statistic title="热映影片" value={stats.shows} /></Card></Col>
      </Row>
    </div>
  );
};

export default DashboardPage;
