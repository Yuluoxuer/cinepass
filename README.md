# 妙语购票前端

Umi 4 + React + TypeScript + Zustand + Ant Design。

## 快速开始

```bash
pnpm install
pnpm dev
```

默认开启 **Mock**（右下角 `Mock ON`）。关闭后请求走 `/api/v1` → 代理 `http://localhost:8080`。

## 演示账号

| 账号 | 密码 | 角色 |
|------|------|------|
| 演示用户甲 | demo123456 | 用户 |
| 运营小王 | demo123456 | staff |
| 系统管理员 | demo123456 | admin |

## 目录要点

- `src/api/` — 对接后端契约的 API（页面只调这里）
- `src/mock/` — Mock 数据与路由（独立目录）
- `src/pages/` — C 端 / H5 / 管理端页面
- `src/agent/` — Agent Drawer 与动态卡片
- `src/components/MockToggle` — Mock 开关

## 路由

- C 端：`/`、`/movies`、`/booking/*`、`/me`、`/agent`
- H5：`/m/pay/:orderId?t=`
- 管理端：`/admin/*`
