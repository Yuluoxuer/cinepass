<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# leijieming_frontend

## Purpose
Admin panel frontend scaffold for HR management systems (人员入转调离). Built with Umi 4, Ant Design 6, Zustand, and TypeScript. Provides complete infrastructure for authentication, routing, API integration, and the application shell. Business pages are intentionally left as placeholders — the template preserves all infrastructure while business-specific modules are added per project.

## Key Files

| File | Description |
|------|-------------|
| `package.json` | Project dependencies and pnpm scripts (`dev`, `build`, `start`, `postinstall`) |
| `tsconfig.json` | TypeScript configuration |
| `typings.d.ts` | Umi global type augmentation (`import 'umi/typings'`) |
| `pnpm-lock.yaml` | Locked dependency versions (pnpm) |
| `pnpm-workspace.yaml` | pnpm workspace configuration |
| `README.md` | Project overview, directory structure, and quick-start guide |
| `README.en.md` | English version of the README |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `public/` | Static assets served at web root (see `public/AGENTS.md`) |
| `src/` | All application source code (see `src/AGENTS.md`) |

## For AI Agents

### Working In This Directory
- Use `pnpm` for all package operations — do not use `npm` or `yarn`
- Ask the user before installing new dependencies
- Build tool is Umi 4; run `pnpm dev` to start the dev server (proxies `/api` → `http://localhost:8080`)
- Do not edit `pnpm-lock.yaml` directly
- The `.umi` and `.umi-production` directories inside `src/` are Umi-generated — never edit them manually
- The `.omc/` directory is tooling state — ignore it

### Testing Requirements
- No test framework is set up yet; `playwright` is listed as a devDependency but no test files exist

### Common Patterns
- Path alias `@/` resolves to `src/`
- All API calls use the `/api` prefix (proxied to the backend in dev)
- Backend response envelope: `{ code: number, message: string, data: T }` — request interceptors unwrap `.data` automatically

## Dependencies

### External
- `umi ^4.6.74` — React meta-framework (routing, build, Umi plugins)
- `antd 6.5.0` — UI component library
- `@ant-design/icons 6.3.2` — Icon set
- `zustand 5.0.14` — Lightweight global state management
- `axios 1.18.1` — HTTP client (used in `src/services/request.ts` and `src/utils/request.ts`)
- `react-router-dom 7.18.1` — Routing (consumed via Umi)
- `dayjs 1.11.21` — Date utilities
- `@antv/g2plot 2.4.35` — Chart/visualization library
- `react-markdown ^10.1.0` + `remark-gfm ^4.0.1` — Markdown rendering
- `ahooks ^3.9.7` — React hooks utility library
- `lodash 4.18.1` — General utility functions
- `moment 2.30.1` — Legacy date library (prefer dayjs for new code)

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
