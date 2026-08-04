<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-31 | Updated: 2026-07-31 -->

# cinepass_leijieming_frontend（购票前端）

## Purpose
基于 UmiJS 4 + Ant Design 6 + Zustand + TypeScript 的电影购票 Web 前端。提供传统页面式购票流（选电影→选场次→选座→支付→出票）与 AI Agent 对话购票双入口，包含完整的认证、路由与真实后端 API 集成。

## Key Files

| File | Description |
|------|-------------|
| `package.json` | 依赖（umi 4.6, antd 6.5, zustand 5, react-markdown 10）与 pnpm 脚本 |
| `tsconfig.json` | TypeScript 配置 |
| `typings.d.ts` | Umi 全局类型扩展（`import 'umi/typings'`） |
| `pnpm-lock.yaml` | 锁定依赖版本（使用 pnpm） |
| `pnpm-workspace.yaml` | pnpm workspace 配置 |
| `README.md` | 中文项目说明与目录结构 |
| `README.en.md` | 英文项目说明 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/` | 全部应用源码（见 `src/AGENTS.md`） |
| `public/` | 静态资源（favicon 等） |

## For AI Agents

### Working In This Directory
- **包管理器**：只用 `pnpm`，不用 `npm` 或 `yarn`；添加依赖前需询问用户
- **启动开发服务**：`pnpm dev`（默认端口 8000，`/api` 代理到后端 8080；需先启动后端）
- **构建**：`pnpm build`，产物在 `dist/`
- **后端依赖**：所有业务请求直连真实 `/api/v1`；读接口 404/空数据用 `BlankPlaceholder` 灰色形状占位
- `.umi` 和 `.umi-production` 目录是 Umi 自动生成的，**不要手动编辑**
- Path alias `@/` 解析到 `src/`

### Testing Requirements
- 构建验证：`pnpm build`（TypeScript 编译 + bundle）
- E2E：`playwright`（已列为 devDependency，测试文件待建立）

### Common Patterns
- 路由：UmiJS 文件系统约定路由（`src/pages/` 目录结构即路由）
- 状态管理：Zustand store（`src/stores/`）
- API 层：`src/api/`（axios 封装与业务方法）
- 后端响应信封：`{ code, message, data }` — 拦截器自动解包 `.data`
- 公共组件：`src/components/`；业务特性：`src/features/`（如 `seatmap`）

## Dependencies

### External
- `umi` 4.6.74 — 框架（路由、构建、约定）
- `antd` 6.5.0 — UI 组件库
- `zustand` 5.0.14 — 全局状态管理
- `react-markdown` 10 + `remark-gfm` — Agent 回复 Markdown 渲染
- `axios` 1.18.1 — HTTP 客户端
- `dayjs` 1.11.21 — 日期处理（新代码优先使用 dayjs，不用 moment）
- `@antv/g2plot` 2.4.35 — 数据图表
- `ahooks` ^3.9.7 — React hooks 工具库

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
