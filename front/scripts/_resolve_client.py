from pathlib import Path

path = Path("src/api/client.ts")
text = path.read_text()

old_auth = """function handleUnauthorized() {
  useAuthStore.getState().clearAuth();
  if (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')) {
    const redirect = `${window.location.pathname}${window.location.search}`;
    window.location.replace(`/admin/login?redirect=${encodeURIComponent(redirect)}`);
    return;
  }
  void useAuthStore.getState().openLoginModal();
}"""

new_auth = """function handleUnauthorized() {
  useAuthStore.getState().clearAuth();
  if (typeof window !== 'undefined' && window.location.pathname.startsWith('/admin')) {
    // 管理端走独立登录页，避免 C 端 LoginModal；已在登录页则不再跳转
    if (!window.location.pathname.startsWith('/admin/login')) {
      const redirect = `${window.location.pathname}${window.location.search}`;
      window.location.replace(`/admin/login?redirect=${encodeURIComponent(redirect)}`);
    }
    return;
  }
  void useAuthStore.getState().openLoginModal();
}

function isUnauthorizedError(err: unknown): boolean {
  return (
    err instanceof ApiError &&
    (err.errorCode === 'UNAUTHORIZED' || err.httpStatus === 401 || err.code === 401 || err.code === 40101)
  );
}

/** 鉴权失败已跳转/弹窗后，挂起 Promise，避免未捕获 rejection 触发 React 开发红屏 */
function pendingAuthRedirect<T>(): Promise<T> {
  return new Promise<T>(() => {});
}"""

if old_auth not in text:
    raise SystemExit("handleUnauthorized block not found")
text = text.replace(old_auth, new_auth, 1)

old_reject = """function rejectApiError(error: ApiError, silent?: boolean): Promise<never> {
  error.silent = Boolean(silent) || Boolean(error.silent);
  presentApiError(error, silent);
  redirectToRequestError(error);
  return Promise.reject(error);
}"""

new_reject = """function rejectApiError(error: ApiError, silent?: boolean): Promise<never> {
  error.silent = Boolean(silent) || Boolean(error.silent);
  presentApiError(error, silent);
  redirectToRequestError(error);
  if (isUnauthorizedError(error)) {
    return pendingAuthRedirect();
  }
  return Promise.reject(error);
}"""

if old_reject not in text:
    raise SystemExit("rejectApiError block not found")
text = text.replace(old_reject, new_reject, 1)

path.write_text(text)
print("client.ts resolved")
