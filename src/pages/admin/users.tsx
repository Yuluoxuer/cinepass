import React, { useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, message, Empty} from 'antd';
import * as adminApi from '@/api/admin';
import * as catalogApi from '@/api/catalog';
import type { AdminUserVO, CinemaVO } from '@/types';
import { useAuthStore } from '@/stores/auth';

function isActiveStatus(status: AdminUserVO['status']) {
  return status === 'active' || status === 1;
}

const AdminUsersPage: React.FC = () => {
  const [data, setData] = useState<AdminUserVO[]>([]);
  const [cinemas, setCinemas] = useState<CinemaVO[]>([]);
  const [open, setOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUserVO | null>(null);
  const [form] = Form.useForm();
  const [roleForm] = Form.useForm<{ role: AdminUserVO['role']; cinemaId?: string }>();
  const createRole = Form.useWatch('role', form);
  const editRole = Form.useWatch('role', roleForm);
  const currentUserId = useAuthStore((s) => s.user?.userId);

  const load = () => void adminApi.listUsers().then((r) => setData(r.items)).catch(() => setData([]));

  useEffect(() => {
    load();
    void catalogApi
      .listCinemas({ sort: 'price', page: 1, size: 50 })
      .then((r) => setCinemas(r.items))
      .catch(() => setCinemas([]));
  }, []);

  const cinemaOptions = cinemas.map((c) => ({ value: c.cinemaId, label: `${c.name}（${c.cinemaId}）` }));
  const cinemaName = (cinemaId?: string | null) => {
    if (!cinemaId) return '—';
    return cinemas.find((c) => c.cinemaId === cinemaId)?.name || cinemaId;
  };

  return (
    <div>
      <Button type="primary" style={{ marginBottom: 16 }} onClick={() => setOpen(true)}>
        + 新建
      </Button>
      <Table
        rowKey="userId"
        dataSource={data}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有数据" /> }}
        columns={[
          { title: '昵称', dataIndex: 'nickname' },
          { title: '手机', dataIndex: 'phone' },
          { title: '角色', dataIndex: 'role' },
          {
            title: '所属影院',
            dataIndex: 'cinemaId',
            render: (cinemaId: string | null | undefined, r) =>
              r.role === 'staff' ? cinemaName(cinemaId) : '—',
          },
          {
            title: '状态',
            dataIndex: 'status',
            render: (status: AdminUserVO['status']) => (
              <Tag color={isActiveStatus(status) ? 'green' : 'default'}>
                {isActiveStatus(status) ? '启用' : '停用'}
              </Tag>
            ),
          },
          {
            title: '操作',
            render: (_, r) => {
              const isSelf = r.userId === currentUserId;
              const active = isActiveStatus(r.status);
              return (
                <Space>
                  <Button
                    type="link"
                    disabled={isSelf}
                    onClick={() => {
                      roleForm.setFieldsValue({
                        role: r.role,
                        cinemaId: r.cinemaId || undefined,
                      });
                      setEditingUser(r);
                    }}
                  >
                    编辑角色
                  </Button>
                  <Popconfirm
                    title={active ? '确认停用该用户？' : '确认启用该用户？'}
                    description={active ? '停用后该用户的现有会话会立即失效。' : undefined}
                    onConfirm={async () => {
                      try {
                        await adminApi.updateUser(r.userId, {
                          status: active ? 0 : 1,
                        });
                        message.success(active ? '用户已停用' : '用户已启用');
                        load();
                      } catch {
                        // 请求层已处理。
                      }
                    }}
                    disabled={isSelf}
                  >
                    <Button type="link" danger={active} disabled={isSelf}>
                      {active ? '停用' : '启用'}
                    </Button>
                  </Popconfirm>
                  {isSelf ? <span style={{ color: '#999', fontSize: 12 }}>当前账号不可改权限</span> : null}
                </Space>
              );
            },
          },
        ]}
      />
      <Modal
        title="新建用户"
        open={open}
        onCancel={() => {
          setOpen(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={async (v) => {
            try {
              const body = {
                ...v,
                cinemaId: v.role === 'staff' ? v.cinemaId : null,
              };
              await adminApi.createUser(body);
              message.success('已创建');
              setOpen(false);
              form.resetFields();
              load();
            } catch {
              // 请求层已处理。
            }
          }}
        >
          <Form.Item name="nickname" label="昵称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="手机">
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 8 }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="role" label="角色" initialValue="staff">
            <Select
              options={[
                { value: 'user', label: 'user' },
                { value: 'staff', label: 'staff' },
                { value: 'admin', label: 'admin' },
              ]}
              onChange={(role) => {
                if (role !== 'staff') form.setFieldValue('cinemaId', undefined);
              }}
            />
          </Form.Item>
          {createRole === 'staff' ? (
            <Form.Item
              name="cinemaId"
              label="所属影院"
              rules={[{ required: true, message: '工作人员必须绑定影院' }]}
            >
              <Select showSearch optionFilterProp="label" options={cinemaOptions} placeholder="选择影院" />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
      <Modal
        title="编辑角色"
        open={Boolean(editingUser)}
        onCancel={() => {
          setEditingUser(null);
          roleForm.resetFields();
        }}
        onOk={() => roleForm.submit()}
        destroyOnHidden
      >
        <Form
          form={roleForm}
          layout="vertical"
          onFinish={async ({ role, cinemaId }) => {
            if (!editingUser) return;
            try {
              await adminApi.updateUser(editingUser.userId, {
                role,
                cinemaId: role === 'staff' ? cinemaId || null : null,
              });
              message.success('角色已更新');
              setEditingUser(null);
              roleForm.resetFields();
              load();
            } catch {
              // 请求层已处理。
            }
          }}
        >
          <Form.Item name="role" label="角色" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'user', label: 'user' },
                { value: 'staff', label: 'staff' },
                { value: 'admin', label: 'admin' },
              ]}
              onChange={(role) => {
                if (role !== 'staff') roleForm.setFieldValue('cinemaId', undefined);
              }}
            />
          </Form.Item>
          {editRole === 'staff' ? (
            <Form.Item
              name="cinemaId"
              label="所属影院"
              rules={[{ required: true, message: '工作人员必须绑定影院' }]}
            >
              <Select showSearch optionFilterProp="label" options={cinemaOptions} placeholder="选择影院" />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </div>
  );
};

export default AdminUsersPage;
