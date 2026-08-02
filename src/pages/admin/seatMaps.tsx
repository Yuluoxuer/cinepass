import React, { useEffect, useState } from 'react';
import { Button, Modal, Space, Table, message } from 'antd';
import { history } from 'umi';
import * as adminApi from '@/api/admin';
import type { SeatMapVO } from '@/types';

const SeatMapsPage: React.FC = () => {
  const [data, setData] = useState<SeatMapVO[]>([]);

  const load = () => void adminApi.listSeatMaps().then((r) => setData(r.items));

  useEffect(() => {
    load();
  }, []);

  const requestDelete = async (seatMapId: string) => {
    const usage = await adminApi.getSeatMapUsage(seatMapId);
    if (usage.hallCount || usage.showCount) {
      message.warning(
        `该座位图正被 ${usage.hallCount} 个影厅、${usage.showCount} 个场次引用，不能删除。`,
      );
      return;
    }
    Modal.confirm({
      title: '确认删除座位图？',
      content: '删除后不可恢复。',
      okButtonProps: { danger: true },
      onOk: async () => {
        await adminApi.deleteSeatMap(seatMapId);
        message.success('已删除');
        load();
      },
    });
  };

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
                <Button type="link" danger onClick={() => void requestDelete(r.seatMapId)}>
                  删除
                </Button>
              </Space>
            ),
          },
        ]}
      />
    </div>
  );
};

export default SeatMapsPage;
