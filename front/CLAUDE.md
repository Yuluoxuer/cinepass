# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

妙语购票（电影购票 Web 前端）：UmiJS 4 + React 18 + TypeScript + Zustand 5 + Ant Design 6。提供传统页面式购票流（选片→选院→选场→选座→支付→出票）与 AI Agent 对话购票双入口，另有 H5 扫码支付/核销与管理端运营后台。

所有业务请求直连真实后端 `/api/v1`（无 Mock）；后端默认 `http://localhost:8080`。

## 常用命令（包管理器只用 pnpm）

```bash
pnpm install        # 安装依赖；postinstall 自动跑 umi setup 生成 src/.umi
pnpm dev            # 启动开发服务（默认端口 8000）；需先启动后端，/api 代理到 8080
pnpm build          # 生产构建，产物在 dist/
pnpm setup          # 重新生成 Umi 运行时（src/.umi）
```

- 无测试运行器、无 lint 脚本。`playwright` 已列为 devDependency 但测试文件待建。
- `src/.umi`、`src/.umi-production` 是 Umi 自动生成目录，**不要手动编辑**。
- Path alias `@/` 解析到 `src/`，一律用 `@/` 导入而非相对路径。

## 路由（配置式，定义于 `.umirc.ts`，非文件约定）

- C 端：`/`、`/movies`、`/movies/:movieId`、`/cinemas`、`/booking/*`、`/me/*`、`/search`、`/agent`，外层 `ClientLayout`（含 SiteHeader、LoginModal、AgentDrawer）
- H5（`layout: false`，扫码进入）：`/m/pay/:orderId`、`/m/redeem/:orderId`
- 管理端：`/admin/login`（独立登录页）+ `/admin/*` 外层 `AdminLayout`（dashboard、movies、cinemas、halls、seat-maps、shows、orders、tickets/verify、users）

代理：`/api` 与 `/uploads` → 后端 8080；`/amap-api` → 高德 REST API（pathRewrite 去掉前缀）。

## 架构与分层

请求流：页面 → `src/api/*`（唯一对接后端契约的层）→ `src/api/client.ts`（axios 封装）。

**核心机制：响应信封 `{ code, message, data }`**，`client.ts` 拦截器统一解包 `.data` 并把失败转成 `ApiError`。页面只调 `get/post/put/del`（`src/api/client.ts`），不自行判断 HTTP/业务错误码；GET 404 默认静默，页面用 `BlankPlaceholder` 灰色形状占位。

错误处理集中在 `src/api/error.ts` + `src/app.tsx`：
- 拦截器统一 `presentApiError` 弹提示、`redirectToRequestError` 跳可恢复错误页（仅 5xx/网络故障跳页）
- 401 → 管理端跳 `/admin/login`，C 端弹 LoginModal（`stores/auth` 的 `openLoginModal`）
- `app.tsx` 注册 `unhandledrejection` 兜底拦截未 catch 的 `ApiError`，避免 dev 红屏

**状态（Zustand）**：
- `stores/auth.ts` — token/user 持久化到 localStorage（`miaoyu_access_token` / `miaoyu_user` / `miaoyu_token_expire_at`），组件外取 `useAuthStore.getState()`，`restoreLoginState()` 启动时恢复
- `stores/booking.ts` — 购票草稿（Draft）状态机。Draft 以**服务端为准**（sessionId 存 `miaoyu_sessionId`），带 `version` 做 CAS；`patchLocal` 乐观更新 + 300ms 防抖，冲突时以服务端状态重放；`rollbackDependent` 在回退选片/选院等步骤时级联清空下游字段
- `stores/agent.ts` — Agent 对话状态；打开 Drawer 时 `setAgentPaused(true)`，消息/卡片通过 `/agent/turns` 与服务端交互，`applyTurn` 回写 Draft

**购票步骤推导**：`src/utils/bookingProgress.ts` 的 `firstIncompleteStep` 按 Draft 字段完备度（movieId→cinemaId→showId→lockId→orderId）决定当前焦点步，页面与 Agent 共用，不要只信 draft.state 字符串。

**AI Agent**：`src/agent/`（AgentDrawer + CardRenderer 渲染服务端返回的动态卡片 `AgentCardVO`），`src/api/agent.ts` 仅一个 `postTurn`。类型见 `types/index.ts` 的 `AgentTurnRequest/Response`。

**类型**：`src/types/index.ts` 与后端 VO/DTO 对齐（`ApiEnvelope`、`ApiError`、`BookingDraft`、`MovieVO`、`SeatMapVO`、`OrderVO` 等）。

**其他**：`src/features/seatmap/SeatMap.tsx` 是座位图渲染 + `useLockCountdown` 锁座倒计时；`src/utils/lanIp.ts` 配合 `.umirc.ts` 注入的 `__LAN_IP__` 生成手机可访问的扫码地址（不能用 localhost）。

## 常用 localStorage 键

| 键 | 用途 |
|----|------|
| `miaoyu_access_token` | JWT（三段式；启动时若非三段式会清除） |
| `miaoyu_sessionId` | 购票 Draft sessionId |
| `miaoyu_city_id` | 当前城市，请求头 `X-City-Id`（默认 `city_sh`） |

## 注意

- `AGENTS.md`（含各目录子文件）为自动生成，部分描述已过时（如 `utils/token.ts`、`utils/request.ts`、`stores/theme.ts` 已不存在；路由实为配置式而非文件约定）。以本文件为准。
- 高德 Key、演示账号等见 `README.md` 与 `src/constants/index.ts`。
