<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# FilterBar

## Purpose
Generic search/filter bar for list pages. Renders a CSS Grid of labelled form fields with configurable column count, plus a button row: a left slot for custom action buttons (New, Import…) and a fixed right side with Reset and Search buttons.

## Key Files

| File | Description |
|------|-------------|
| `index.tsx` | `FilterBar` default export; re-exports `FilterCell` and `FilterBarProps` types |
| `interface.ts` | `FilterCell` (label + children + optional span) and `FilterBarProps` interfaces |
| `index.less` | CSS Modules: `.root`, `.filterGrid`, `.cols1–4`, `.filterCell`, `.filterLabel`, `.filterControl`, `.filterActions` |

## For AI Agents

### Working In This Directory
- Pass any Ant Design form control (Select, Input, DatePicker…) as `field.children` — FilterBar is uncontrolled; the parent page owns the form state
- Use `field.span` to span multiple columns (e.g. a RangePicker at `span: 2`)
- `columns` prop: `1 | 2 | 3 | 4` (default `4`)
- `actions` prop renders in the left of the button row — use for New / Export buttons
- Returns `null` when `fields` is empty — safe to render unconditionally

### Common Patterns
```tsx
import FilterBar from '@/components/FilterBar';

<FilterBar
  columns={4}
  fields={[
    { label: '姓名', children: <Input value={name} onChange={e => setName(e.target.value)} /> },
    { label: '部门', children: <Select options={deptOptions} value={dept} onChange={setDept} /> },
    { label: '日期', span: 2, children: <RangePicker value={range} onChange={setRange} /> },
  ]}
  actions={<Button type="primary" onClick={handleCreate}>新增</Button>}
  onSearch={handleSearch}
  onReset={handleReset}
/>
```

### Testing Requirements
- Clicking Reset calls `onReset`; clicking Search calls `onSearch`
- `fields=[]` renders nothing (null)

## Dependencies

### External
- `antd` — `Button`, `Space`
- `@ant-design/icons` — `SearchOutlined`, `ReloadOutlined`

<!-- MANUAL: -->
