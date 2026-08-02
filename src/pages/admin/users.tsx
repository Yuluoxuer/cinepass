import React, { useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import * as adminApi from '@/api/admin';
import type { AdminUserVO } from '@/types';
import { useAuthStore } from '@/stores/auth';

const AdminUsersPage: React.FC = () => {
  const [data, setData] = useState<AdminUserVO[]>([]);
  const [open, setOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<AdminUserVO | null>(null);
  const [form] = Form.useForm();
  const [roleForm] = Form.useForm<{ role: AdminUserVO['role'] }>();
  const currentUserId = useAuthStore((s) => s.user?.userId);

  const load = () => void adminApi.listUsers().then((r) => setData(r.items));

  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <Button type="primary" style={{ marginBottom: 16 }} onClick={() => setOpen(true)}>
        + 新建
      </Button>
      <Table
        rowKey="userId"
        dataSource={data}
        columns={[
          { title: '昵称', dataIndex: 'nickname' },
          { title: '手机', dataIndex: 'phone' },
          { title: '角色', dataIndex: 'role' },
          {
            title: '状态',
            dataIndex: 'status',
            render: (status: AdminUserVO['status']) => (
              <Tag color={status === 'active' ? 'green' : 'default'}>
                {status === 'active' ? '启用' : '停用'}
              </Tag>
            ),
          },
          {
            title: '操作',
            render: (_, r) => {
              const isSelf = r.userId === currentUserId;
              return (
                <Space>
                  <Button
                    type="link"
                    disabled={isSelf}
                    onClick={() => {
                      roleForm.setFieldsValue({ role: r.role });
                      setEditingUser(r);
                    }}
                  >
                    编辑角色
                  </Button>
                  <Popconfirm
                    title={r.status === 'active' ? '确认停用该用户？' : '确认启用该用户？'}
                    description={r.status === 'active' ? '停用后该用户的现有会话会立即失效。' : undefined}
                    onConfirm={async () => {
                      await adminApi.updateUser(r.userId, {
                        status: r.status === 'active' ? 'disabled' : 'active',
                      });
                      message.success(r.status === 'active' ? '用户已停用' : '用户已启用');
                      load();
                    }}
                    disabled={isSelf}
                  >
                    <Button type="link" danger={r.status === 'active'} disabled={isSelf}>
                      {r.status === 'active' ? '停用' : '启用'}
                    </Button>
                  </Popconfirm>
                  {isSelf ? <span style={{ color: '#999', fontSize: 12 }}>当前账号不可改权限</span> : null}
                </Space>
              );
            },
          },
        ]}
      />
      <Modal title="新建用户" open={open} onCancel={() => setOpen(false)} onOk={() => form.submit()}>
        <Form
          form={form}
          layout="vertical"
          onFinish={async (v) => {
            await adminApi.createUser(v);
            message.success('已创建');
            setOpen(false);
            form.resetFields();
            load();
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
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="编辑角色"
        open={Boolean(editingUser)}
        onCancel={() => setEditingUser(null)}
        onOk={() => roleForm.submit()}
        destroyOnHidden
      >
        <Form
          form={roleForm}
          layout="vertical"
          onFinish={async ({ role }) => {
            if (!editingUser) return;
            await adminApi.updateUser(editingUser.userId, { role });
            message.success('角色已更新');
            setEditingUser(null);
            roleForm.resetFields();
            load();
          }}
        >
          <Form.Item name="role" label="角色" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'user', label: 'user' },
                { value: 'staff', label: 'staff' },
                { value: 'admin', label: 'admin' },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AdminUsersPage;
