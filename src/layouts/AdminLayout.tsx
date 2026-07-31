import React, { useEffect } from 'react';
import { Outlet, history, useLocation } from 'umi';
import { Layout, Menu, Dropdown } from 'antd';
import {
  DashboardOutlined,
  VideoCameraOutlined,
  ShopOutlined,
  BorderOuterOutlined,
  CalendarOutlined,
  OrderedListOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { restoreLoginState, useAuthStore, isStaffOrAdmin } from '@/stores/auth';
import * as authApi from '@/api/auth';
import MockToggle from '@/components/MockToggle';
import '@/styles/tokens.css';

const { Header, Sider, Content } = Layout;

const AdminLayout: React.FC = () => {
  const loc = useLocation();
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);

  useEffect(() => {
    const restored = restoreLoginState();
    if (restored.accessToken) {
      useAuthStore.setState({
        accessToken: restored.accessToken,
        tokenExpireAt: restored.tokenExpireAt,
        user: restored.user,
      });
    }
    const u = restored.user || useAuthStore.getState().user;
    const token = restored.accessToken || useAuthStore.getState().accessToken;
    if (!token || !u || !isStaffOrAdmin(u.role)) {
      history.replace(`/admin/login?redirect=${encodeURIComponent(loc.pathname)}`);
    }
  }, []);

  const items = [
    { key: '/admin', icon: <DashboardOutlined />, label: '概览' },
    { key: '/admin/movies', icon: <VideoCameraOutlined />, label: '影片' },
    { key: '/admin/cinemas', icon: <ShopOutlined />, label: '影院' },
    { key: '/admin/seat-maps', icon: <BorderOuterOutlined />, label: '座位图' },
    { key: '/admin/shows', icon: <CalendarOutlined />, label: '排片' },
    { key: '/admin/orders', icon: <OrderedListOutlined />, label: '订单' },
    { key: '/admin/tickets/verify', icon: <SafetyCertificateOutlined />, label: '验票' },
    ...(user?.role === 'admin'
      ? [{ key: '/admin/users', icon: <UserOutlined />, label: '用户' }]
      : []),
  ];

  const logout = async () => {
    try {
      await authApi.logout();
    } catch {
      /* ignore */
    }
    clearAuth();
    history.push('/admin/login');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={220} theme="dark">
        <div
          style={{
            height: 56,
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 700,
            fontSize: 16,
          }}
        >
          妙语运营后台
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[loc.pathname]}
          items={items}
          onClick={({ key }) => history.push(key)}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            justifyContent: 'flex-end',
            alignItems: 'center',
          }}
        >
          <Dropdown
            menu={{
              items: [
                {
                  key: 'logout',
                  icon: <LogoutOutlined />,
                  label: '退出',
                  onClick: () => void logout(),
                },
              ],
            }}
          >
            <span style={{ cursor: 'pointer' }}>{user?.nickname || '管理员'} ▼</span>
          </Dropdown>
        </Header>
        <Content style={{ margin: 24, background: '#fff', padding: 24, borderRadius: 8 }}>
          <Outlet />
        </Content>
      </Layout>
      <MockToggle />
    </Layout>
  );
};

export default AdminLayout;
