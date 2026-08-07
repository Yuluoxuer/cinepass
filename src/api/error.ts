import { message } from 'antd';
import { history } from 'umi';
import { ApiError } from '@/types';

/**
 * 扫码支付/核销流程错误文案。后端错误 envelope 只带数字 code（无 errorCode 字符串），
 * 这里按业务码映射可直接展示的提示。
 */
export function qrFlowErrorMessage(error: ApiError, mode: 'pay' | 'redeem'): string {
  switch (error.code) {
    case 4101:
      return '支付已超时，请重新下单';
    case 4103:
      return '仅待支付订单可发起支付';
    case 4104:
      return '仅已出票订单可核销';
    case 4105:
    case 4107:
      return '二维码无效，请重新生成';
    case 4106:
    case 4108:
      return '二维码已失效，请重新生成';
    case 404:
      return '订单不存在';
    case 40101:
      return '链接无效或未授权';
    case 40301:
      return '无权操作该订单';
    default:
      return error.message || `${mode === 'pay' ? '支付' : '核销'}失败，请稍后重试`;
  }
}

/** 将后端错误码映射为可直接展示给用户的提示。 */
export function formatApiError(error: ApiError): string {
  if (error.errorCode === 'UNAUTHORIZED' || error.httpStatus === 401 || error.code === 401 || error.code === 40101) {
    return '登录已过期，请重新登录后继续操作';
  }
  if (error.errorCode === 'FORBIDDEN' || error.httpStatus === 403 || error.code === 403 || error.code === 40301) {
    return error.message || '当前账号无权访问此资源，请切换到有权限的影院或联系管理员';
  }
  if (error.errorCode === 'NOT_FOUND' || error.httpStatus === 404 || error.code === 404) {
    return '资源不存在或已被删除，请返回后重试';
  }
  if (error.errorCode === 'DRAFT_CONFLICT' || error.httpStatus === 409 || error.code === 409) {
    return '购票信息已更新，请刷新后重试';
  }
  if ((error.httpStatus || 0) >= 500 || error.code >= 500) {
    return '服务暂时不可用，请稍后重试';
  }
  return error.message || '请求失败，请稍后重试';
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError || (typeof error === 'object' && error !== null && (error as { name?: string }).name === 'ApiError');
}

function isUnauthorized(error: ApiError) {
  return error.errorCode === 'UNAUTHORIZED' || error.httpStatus === 401 || error.code === 401 || error.code === 40101;
}

/** 仅网络/服务端故障进入错误页；权限和其他业务错误保留当前页面。 */
export function shouldRedirectToRequestError(error: ApiError) {
  if (error.silent || isUnauthorized(error)) return false;
  if ((error.httpStatus != null && error.httpStatus >= 400 && error.httpStatus < 500) || (error.code >= 400 && error.code < 500)) {
    return false;
  }
  if ((error.httpStatus || 0) >= 500 || error.code >= 500) return true;
  // Mock 与部分后端业务失败使用 code=-1 + errorCode，仍应作为当前页业务提示处理。
  if (error.errorCode) return false;
  return error.code < 0;
}

/** 由响应拦截器统一提示，silent 仅抑制提示但仍标记为已处理，防止重复弹窗。 */
export function presentApiError(error: ApiError, silent?: boolean) {
  if (error.notified) return;
  error.notified = true;
  if (!silent) message.error(formatApiError(error));
}

/** 页面遗漏 catch 时，跳转到可恢复的错误页，避免开发环境显示未捕获 Promise 红屏。 */
export function redirectToRequestError(error: ApiError) {
  if (!shouldRedirectToRequestError(error)) return;
  if (typeof window === 'undefined' || window.location.pathname === '/error') return;
  const query = new URLSearchParams({
    reason: formatApiError(error),
    from: `${window.location.pathname}${window.location.search}`,
  });
  if (error.httpStatus) query.set('status', String(error.httpStatus));
  history.replace(`/error?${query.toString()}`);
}
