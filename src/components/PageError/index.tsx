import React from 'react';
import { Button, Result } from 'antd';
import { history } from 'umi';
import { ApiError } from '@/types';

export interface PageErrorProps {
  /** 主标题 */
  title?: string;
  /** 说明文案；也可直接传 Error */
  error?: unknown;
  description?: string;
  /** 重试 */
  onRetry?: () => void;
  /** 是否提供「返回上一页」 */
  showBack?: boolean;
}

function resolveMessage(error: unknown, fallback?: string): string {
  if (fallback) return fallback;
  if (error instanceof ApiError) return error.message || '请求失败';
  if (error instanceof Error) return error.message || '请求失败';
  if (typeof error === 'string') return error;
  return '页面加载失败，请稍后重试';
}

function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.errorCode === 'UNAUTHORIZED';
}

/**
 * 管理端 / C 端统一错误页：避免未捕获 ApiError 弹出 React 开发红屏。
 */
const PageError: React.FC<PageErrorProps> = ({
  title,
  error,
  description,
  onRetry,
  showBack = true,
}) => {
  const unauthorized = isUnauthorized(error);
  const msg = resolveMessage(error, description);
  const heading = title || (unauthorized ? '需要登录' : '出错了');

  return (
    <div style={{ padding: '48px 24px', background: 'var(--color-bg-card, #fff)', borderRadius: 8 }}>
      <Result
        status={unauthorized ? 'warning' : 'error'}
        title={heading}
        subTitle={msg}
        extra={[
          unauthorized ? (
            <Button
              type="primary"
              key="login"
              onClick={() => {
                const path = window.location.pathname + window.location.search;
                if (path.startsWith('/admin')) {
                  history.push(`/admin/login?redirect=${encodeURIComponent(path)}`);
                } else {
                  history.push('/');
                }
              }}
            >
              去登录
            </Button>
          ) : null,
          onRetry ? (
            <Button type={unauthorized ? 'default' : 'primary'} key="retry" onClick={onRetry}>
              重试
            </Button>
          ) : null,
          showBack ? (
            <Button key="back" onClick={() => history.back()}>
              返回
            </Button>
          ) : null,
        ].filter(Boolean)}
      />
    </div>
  );
};

export default PageError;
