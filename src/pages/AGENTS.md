<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# pages

## Purpose
Page-level route components, each in its own subdirectory matching its route path. Currently a placeholder — business pages are intentionally omitted from this template. The infrastructure pages (`system/`) for login, user management, role management, and audit logs will live here once implemented.

## For AI Agents

### Working In This Directory
- Create one subdirectory per route segment, mirroring the URL path (e.g. `pages/system/users/index.tsx` → route `/system/users`)
- Umi auto-discovers routes from the file system; a file named `index.tsx` in a subdirectory is the page component for that route
- Shared types for a page group go in a `types.ts` file within the group (e.g. `pages/system/types.ts`) — the existing services (`roles.ts`, `users.ts`, `audit.ts`) already import from `@/pages/system/types`
- Page components fetch their own data via `@/services/*` — do not put fetch logic directly in layout or component files

### Common Patterns
- Typical page structure: `PageHeader` → `FilterBar` (if list page) → Ant Design `Table` or form
- Use `useBreadcrumbStore.setOverrides()` in a `useEffect` on detail pages to replace the ID segment with a human-readable name
- Access-guard a page: wrap with `<Access accessible={access.canManageUsers} fallback={<Navigate to="/403" />}>`

<!-- MANUAL: -->
