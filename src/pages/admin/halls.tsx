import React, { useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Space, Table, message } from 'antd';
import { history, useLocation, useParams } from 'umi';
import * as adminApi from '@/api/admin';
import type { HallVO } from '@/types';

const HallsPage: React.FC = () => {
  const { cinemaId } = useParams<{ cinemaId: string }>();
  const location = useLocation();
  const [halls, setHalls] = useState<HallVO[]>([]);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = () => void adminApi.listHalls({ cinemaId, page: 1, size: 50 })
    .then((r) => setHalls(r.items))
    .catch(() => setHalls([]));

  useEffect(() => {
    load();
  }, [cinemaId]);

  useEffect(() => {
    const seatMapId = new URLSearchParams(location.search).get('seatMapId');
    if (seatMapId) {
      form.setFieldValue('seatMapId', seatMapId);
      setOpen(true);
    }
  }, [location.search, form]);

  return (
    <div>
      <Space style={{ margin: '12px 0' }}>
        <Button type="primary" onClick={() => setOpen(true)}>+ 新建影厅</Button>
        <Button onClick={() => history.push(`/admin/seat-maps/new?cinemaId=${encodeURIComponent(cinemaId)}`)}>
          新建座位图
        </Button>
      </Space>
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
              <Button type="link" onClick={() => {
                let nextName = r.name;
                Modal.confirm({
                  title: '修改影厅名称',
                  content: <Input defaultValue={r.name} onChange={(event) => { nextName = event.target.value; }} />,
                  onOk: async () => {
                    try {
                      await adminApi.updateHall(r.hallId, { name: nextName.trim() || r.name });
                      message.success('已更新');
                      load();
                    } catch {
                      // 请求层已处理。
                    }
                  },
                });
              }}>改名</Button>
            ),
          },
        ]}
      />
      <Modal title="新建影厅" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()}>
        <Form form={form} layout="vertical" onFinish={async (values) => {
          try {
            await adminApi.createHall({ ...values, cinemaId });
            message.success('已创建');
            setOpen(false);
            form.resetFields();
            load();
          } catch {
            // 请求层已处理。
          }
        }}>
          <Form.Item name="name" label="厅名" rules={[{ required: true, whitespace: true, message: '请输入影厅名称' }]}><Input /></Form.Item>
          <Form.Item name="seatMapId" label="座位图 ID" rules={[{ required: true, whitespace: true, message: '请输入已创建的座位图 ID' }]}>
            <Input placeholder="例如 sm_sh_xuhui_01" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default HallsPage;
