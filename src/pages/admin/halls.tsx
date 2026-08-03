import React, { useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Select, Table, message } from 'antd';
import { useParams } from 'umi';
import * as adminApi from '@/api/admin';
import type { HallVO, SeatMapVO } from '@/types';

const HallsPage: React.FC = () => {
  const { cinemaId } = useParams<{ cinemaId: string }>();
  const [halls, setHalls] = useState<HallVO[]>([]);
  const [maps, setMaps] = useState<SeatMapVO[]>([]);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = async () => {
    const [h, m] = await Promise.all([
      adminApi.listHalls(cinemaId),
      adminApi.listSeatMaps(),
    ]);
    setHalls(h.items);
    setMaps(m.items);
  };

  useEffect(() => {
    void load();
  }, [cinemaId]);

  return (
    <div>
      <Button type="primary" style={{ margin: '12px 0' }} onClick={() => setOpen(true)}>
        + 新建影厅
      </Button>
      <Table
        rowKey="hallId"
        dataSource={halls}
        columns={[
          { title: '厅名', dataIndex: 'name' },
          { title: '座位图 ID', dataIndex: 'seatMapId' },
          { title: '场次数', dataIndex: 'showCount' },
          {
            title: '操作',
            render: (_, r) => (
              <Button
                type="link"
                onClick={() => {
                  Modal.confirm({
                    title: '改名',
                    content: (
                      <Input
                        defaultValue={r.name}
                        id="hall-rename"
                      />
                    ),
                    onOk: async () => {
                      const el = document.getElementById('hall-rename') as HTMLInputElement;
                      await adminApi.updateHall(r.hallId, { name: el?.value || r.name });
                      message.success('已更新');
                      void load();
                    },
                  });
                }}
              >
                改名
              </Button>
            ),
          },
        ]}
      />
      <Modal
        title="新建影厅"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => form.submit()}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={async (v) => {
            await adminApi.createHall({ ...v, cinemaId });
            message.success('已创建');
            setOpen(false);
            form.resetFields();
            void load();
          }}
        >
          <Form.Item name="name" label="厅名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="seatMapId" label="座位图" rules={[{ required: true }]}>
            <Select
              options={maps.map((m) => ({
                value: m.seatMapId,
                label: `${m.seatMapId} (${m.rows}×${m.cols})`,
              }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default HallsPage;
