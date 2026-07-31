import React, { useState } from 'react';
import { Button, Drawer, Form, Input, Select, Space, Table } from 'antd';
import * as adminApi from '@/api/admin';
import type { OrderVO } from '@/types';

const AdminOrdersPage: React.FC = () => {
  const [data, setData] = useState<OrderVO[]>([]);
  const [current, setCurrent] = useState<OrderVO | null>(null);
  const [form] = Form.useForm();

  const search = async (v?: Record<string, string>) => {
    const values = v || form.getFieldsValue();
    const res = await adminApi.adminListOrders({
      orderId: values.orderId,
      userId: values.userId,
      status: values.status,
      page: 1,
      size: 50,
    });
    setData(res.items);
  };

  return (
    <div>
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
        dataSource={data}
        onRow={(r) => ({ onClick: () => setCurrent(r) })}
        columns={[
          { title: '订单号', dataIndex: 'orderId' },
          { title: '影片', dataIndex: 'movieTitle' },
          { title: '用户', dataIndex: 'userId' },
          { title: '金额', dataIndex: 'amount' },
          { title: '状态', dataIndex: 'status' },
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
            <div>{current.startTime}</div>
            <div>座位：{current.seatIds.join('、')}</div>
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
