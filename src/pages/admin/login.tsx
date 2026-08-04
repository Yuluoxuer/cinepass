import React, { useState } from 'react';
import { history, useLocation } from 'umi';
import { Card, Form, Input, Button, message } from 'antd';
import * as authApi from '@/api/auth';
import { getCinemaIdFromAccessToken, useAuthStore, isStaffOrAdmin } from '@/stores/auth';
import type { UserVO } from '@/types';
import MockToggle from '@/components/MockToggle';
import '@/styles/tokens.css';

const AdminLoginPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const setLogin = useAuthStore((s) => s.setLogin);
  const loc = useLocation();
  const redirect = new URLSearchParams(loc.search).get('redirect') || '/admin';

  const onFinish = async (v: { account: string; password: string }) => {
    setLoading(true);
    try {
      const res = await authApi.login(v.account, v.password);
      if (!isStaffOrAdmin(res.role)) {
        message.error('该账号无管理端权限');
        return;
      }
      const user: UserVO = {
        userId: res.userId,
        nickname: res.nickname,
        phone: res.phone,
        role: res.role,
        avatarUrl: null,
        cinemaId: res.cinemaId || getCinemaIdFromAccessToken(res.accessToken),
      };
      setLogin({ accessToken: res.accessToken, expiresIn: res.expiresIn, user });
      history.replace(redirect);
    } catch (e) {
      message.error(e instanceof Error ? e.message : '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
      }}
    >
      <Card title="妙语运营后台登录" style={{ width: 400 }}>
        <Form layout="vertical" onFinish={onFinish} initialValues={{ account: '运营小王', password: 'demo123456' }}>
          <Form.Item name="account" label="账号" rules={[{ required: true }]}>
            <Input placeholder="昵称或手机号" />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 8 }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>
            登录
          </Button>
          <p style={{ marginTop: 12, color: '#999', fontSize: 12 }}>
            演示：运营小王 / 系统管理员，密码 demo123456
          </p>
        </Form>
      </Card>
      <MockToggle />
    </div>
  );
};

export default AdminLoginPage;
