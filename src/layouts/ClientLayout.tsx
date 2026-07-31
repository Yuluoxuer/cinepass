import React, { useEffect } from 'react';
import { Outlet } from 'umi';
import '@/styles/tokens.css';
import SiteHeader from '@/components/SiteHeader';
import LoginModal from '@/components/LoginModal';
import MockToggle from '@/components/MockToggle';
import AgentDrawer from '@/agent/AgentDrawer';
import { restoreLoginState, useAuthStore } from '@/stores/auth';
import { useBookingStore } from '@/stores/booking';

const ClientLayout: React.FC = () => {
  useEffect(() => {
    const { accessToken, tokenExpireAt, user } = restoreLoginState();
    if (accessToken) {
      useAuthStore.setState({ accessToken, tokenExpireAt, user });
    }
    void useBookingStore.getState().ensureSession();
  }, []);

  return (
    <div style={{ minHeight: '100vh', background: 'var(--color-bg-page)' }}>
      <SiteHeader />
      <Outlet />
      <AgentDrawer />
      <LoginModal />
      <MockToggle />
    </div>
  );
};

export default ClientLayout;
