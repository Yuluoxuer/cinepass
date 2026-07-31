<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# StatusTag

## Purpose
Renders an Ant Design `Tag` that maps a backend status code string to a human-readable Chinese label and a semantic colour. Covers the four HR workflow modules: onboarding (入职), probation (试用期), transfer (调岗), and resignation (离职).

## Key Files

| File | Description |
|------|-------------|
| `index.tsx` | `StatusTag` default export; exports `StatusModule` type; contains the `STATUS_MAP` lookup table |

## For AI Agents

### Working In This Directory
- Pass the backend status code exactly as returned (lowercase English, e.g. `"pending_approval"`)
- `module` must be one of `'onboarding' | 'probation' | 'transfer' | 'resignation'`
- Use the `label` prop to override the display text while keeping the module colour
- If a status code is unknown, the component falls back to rendering the raw code string in a default Tag

### Extending STATUS_MAP
Add new status entries inside the appropriate module key in `STATUS_MAP`. Colour palette:
- `#9CA3AF` — neutral / draft / pending-initiate
- `#4C81E8` — in-progress / pending approval
- `#FA8C16` — warning / pending action
- `#52C41A` — success / positive outcome
- `#1B7340` — strong success (e.g. onboarded)
- `#FF4D4F` — rejected / abandoned
- `#8C8C8C` — withdrawn / inactive

### Common Patterns
```tsx
import StatusTag, { type StatusModule } from '@/components/StatusTag';

// In a Table column render function:
render: (status: string) => (
  <StatusTag status={status} module="onboarding" />
)
```

### Testing Requirements
- Known status code renders expected label and colour; unknown code renders raw string as fallback

## Dependencies

### External
- `antd` — `Tag`

<!-- MANUAL: -->
