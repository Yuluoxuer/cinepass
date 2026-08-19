import React from 'react';
import PageError from '@/components/PageError';

interface State {
  error: Error | null;
}

/**
 * 捕获子树渲染期异常，展示友好错误页（非 React 开发红屏）。
 */
export default class AdminErrorBoundary extends React.Component<
  { children: React.ReactNode },
  State
> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error) {
    // eslint-disable-next-line no-console
    console.error('[AdminErrorBoundary]', error);
  }

  render() {
    if (this.state.error) {
      return (
        <PageError
          error={this.state.error}
          onRetry={() => {
            this.setState({ error: null });
            window.location.reload();
          }}
        />
      );
    }
    return this.props.children;
  }
}
