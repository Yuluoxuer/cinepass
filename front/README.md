# 妙语购票前端

Umi 4 + React + TypeScript + Zustand + Ant Design。

## 快速开始

先启动后端（默认 `http://localhost:8080`），再启动前端：

```bash
pnpm install
pnpm dev
```

请求走 `/api/v1`，由开发代理转发到后端 `8080`。资源缺失或读接口 404 时页面用灰色基本形状占位，不依赖 Mock。

## 演示账号

以后端种子/环境中的账号为准（常见演示账号）：

| 账号 | 密码 | 角色 |
|------|------|------|
| 演示用户甲 | demo123456 | 用户 |
| 运营小王 | demo123456 | staff |
| 系统管理员 | demo123456 | admin |

## 目录要点

- `src/api/` — 对接后端契约的 API（页面只调这里）
- `src/pages/` — C 端 / H5 / 管理端页面
- `src/agent/` — Agent Drawer 与动态卡片
- `src/components/BlankPlaceholder` — 读失败/空数据时的灰色形状占位

## 路由

- C 端：`/`、`/movies`、`/booking/*`、`/me`、`/agent`
- H5：`/m/pay/:orderId?t=`
- 管理端：`/admin/*`
