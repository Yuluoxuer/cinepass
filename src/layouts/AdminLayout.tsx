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

const CRUMB: Record<string, string> = {
  '/admin': '概览',
  '/admin/movies': '影片资源',
  '/admin/cinemas': '影院',
  '/admin/seat-maps': '座位图',
  '/admin/shows': '排片工作台',
  '/admin/orders': '订单',
  '/admin/tickets/verify': '验票',
  '/admin/users': '用户权限',
};

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
    { key: '/admin', icon: <DashboardOutlined />, label: '运营概览' },
    { key: '/admin/movies', icon: <VideoCameraOutlined />, label: '影片资源' },
    { key: '/admin/cinemas', icon: <ShopOutlined />, label: '影院' },
    { key: '/admin/seat-maps', icon: <BorderOuterOutlined />, label: '座位图' },
    { key: '/admin/shows', icon: <CalendarOutlined />, label: '排片工作台' },
    { key: '/admin/orders', icon: <OrderedListOutlined />, label: '订单' },
    { key: '/admin/tickets/verify', icon: <SafetyCertificateOutlined />, label: '订单与验票' },
    ...(user?.role === 'admin'
      ? [{ key: '/admin/users', icon: <UserOutlined />, label: '用户权限' }]
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

  const crumb =
    Object.entries(CRUMB).find(([path]) => loc.pathname === path)?.[1] ||
    Object.entries(CRUMB).find(([path]) => path !== '/admin' && loc.pathname.startsWith(path))?.[1] ||
    '工作台';

  return (
    <Layout style={{ minHeight: '100vh', background: 'var(--admin-bg)' }}>
      <Sider
        width={226}
        theme="dark"
        style={{
          background: 'var(--admin-sidebar-bg)',
          position: 'sticky',
          top: 0,
          height: '100vh',
        }}
      >
        <div
          style={{
            height: 72,
            color: '#fff',
            display: 'flex',
            alignItems: 'center',
            gap: 11,
            padding: '0 24px',
            borderBottom: '1px solid #2c394e',
          }}
        >
          <img
            src="/app-icon.png"
            alt=""
            style={{ width: 30, height: 30, borderRadius: '50% 50% 46% 54%', objectFit: 'cover' }}
          />
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.2 }}>
            <b style={{ fontFamily: 'var(--font-display)', fontSize: 18 }}>妙语运营</b>
            <small style={{ color: '#778499', fontSize: 11, marginTop: 3 }}>放映工作台</small>
          </div>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[loc.pathname]}
          items={items}
          onClick={({ key }) => history.push(key)}
          style={{ background: 'transparent', borderInlineEnd: 0, padding: '12px 8px' }}
        />
        <div
          style={{
            position: 'absolute',
            left: 14,
            right: 14,
            bottom: 18,
            borderTop: '1px solid #2c394e',
            paddingTop: 16,
            display: 'grid',
            gridTemplateColumns: '34px 1fr',
            gap: 9,
            alignItems: 'center',
            color: '#fff',
          }}
        >
          <span
            style={{
              width: 34,
              height: 34,
              borderRadius: '50%',
              display: 'grid',
              placeItems: 'center',
              background: 'var(--color-agent)',
              fontSize: 11,
              fontWeight: 700,
            }}
          >
            {(user?.nickname || '运').slice(0, 1)}
          </span>
          <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
            <b style={{ fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {user?.nickname || '运营'}
            </b>
            <small style={{ color: '#78869b', marginTop: 2, fontSize: 11 }}>
              {user?.role === 'admin' ? 'Administrator' : 'Staff'}
            </small>
          </div>
        </div>
      </Sider>
      <Layout style={{ background: 'var(--admin-bg)' }}>
        <Header
          style={{
            background: '#fff',
            padding: '0 26px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            height: 64,
            borderBottom: '1px solid #dfe2e6',
            lineHeight: 'normal',
          }}
        >
          <div style={{ display: 'flex', gap: 22, alignItems: 'center' }}>
            <span style={{ color: '#77808d', fontSize: 12 }}>
              妙语运营 / <b style={{ color: 'var(--color-text-primary)' }}>{crumb}</b>
            </span>
            <span style={{ fontSize: 11, color: '#668078', display: 'flex', alignItems: 'center', gap: 7 }}>
              <i
                style={{
                  display: 'inline-block',
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  background: 'var(--color-agent)',
                }}
              />
              票务中台运行正常
            </span>
          </div>
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
            <span style={{ cursor: 'pointer', fontSize: 13 }}>{user?.nickname || '管理员'} ▾</span>
          </Dropdown>
        </Header>
        <Content
          style={{
            margin: 28,
            background: '#fff',
            padding: 24,
            border: '1px solid #dce0e4',
            minHeight: 360,
          }}
        >
          <Outlet />
        </Content>
      </Layout>
      <MockToggle />
    </Layout>
  );
};

export default AdminLayout;
