import React, { useEffect, useState } from 'react';
import { Button, Form, Input, Modal, Select, Table, message } from 'antd';
import * as adminApi from '@/api/admin';
import type { AdminUserVO } from '@/types';

const AdminUsersPage: React.FC = () => {
  const [data, setData] = useState<AdminUserVO[]>([]);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

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
          { title: '状态', dataIndex: 'status' },
          {
            title: '操作',
            render: (_, r) => (
              <Button
                type="link"
                onClick={() => {
                  Modal.confirm({
                    title: '编辑角色',
                    content: (
                      <Select
                        id="user-role"
                        defaultValue={r.role}
                        style={{ width: '100%' }}
                        options={[
                          { value: 'user', label: 'user' },
                          { value: 'staff', label: 'staff' },
                          { value: 'admin', label: 'admin' },
                        ]}
                      />
                    ),
                    onOk: async () => {
                      const el = document.getElementById('user-role') as HTMLInputElement;
                      await adminApi.updateUser(r.userId, { role: (el?.value as AdminUserVO['role']) || r.role });
                      message.success('已更新');
                      load();
                    },
                  });
                }}
              >
                编辑
              </Button>
            ),
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
    </div>
  );
};

export default AdminUsersPage;
