import React from 'react';
import { Button, Result } from 'antd';
import { history, useLocation } from 'umi';

const RequestErrorPage: React.FC = () => {
  const location = useLocation();
  const params = new URLSearchParams(location.search);
  const reason = params.get('reason') || '请求失败，请检查网络或稍后重试';
  const status = params.get('status');
  const from = params.get('from');

  return (
    <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, background: 'var(--color-bg-page, #f6f1e8)' }}>
      <Result
        status="warning"
        title={status ? `请求未完成（${status}）` : '请求未完成'}
        subTitle={reason}
        extra={[
          <Button key="retry" type="primary" onClick={() => history.replace(from && from.startsWith('/') ? from : '/')}>返回重试</Button>,
          <Button key="home" onClick={() => history.replace('/')}>返回首页</Button>,
        ]}
      />
    </main>
  );
};

export default RequestErrorPage;
