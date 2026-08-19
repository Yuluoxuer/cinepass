import React, { useEffect, useState } from 'react';
import { Outlet, history, useLocation } from 'umi';
import { Breadcrumb, Button, Layout, Menu, Dropdown, message } from 'antd';
import {
  DashboardOutlined,
  VideoCameraOutlined,
  ShopOutlined,
  BorderOuterOutlined,
  CalendarOutlined,
  OrderedListOutlined,
  SafetyCertificateOutlined,
  BookOutlined,
  UserOutlined,
  LogoutOutlined,
  KeyOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons';
import { getCinemaIdFromAccessToken, restoreLoginState, useAuthStore, isStaffOrAdmin } from '@/stores/auth';
import * as authApi from '@/api/auth';
import AdminErrorBoundary from '@/components/AdminErrorBoundary';
import ChangePasswordModal from '@/components/ChangePasswordModal';
import '@/styles/tokens.css';

const { Header, Sider, Content } = Layout;

type AdminRouteMeta = {
  menuKey: string;
  crumbs: string[];
  description?: string;
  match: (pathname: string) => boolean;
};

/**
 * 后台子页不直接出现在菜单中，需在这里声明其所属模块和面包屑层级。
 * 这样列表、新建、编辑页共用同一份导航规则，避免路由切换时状态脱节。
 */
const ADMIN_ROUTE_META: AdminRouteMeta[] = [
  {
    menuKey: '/admin/movies',
    crumbs: ['影片资源', '新建影片'],
    description: '录入影片的展示信息与上映资料。',
    match: (path) => path === '/admin/movies/new',
  },
  {
    menuKey: '/admin/movies',
    crumbs: ['影片资源', '编辑影片'],
    description: '维护影片的展示信息与上映资料。',
    match: (path) => /^\/admin\/movies\/[^/]+$/.test(path),
  },
  { menuKey: '/admin/movies', crumbs: ['影片资源'], match: (path) => path === '/admin/movies' },
  {
    menuKey: '/admin/cinemas',
    crumbs: ['影院', '新建影院'],
    description: '填写影院基础资料，保存后可继续配置影厅。',
    match: (path) => path === '/admin/cinemas/new',
  },
  {
    menuKey: '/admin/cinemas',
    crumbs: ['影院', '影厅管理'],
    description: '管理影厅及其关联的座位图。',
    match: (path) => /^\/admin\/cinemas\/[^/]+\/halls$/.test(path),
  },
  {
    menuKey: '/admin/cinemas',
    crumbs: ['影院', '编辑影院'],
    description: '维护影院基础资料与服务范围。',
    match: (path) => /^\/admin\/cinemas\/[^/]+$/.test(path),
  },
  { menuKey: '/admin/cinemas', crumbs: ['影院'], match: (path) => path === '/admin/cinemas' },
  {
    menuKey: '/admin/seat-maps',
    crumbs: ['座位图', '新建座位图'],
    description: '绘制座位布局，并设置分区和座位类型。',
    match: (path) => path === '/admin/seat-maps/new',
  },
  {
    menuKey: '/admin/seat-maps',
    crumbs: ['座位图', '编辑座位图'],
    description: '调整座位布局、分区和座位类型。',
    match: (path) => /^\/admin\/seat-maps\/[^/]+$/.test(path),
  },
  { menuKey: '/admin/seat-maps', crumbs: ['座位图'], match: (path) => path === '/admin/seat-maps' },
  { menuKey: '/admin/shows', crumbs: ['排片工作台'], match: (path) => path === '/admin/shows' },
  { menuKey: '/admin/orders', crumbs: ['订单'], match: (path) => path === '/admin/orders' },
  {
    menuKey: '/admin/tickets/verify',
    crumbs: ['订单与验票'],
    match: (path) => path === '/admin/tickets/verify',
  },
  { menuKey: '/admin/users', crumbs: ['用户权限'], match: (path) => path === '/admin/users' },
  {
    menuKey: '/admin/knowledge',
    crumbs: ['知识库'],
    description: '维护供购票助手检索的知识文档（admin 管理系统的，staff 管理本院）。',
    match: (path) => path === '/admin/knowledge',
  },
  { menuKey: '/admin', crumbs: ['运营总览'], match: (path) => path === '/admin' },
];

const AdminLayout: React.FC = () => {
  const loc = useLocation();
  const user = useAuthStore((s) => s.user);
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const setUser = useAuthStore((s) => s.setUser);
  const [verified, setVerified] = useState(false);
  const [changePwdOpen, setChangePwdOpen] = useState(false);

  useEffect(() => {
    let disposed = false;
    const restored = restoreLoginState();
    if (restored.accessToken) {
      useAuthStore.setState({
        accessToken: restored.accessToken,
        tokenExpireAt: restored.tokenExpireAt,
        user: restored.user,
      });
    }
    const token = restored.accessToken || useAuthStore.getState().accessToken;
    if (!token) {
      clearAuth();
      history.replace(`/admin/login?redirect=${encodeURIComponent(loc.pathname)}`);
      return () => {
        disposed = true;
      };
    }

    void authApi
      .me()
      .then((serverUser) => {
        if (disposed) return;
        if (!isStaffOrAdmin(serverUser.role)) {
          clearAuth();
          history.replace(`/admin/login?redirect=${encodeURIComponent(loc.pathname)}`);
          return;
        }
        if (loc.pathname.startsWith('/admin/users') && serverUser.role !== 'admin') {
          history.replace('/admin');
          return;
        }
        if (serverUser.role === 'staff') {
          const staffCinemaId = serverUser.cinemaId || getCinemaIdFromAccessToken(token);
          const cinemaRoute = loc.pathname.match(/^\/admin\/cinemas\/([^/]+)(?:\/halls)?$/);
          if (loc.pathname === '/admin/cinemas/new') {
            message.error('仅管理员可新建影院');
            history.replace('/admin/cinemas');
            return;
          }
          if (cinemaRoute && (!staffCinemaId || cinemaRoute[1] !== staffCinemaId)) {
            message.error(staffCinemaId ? '员工只能管理所属影院' : '未识别所属影院，暂不可执行影院管理操作');
            history.replace('/admin/cinemas');
            return;
          }
        }
        setUser(serverUser);
        setVerified(true);
      })
      .catch(() => {
        if (disposed) return;
        clearAuth();
        history.replace(`/admin/login?redirect=${encodeURIComponent(loc.pathname)}`);
      });

    return () => {
      disposed = true;
    };
  }, [clearAuth, loc.pathname, setUser]);

  const items = [
    { key: '/admin', icon: <DashboardOutlined />, label: '运营概览' },
    { key: '/admin/movies', icon: <VideoCameraOutlined />, label: '影片资源' },
    { key: '/admin/cinemas', icon: <ShopOutlined />, label: '影院' },
    { key: '/admin/seat-maps', icon: <BorderOuterOutlined />, label: '座位图' },
    { key: '/admin/shows', icon: <CalendarOutlined />, label: '排片工作台' },
    { key: '/admin/orders', icon: <OrderedListOutlined />, label: '订单' },
    { key: '/admin/tickets/verify', icon: <SafetyCertificateOutlined />, label: '订单与验票' },
    { key: '/admin/knowledge', icon: <BookOutlined />, label: '知识库' },
    ...(user?.role === 'admin'
      ? [{ key: '/admin/users', icon: <UserOutlined />, label: '用户权限' }]
      : []),
  ];

  const logout = async () => {
    try {
      // 接口异常/挂起时也要保证能退出：3 秒内没返回就继续走本地清理流程
      await Promise.race([
        authApi.logout(),
        new Promise((resolve) => setTimeout(resolve, 3000)),
      ]);
    } catch {
      /* ignore */
    }
    clearAuth();
    history.push('/admin/login');
  };

  const routeMeta = ADMIN_ROUTE_META.find((item) => item.match(loc.pathname));
  const activeMenuKey = routeMeta?.menuKey || '/admin';
  const crumbs = routeMeta?.crumbs || ['工作台'];
  const isSecondaryPage = crumbs.length > 1;
  const secondaryDescription = routeMeta?.description || '完成配置后保存即可生效。';

  if (!verified) return null;

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
          selectedKeys={[activeMenuKey]}
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
            <Breadcrumb
              separator="/"
              items={[
                { title: '妙语运营' },
                ...crumbs.map((title, index) => ({
                  title:
                    index === crumbs.length - 1 ? (
                      <b style={{ color: 'var(--color-text-primary)' }}>{title}</b>
                    ) : (
                      title
                    ),
                })),
              ]}
              style={{ color: '#77808d', fontSize: 12 }}
            />
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
            trigger={['click', 'hover']}
            menu={{
              items: [
                {
                  key: 'change-password',
                  icon: <KeyOutlined />,
                  label: '修改密码',
                },
                {
                  key: 'logout',
                  icon: <LogoutOutlined />,
                  label: '退出',
                },
              ],
              onClick: ({ key }) => {
                if (key === 'change-password') setChangePwdOpen(true);
                if (key === 'logout') void logout();
              },
            }}
          >
            <span style={{ cursor: 'pointer', fontSize: 13 }}>{user?.nickname || '管理员'} ▾</span>
          </Dropdown>
        </Header>
        <Content
          style={{
            margin: 28,
            background: isSecondaryPage ? 'transparent' : '#fff',
            padding: isSecondaryPage ? 0 : 24,
            border: isSecondaryPage ? 0 : '1px solid #dce0e4',
            minHeight: 360,
          }}
        >
          <AdminErrorBoundary>
            {isSecondaryPage && (
              <section
                style={{
                  width: '100%',
                  minHeight: 360,
                  padding: '28px 32px 36px',
                  background: '#fff',
                  border: '1px solid #dce0e4',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'flex-start',
                    gap: 24,
                    paddingBottom: 20,
                    marginBottom: 24,
                    borderBottom: '1px solid #eef0f2',
                  }}
                >
                  <div>
                    <h1
                      style={{
                        margin: 0,
                        color: 'var(--color-text-primary)',
                        fontFamily: 'var(--font-display)',
                        fontSize: 24,
                        lineHeight: 1.35,
                      }}
                    >
                      {crumbs[crumbs.length - 1]}
                    </h1>
                    <p style={{ margin: '7px 0 0', color: '#77808d', fontSize: 13 }}>{secondaryDescription}</p>
                  </div>
                  <Button type="link" icon={<ArrowLeftOutlined />} onClick={() => history.push(activeMenuKey)}>
                    返回{crumbs[0]}
                  </Button>
                </div>
                <Outlet />
              </section>
            )}
            {!isSecondaryPage && <Outlet />}
          </AdminErrorBoundary>
        </Content>
      </Layout>
      <ChangePasswordModal
        open={changePwdOpen}
        account={user?.nickname || user?.phone || ''}
        onClose={() => setChangePwdOpen(false)}
        onChanged={() => {
          // 后端已吊销旧会话，清空本地登录态并回后台登录页用新密码重新登录
          clearAuth();
          setChangePwdOpen(false);
          history.replace('/admin/login');
        }}
      />
    </Layout>
  );
};

export default AdminLayout;
