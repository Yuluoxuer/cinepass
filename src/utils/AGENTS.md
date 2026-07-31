<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# utils

## Purpose
Pure utility functions with no UI concerns. Covers three domains: JWT/token lifecycle management, a typed axios HTTP client, and value formatters. These are low-level building blocks imported by stores, services, and `app.tsx`.

## Key Files

| File | Description |
|------|-------------|
| `token.ts` | JWT and session utilities: `getAccessToken`, `getRefreshToken`, `isAccessTokenExpired`, `isSessionReady`, `refreshAccessToken` (single-flight), `ensureValidAccessToken`, `restoreSession`, `handleAuthFailure` |
| `request.ts` | Typed axios instance with auto-token injection, 401 auto-retry with refresh, response unwrapping, and typed `get`/`post`/`put` helpers; exports `ApiError` class |
| `format.ts` | Value formatters: `formatMoney` (→ `¥1,234.56`), `displayOrDash` (null/empty → `'-'`) |

## For AI Agents

### Working In This Directory
- `token.ts` is the canonical token source — read tokens only through its exported functions, not directly from sessionStorage/localStorage
- `refreshAccessToken()` is single-flight: concurrent callers share one in-flight promise; never call `fetch('/api/auth/refresh')` directly elsewhere
- `restoreSession()` is also single-flight and is awaited by the layout and `onRouteChange` before allowing navigation
- `request.ts` exports `ApiError` — catch this type in pages to distinguish API errors from network errors
- Do not add side effects (DOM manipulation, store writes) to `format.ts`

### Common Patterns
```ts
// Token check before a non-Umi fetch
const token = await ensureValidAccessToken();
if (!token) { handleAuthFailure(); return; }

// Typed API call via utils/request
import { get, post } from '@/utils/request';
const data = await get<Item[]>('/api/items', { page: 1 });

// Safe display
import { displayOrDash, formatMoney } from '@/utils/format';
<span>{formatMoney(employee.salary)}</span>
```

### Testing Requirements
- `token.ts` functions are pure (read from storage, call fetch) — mock `sessionStorage`, `localStorage`, and `fetch` in tests
- `format.ts` functions are pure — unit test with plain inputs

## Dependencies

### Internal
- `@/stores/auth` — `useAuthStore` for token writes in `refreshAccessToken` and `restoreSession`

### External
- `axios` — HTTP client (`request.ts`)
- `antd` — `message` for auth-failure toasts (`token.ts`, `request.ts`)

<!-- MANUAL: -->
