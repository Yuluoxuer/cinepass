<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# components

## Purpose
Shared, reusable presentational UI components used across multiple pages. Each component lives in its own subdirectory with co-located styles and type definitions. These components are domain-agnostic infrastructure pieces — they accept data via props and emit events; they do not fetch data or read from global stores.

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `FilterBar/` | Generic search/filter bar with a configurable grid of labelled fields and Search/Reset buttons (see `FilterBar/AGENTS.md`) |
| `PageHeader/` | Page title with a blue accent bar and optional description paragraph (see `PageHeader/AGENTS.md`) |
| `StatusTag/` | Ant Design Tag that maps backend status codes to localised labels and colours for HR workflow modules (see `StatusTag/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Each component has its own subdirectory; create a new subdirectory for each new component
- Co-locate styles as `index.less` (CSS Modules via Umi) and type definitions as `interface.ts`
- Export the default component from `index.tsx`; re-export public types from the same file if needed
- Import a component with `import FilterBar from '@/components/FilterBar'`

### Common Patterns
- Props interfaces are defined in `interface.ts` and re-exported from `index.tsx`
- CSS Modules: `import styles from './index.less'`, then `className={styles.root}`
- Components do not call `useAuthStore`, `useThemeStore`, or any global store directly

### Testing Requirements
- No tests exist yet; use React Testing Library when adding tests

## Dependencies

### Internal
- Components are consumed by `src/layouts/` and `src/pages/**`

### External
- `antd` — UI primitives (Button, Tag, Space, etc.)
- `@ant-design/icons` — Icon components

<!-- MANUAL: -->
