<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# PageHeader

## Purpose
Standardised page-title block placed at the top of every content page. Renders a bold title with a 3 px blue left-border accent bar and an optional muted description paragraph below.

## Key Files

| File | Description |
|------|-------------|
| `index.tsx` | `PageHeader` default export with inline `PageHeaderProps` interface (`title`, optional `description`) |
| `index.less` | CSS Modules: `.root` (bottom margin), `.title` (flex + 18 px bold), `.bar` (blue accent), `.desc` (muted 13 px text) |

## For AI Agents

### Working In This Directory
- Place `<PageHeader>` as the first element inside a page component, before `FilterBar` or any table
- `description` is optional — omit when the title is self-explanatory
- Do not add interactive elements (buttons, dropdowns) here — use the page layout directly above the FilterBar for those

### Common Patterns
```tsx
import PageHeader from '@/components/PageHeader';

<PageHeader title="角色管理" description="管理系统角色与权限分配" />
```

### Testing Requirements
- Renders `title` text; `description` paragraph is conditionally rendered only when the prop is provided

<!-- MANUAL: -->
