<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# services

## Purpose
API service modules — one file per backend controller group. Each module exports async functions that map directly to backend endpoints. Services use either the Umi `request` wrapper (from `'umi'`) or the custom axios instance in `@/utils/request`; both handle auth headers and response unwrapping automatically.

## Key Files

| File | Description |
|------|-------------|
| `auth.ts` | Authentication API: `login`, `logout`, `getCurrentUser`, `refreshToken`, `changePassword`, `resetPassword`, `getLoginLogs` |
| `profile.ts` | Personal profile API: `getMyProfile`, `updateMyProfile`, `getAttendanceCalendar`, `getAttendanceSummary`, `getMyLeaves`, `getPaySlips`, `getDashboard`, `submitLeave`, `clockIn` |
| `users.ts` | User management API: `getUsers`, `updateUserStatus`, `resetUserPassword` |
| `roles.ts` | Role management API: `getRoles`, `getRole`, `createRole`, `updateRole`, `deleteRole`, `getAllPermissions` |
| `audit.ts` | Audit log API: `getAuditLogs` |
| `request.ts` | Standalone axios instance (`baseURL: '/api'`, token injection, 401 auto-refresh) — alternative to the Umi `request` plugin |

## For AI Agents

### Working In This Directory
- Prefer the Umi `request` import (`import { request } from 'umi'`) over the local `request.ts` for new service files — it reuses the interceptors configured in `app.tsx`
- Use `request.ts` only for code that runs outside the Umi context (e.g., standalone scripts)
- Name new service files after the backend controller, e.g. `leave.ts` for `LeaveController`
- Export interfaces alongside functions in the same file (co-locate request/response types)
- The backend always wraps responses in `{ code, message, data }` — interceptors unwrap `.data`; your function return type should be the unwrapped type

### Common Patterns
```ts
// Paginated list
export async function getItems(params: ListParams) {
  return request<PageResult<Item>>('/api/items', { params });
}

// Mutation
export async function createItem(params: CreateParams) {
  return request('/api/items', { method: 'POST', data: params });
}
```

### Testing Requirements
- Mock `request` from `'umi'` or the axios instance when unit-testing pages that call service functions

## Dependencies

### Internal
- `@/utils/token` — `ensureValidAccessToken`, `refreshAccessToken`, `handleAuthFailure` (used by `request.ts`)
- `@/stores/auth` — token storage (via `utils/token`)
- `@/pages/system/types` — shared data types imported by `roles.ts`, `users.ts`, `audit.ts`

### External
- `umi` — `request` plugin (used in `auth.ts`, `profile.ts`, `roles.ts`, `users.ts`, `audit.ts`)
- `axios` — HTTP client (used in `request.ts`)
- `antd` — `message` for error toasts (dynamically imported in `request.ts`)

<!-- MANUAL: -->
