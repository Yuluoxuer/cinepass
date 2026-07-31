import React, { useEffect, useState } from 'react';
import { Button, Popconfirm, Space, Table, message } from 'antd';
import { history } from 'umi';
import * as adminApi from '@/api/admin';
import type { SeatMapVO } from '@/types';

const SeatMapsPage: React.FC = () => {
  const [data, setData] = useState<SeatMapVO[]>([]);

  const load = () => void adminApi.listSeatMaps().then((r) => setData(r.items));

  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <Button type="primary" style={{ marginBottom: 16 }} onClick={() => history.push('/admin/seat-maps/new')}>
        + 新建
      </Button>
      <Table
        rowKey="seatMapId"
        dataSource={data}
        columns={[
          { title: 'ID', dataIndex: 'seatMapId' },
          { title: '行列', render: (_, r) => `${r.rows} × ${r.cols}` },
          { title: '座位数', render: (_, r) => r.seats.length },
          {
            title: 'mutable',
            dataIndex: 'mutable',
            render: (v: boolean) => (v === false ? '否' : '是'),
          },
          {
            title: '操作',
            render: (_, r) => (
              <Space>
                <Button type="link" onClick={() => history.push(`/admin/seat-maps/${r.seatMapId}`)}>
                  打开
                </Button>
                <Popconfirm
                  title="确认删除？"
                  onConfirm={async () => {
                    await adminApi.deleteSeatMap(r.seatMapId);
                    message.success('已删除');
                    load();
                  }}
                >
                  <Button type="link" danger>
                    删除
                  </Button>
                </Popconfirm>
              </Space>
            ),
          },
        ]}
      />
    </div>
  );
};

export default SeatMapsPage;
