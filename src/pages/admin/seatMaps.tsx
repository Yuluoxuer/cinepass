import React from 'react';
import { Alert, Button, Card, Space } from 'antd';
import { history } from 'umi';

const SeatMapsPage: React.FC = () => (
  <Card title="座位图">
    <Alert
      type="info"
      showIcon
      message="座位图创建后不可在运营端查询、编辑或删除"
      description="当前真实接口只提供创建座位图。请从具体影院的影厅管理页创建，以自动关联影院；创建完成后将座位图 ID 填入新建影厅表单。"
    />
    <Space style={{ marginTop: 16 }}>
      <Button type="primary" onClick={() => history.push('/admin/cinemas')}>前往影院管理</Button>
      <Button onClick={() => history.push('/admin/seat-maps/new')}>直接新建座位图</Button>
    </Space>
  </Card>
);

export default SeatMapsPage;
