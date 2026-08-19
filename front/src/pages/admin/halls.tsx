import React, { useEffect, useState } from 'react';
import { Button, Empty, Form, Input, Modal, Select, Space, Table, message } from 'antd';
import { history, useLocation, useParams } from 'umi';
import * as adminApi from '@/api/admin';
import type { HallVO, SeatMapVO } from '@/types';

const HallsPage: React.FC = () => {
  const { cinemaId } = useParams<{ cinemaId: string }>();
  const location = useLocation();
  const [halls, setHalls] = useState<HallVO[]>([]);
  const [seatMaps, setSeatMaps] = useState<SeatMapVO[]>([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingHall, setEditingHall] = useState<HallVO | null>(null);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();

  const seatMapDisplayName = (seatMap: SeatMapVO) => {
    const name = (seatMap.name || '').trim();
    if (name) return name;
    const label = (seatMap.screenLabel || '').trim();
    return label || '未命名座位图';
  };

  const seatMapOptions = seatMaps.map((m) => ({
    value: m.seatMapId,
    label: `${seatMapDisplayName(m)}（${m.rows}×${m.cols} · ${m.seatCount ?? m.seats?.length ?? 0} 座）`,
  }));

  const load = () => void adminApi.listHalls({ cinemaId, page: 1, size: 50 })
    .then((r) => setHalls(r.items))
    .catch(() => setHalls([]));

  const loadSeatMaps = () => void adminApi.listSeatMaps({ cinemaId, page: 1, size: 100 })
    .then((r) => setSeatMaps(r.items || []))
    .catch(() => setSeatMaps([]));

  useEffect(() => {
    load();
    loadSeatMaps();
  }, [cinemaId]);

  useEffect(() => {
    const seatMapId = new URLSearchParams(location.search).get('seatMapId');
    if (seatMapId) {
      createForm.setFieldValue('seatMapId', seatMapId);
      setCreateOpen(true);
    }
  }, [location.search, createForm]);

  const openCreateModal = () => {
    loadSeatMaps();
    setCreateOpen(true);
  };

  const openEditModal = (hall: HallVO) => {
    loadSeatMaps();
    setEditingHall(hall);
    editForm.setFieldsValue({ name: hall.name, seatMapId: hall.seatMapId });
    setEditOpen(true);
  };

  const seatMapNameById = (seatMapId: string) => {
    const found = seatMaps.find((m) => m.seatMapId === seatMapId);
    return found ? seatMapDisplayName(found) : seatMapId;
  };

  const seatMapSelectExtra = seatMaps.length === 0 ? (
    <Button
      type="link"
      style={{ padding: 0 }}
      onClick={() => history.push(`/admin/seat-maps/new?cinemaId=${encodeURIComponent(cinemaId ?? '')}`)}
    >
      当前影院暂无座位图，去新建
    </Button>
  ) : undefined;

  return (
    <div>
      <Space style={{ margin: '12px 0' }}>
        <Button type="primary" onClick={openCreateModal}>+ 新建影厅</Button>
        <Button onClick={() => history.push(`/admin/seat-maps/new?cinemaId=${encodeURIComponent(cinemaId ?? '')}`)}>
          新建座位图
        </Button>
      </Space>
      <Table
        rowKey="hallId"
        dataSource={halls}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有数据" /> }}
        columns={[
          { title: '厅名', dataIndex: 'name' },
          {
            title: '座位图',
            dataIndex: 'seatMapId',
            render: (seatMapId: string) => seatMapNameById(seatMapId),
          },
          { title: '场次数', dataIndex: 'showCount' },
          {
            title: '操作',
            render: (_, r) => (
              <Button type="link" onClick={() => openEditModal(r)}>编辑</Button>
            ),
          },
        ]}
      />
      <Modal
        title="新建影厅"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => createForm.submit()}
      >
        <Form
          form={createForm}
          layout="vertical"
          onFinish={async (values) => {
            try {
              await adminApi.createHall({ ...values, cinemaId });
              message.success('已创建');
              setCreateOpen(false);
              createForm.resetFields();
              load();
            } catch {
              // 请求层已处理。
            }
          }}
        >
          <Form.Item name="name" label="厅名" rules={[{ required: true, whitespace: true, message: '请输入影厅名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="seatMapId"
            label="座位图"
            rules={[{ required: true, message: '请选择座位图' }]}
            extra={seatMapSelectExtra}
          >
            <Select
              placeholder="请选择当前影院的座位图"
              showSearch
              optionFilterProp="label"
              options={seatMapOptions}
              notFoundContent="暂无座位图"
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="编辑影厅"
        open={editOpen}
        onCancel={() => {
          setEditOpen(false);
          setEditingHall(null);
        }}
        onOk={() => editForm.submit()}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={async (values) => {
            if (!editingHall) return;
            try {
              await adminApi.updateHall(editingHall.hallId, {
                name: values.name,
                seatMapId: values.seatMapId,
              });
              message.success('已更新');
              setEditOpen(false);
              setEditingHall(null);
              editForm.resetFields();
              load();
              loadSeatMaps();
            } catch {
              // 请求层已处理。
            }
          }}
        >
          <Form.Item name="name" label="厅名" rules={[{ required: true, whitespace: true, message: '请输入影厅名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="seatMapId"
            label="座位图"
            rules={[{ required: true, message: '请选择座位图' }]}
            extra={
              <>
                {seatMapSelectExtra}
                <div style={{ color: 'rgba(0,0,0,0.45)', marginTop: seatMaps.length === 0 ? 0 : 4 }}>
                  换绑仅影响后续排片；已有场次仍使用原座位图。
                </div>
              </>
            }
          >
            <Select
              placeholder="请选择当前影院的座位图"
              showSearch
              optionFilterProp="label"
              options={seatMapOptions}
              notFoundContent="暂无座位图"
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default HallsPage;
