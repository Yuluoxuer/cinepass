<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# stores

## Purpose
Zustand global state stores. Each file manages one domain of shared runtime state. Stores are the single source of truth for auth tokens, current user, UI theme, and breadcrumb overrides. Components and pages read from stores via React hooks; mutations go through the store's action methods.

## Key Files

| File | Description |
|------|-------------|
| `auth.ts` | Authentication store: `accessToken`, `refreshToken`, `tokenExpireAt`, `user`, `firstLogin`, `menuTree`; actions: `setLogin`, `setUser`, `setTokens`, `clearAuth`; also exports `restoreLoginState()` to read from sessionStorage/localStorage |
| `user.ts` | Current user store (legacy/alternative): `currentUser`, `setCurrentUser`, `hasRole()`; simpler shape used in some pages alongside `auth.ts` |
| `theme.ts` | Sidebar theme store: `sidebarTheme` ('light'|'dark'), `toggleTheme()`; persists to localStorage and syncs `data-sidebar-theme` attribute on `<html>` |
| `breadcrumb.ts` | Dynamic breadcrumb label overrides: `overrides` map (path → display name), `setOverrides()`, `clearOverride()`, `clearAll()` |

## For AI Agents

### Working In This Directory
- Import stores with the React hook form: `const token = useAuthStore((s) => s.accessToken)`
- For imperative access outside React (e.g. in interceptors): `useAuthStore.getState().clearAuth()`
- `auth.ts` is the authoritative token source — token state lives in sessionStorage (access) and localStorage (refresh, expireAt), mirrored into the Zustand store on load
- `user.ts` and `auth.ts` serve overlapping purposes; `auth.ts` is preferred for new code — `user.ts` exists for backward compatibility
- Never store sensitive values in the store beyond what's already there (tokens are only stored in Web Storage, not global JS state like Redux DevTools can expose)

### Common Patterns
```ts
// Reading in a component
const user = useAuthStore((s) => s.user);

// Imperative mutation (interceptors, utilities)
useAuthStore.getState().setTokens(accessToken, refreshToken, expiresIn);

// Dynamic breadcrumb for a detail page
useBreadcrumbStore.getState().setOverrides({ [location.pathname]: employeeName });
```

### Testing Requirements
- Zustand stores can be tested without a React component; reset between tests with `useAuthStore.setState({ ... })`

## Dependencies

### Internal
- `auth.ts` is consumed by `src/access.ts`, `src/app.tsx`, `src/utils/token.ts`, `src/layouts/index.tsx`
- `theme.ts` is consumed by `src/layouts/index.tsx`
- `breadcrumb.ts` is consumed by `src/layouts/index.tsx` and detail pages

### External
- `zustand` — `create` factory

<!-- MANUAL: -->
