import React, { useEffect, useState } from 'react';
import { Button, Drawer, Form, Input, Select, Space, Table, Tag, Empty } from 'antd';
import * as adminApi from '@/api/admin';
import type { OrderVO } from '@/types';
import { formatOrderSeatLabels, formatDateTime } from '@/utils/format';
import { getCinemaIdFromAccessToken, useAuthStore } from '@/stores/auth';

const AdminOrdersPage: React.FC = () => {
  const [data, setData] = useState<OrderVO[]>([]);
  const [current, setCurrent] = useState<OrderVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [form] = Form.useForm();
  const user = useAuthStore((s) => s.user);
  const staffCinemaId = user?.role === 'staff' ? user.cinemaId || getCinemaIdFromAccessToken() : undefined;

  const search = async (v?: Record<string, string>) => {
    const values = v || form.getFieldsValue();
    setLoading(true);
    try {
      const res = await adminApi.adminListOrders({
        orderId: values.orderId || undefined,
        userId: values.userId || undefined,
        status: values.status || undefined,
        page: 1,
        size: 50,
      });
      setData(res.items);
    } catch {
      setData([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void search();
    // 进入页面即拉本影院（staff）/全量（admin）订单
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const statusLabel: Record<OrderVO['status'], string> = {
    pending_pay: '待支付',
    issued: '已出票',
    redeemed: '已核销',
    cancelled: '已取消',
    expired: '已过期',
  };

  return (
    <div>
      {staffCinemaId ? (
        <div style={{ marginBottom: 12, color: '#666' }}>
          当前员工影院：{staffCinemaId}（仅展示本院订单）
        </div>
      ) : null}
      <Form form={form} layout="inline" onFinish={search} style={{ marginBottom: 16 }}>
        <Form.Item name="orderId">
          <Input placeholder="订单号" allowClear />
        </Form.Item>
        <Form.Item name="userId">
          <Input placeholder="用户 ID" allowClear />
        </Form.Item>
        <Form.Item name="status">
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 140 }}
            options={[
              { value: 'pending_pay', label: '待支付' },
              { value: 'issued', label: '已出票' },
              { value: 'redeemed', label: '已核销' },
              { value: 'cancelled', label: '已取消' },
            ]}
          />
        </Form.Item>
        <Button type="primary" htmlType="submit">
          查询
        </Button>
      </Form>
      <Table
        rowKey="orderId"
        loading={loading}
        dataSource={data}
        locale={{ emptyText: loading ? '加载中…' : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有数据" /> }}
        onRow={(r) => ({ onClick: () => setCurrent(r) })}
        columns={[
          { title: '订单号', dataIndex: 'orderId' },
          { title: '影片', dataIndex: 'movieTitle' },
          { title: '影院', dataIndex: 'cinemaName' },
          { title: '用户', dataIndex: 'nickname', render: (v: string, r: OrderVO) => v || r.userId },
          { title: '金额', dataIndex: 'amount' },
          {
            title: '状态',
            dataIndex: 'status',
            render: (status: OrderVO['status'], record: OrderVO) => (
              <Space>
                <Tag color={status === 'issued' ? 'green' : status === 'redeemed' ? 'blue' : status === 'pending_pay' ? 'orange' : 'default'}>
                  {statusLabel[status]}
                </Tag>
                {record.cancelReason === 'show_cancelled' ? <span style={{ color: '#cf1322' }}>场次取消</span> : null}
              </Space>
            ),
          },
          { title: '取票码', dataIndex: 'ticketCode' },
        ]}
      />
      <Drawer open={!!current} onClose={() => setCurrent(null)} title="订单详情" width={420}>
        {current ? (
          <Space direction="vertical">
            <div>订单：{current.orderId}</div>
            <div>影片：{current.movieTitle}</div>
            <div>
              {current.cinemaName} · {current.hallName}
            </div>
            <div>{formatDateTime(current.startTime)}</div>
            <div>座位：{formatOrderSeatLabels(current)}</div>
            <div>金额：¥{current.amount}</div>
            <div>状态：{current.status}</div>
            <div>取票码：{current.ticketCode || '—'}</div>
            <div style={{ wordBreak: 'break-all' }}>QR：{current.qrPayload || '—'}</div>
          </Space>
        ) : null}
      </Drawer>
    </div>
  );
};

export default AdminOrdersPage;
