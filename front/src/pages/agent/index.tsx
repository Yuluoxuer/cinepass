import React, { useEffect } from 'react';
import { useAgentStore } from '@/stores/agent';
import AgentDrawer from '@/agent/AgentDrawer';
import LoginModal from '@/components/LoginModal';
import '@/styles/tokens.css';

/** 窄屏全页 Agent：强制打开 Drawer */
const AgentPage: React.FC = () => {
  const openDrawer = useAgentStore((s) => s.openDrawer);

  useEffect(() => {
    openDrawer();
  }, []);

  return (
    <div style={{ minHeight: '100vh' }}>
      <AgentDrawer />
      <LoginModal />
    </div>
  );
};

export default AgentPage;
