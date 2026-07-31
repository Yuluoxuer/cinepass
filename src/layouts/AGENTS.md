<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# layouts

## Purpose
Root application shell rendered for all authenticated routes. Implements a collapsible Ant Design sidebar (Sider), a top header bar with user avatar and theme toggle, dynamic breadcrumbs, and an `<Outlet />` content area. The layout fetches the menu tree from `/api/menus/tree` on mount and falls back to the static `MENU_ITEMS` constant if the API fails.

## Key Files

| File | Description |
|------|-------------|
| `index.tsx` | Main `AppLayout` component — sidebar, header, breadcrumb, session-checking spinner, idle restore logic |
| `index.less` | CSS Modules styles for all layout zones: `.sider`, `.header`, `.menu`, `.content`, dark-theme overrides via `[data-sidebar-theme="dark"]` |
| `constant.tsx` | Static fallback `MENU_ITEMS` array and `BREADCRUMB_NAME_MAP` path→label map |
| `interface.ts` | `MenuItem` type shared between `constant.tsx` and `index.tsx` |

## For AI Agents

### Working In This Directory
- `AppLayout` is registered as the Umi layout in `.umirc.ts` — do not rename or move the default export
- The dark sidebar theme is applied via `document.documentElement.setAttribute('data-sidebar-theme', 'dark')` controlled by `useThemeStore`; CSS in `index.less` selects on this attribute
- The menu is loaded from `/api/menus/tree`; the icon name string (e.g. `"DashboardOutlined"`) is resolved via the `iconMap` object in `index.tsx` — add new icons there
- Breadcrumb labels are looked up first in `useBreadcrumbStore.overrides` (dynamic, set by pages for ID-based routes), then in `BREADCRUMB_NAME_MAP`, then fall back to the raw URL segment
- The session-checking spinner covers the full viewport until `isSessionReady()` returns true — avoid adding logic that depends on the user before this resolves

### Common Patterns
- Add a new top-level menu item: extend `MENU_ITEMS` in `constant.tsx` and add the icon to `iconMap` in `index.tsx`
- Override a breadcrumb for a detail page: `useBreadcrumbStore.getState().setOverrides({ '/employee/123': '张三' })`

### Testing Requirements
- Integration-test session restore and redirect logic; mock `isSessionReady` and `restoreSession` from `@/utils/token`

## Dependencies

### Internal
- `@/stores/auth` — access token and user info
- `@/stores/theme` — sidebar theme toggle
- `@/stores/breadcrumb` — dynamic breadcrumb label overrides
- `@/utils/token` — `isSessionReady`, `restoreSession`, `getSessionRestorePromise`

### External
- `antd` — Layout, Menu, Breadcrumb, Avatar, Dropdown, Spin, Tooltip, Button
- `@ant-design/icons` — sidebar and header icons
- `umi` — `Outlet`, `useLocation`, `useNavigate`, `request`

<!-- MANUAL: -->
