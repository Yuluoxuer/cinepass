# 妙语购票 — 后端系分（票务中台与 Agent）

| 字段 | 内容 |
|------|------|
| 文档版本 | v4.8.0 |
| 状态 | Draft |
| 创建日期 | 2026-07-27 |
| 修订日期 | 2026-08-02 |
| 依据 | PRD v3.8 §3.3 / §10 / §11 / §12 |
| 关联前端 | `02-前端系分-购票UI与Agent壳.md` **v3.1.0**；对照审查 `03-前后端系分对照审查.md` |
| 修订说明 | **v4.8.0：**（1）Agent：补齐 **LangGraph 子 Agent 路由**、**Chroma RAG 切片/入库/检索**落地规格（§3.3 / §5.9 / §5.13）；（2）**STAFF 影院隔离**——`user_account.cinema_id`；staff 仅本影院厅/场/座/单，**不可新建影院**；影片目录仍可维护。前版 v4.7.0 Agent 自有库。 |
| 关联 Agent | **Python LangChain/LangGraph 独立服务**（本文 §5.13；对话落库在 Agent DB） |
| 说明 | 本文覆盖 **Java 票务中台** + **与 Agent 服务的集成**；v4.4 Agent 迁 Python；v4.7 Agent 对话库与中台分离；v4.8 staff 范围 + RAG/子 Agent 实现细规 |

## 目录

1. [需求背景与目标](#1-需求背景与目标)
2. [技术栈与分层](#2-技术栈与分层)
3. [模块划分与子 Agent](#3-模块划分与子-agent)
4. [BookingDraft 与共享状态机](#4-bookingdraft-与共享状态机)
5. [Agent Core 详细设计](#5-agent-core-详细设计重点)
6. [数据库设计与 ER](#6-数据库设计与-er)
7. [API 接口详细设计](#7-api-接口详细设计)
8. [流程 / UML / 时序图](#8-流程--uml--时序图)
9. [Agent 与中台的模块边界](#9-agent-与中台的模块边界)
10. [错误处理](#10-错误处理)
11. [非功能与可观测](#11-非功能与可观测)
12. [测试计划（后端）](#12-测试计划后端)
13. [里程碑映射](#13-里程碑映射)
14. [分工与时间安排](#14-分工与时间安排)

---

## 1. 需求背景与目标

票务中台（**Java**）提供影片/影院/场次/座位/锁座/订单/模拟支付/Draft 的**唯一真相源**；**Agent 服务（Python + LangChain）** 为独立进程，通过 HTTP 调用中台 REST 完成对话购票。普通流程与 Agent **共用同一套中台 API**，禁止第二套库存。

### 1.1 后端目标

| ID | 目标 | 验收 |
|----|------|------|
| B-G1 | 中台 API 支撑手动闭环 | 不经 Agent 可锁座出票 |
| B-G2 | Agent 一轮对话 P95 < 3s | 含规划与 Tool 调用 |
| B-G3 | 锁座原子防超卖 | 并发仅一人成功；TTL=15min |
| B-G4 | Agent 禁止静默支付 | Tools 不暴露自动 pay；pay 需用户会话 + 显式 API |
| B-G5 | 推荐不依赖 LLM | 热门/个人/座位打分纯算法 |
| B-G6 | 模拟支付二维码闭环 | PC 展示支付 QR → 手机扫码确认 → 出票生成签名取票 QR |
| B-G7 | Agent 与中台解耦 | Agent 为 Python 独立服务；中台不嵌入 LangChain；Tool 仅调 REST |

---

## 2. 技术栈与分层（双服务）

### 2.0 部署单元

| 服务 | 技术 | 职责 |
|------|------|------|
| **ticket-api**（本文主体） | Spring Boot 2.7 / Java 8 | 票务中台 REST、DB、锁座/订单/支付、Draft 存储、JWT |
| **ticket-agent**（独立） | Python 3.11+ / **LangChain** / FastAPI（建议） | `POST /agent/turns`、NLP/RAG、LangChain Tools → 回调 ticket-api |

```text
浏览器 ──► ticket-agent (Python)     ──HTTP Tools──► ticket-api (Java)
         └─► ticket-api (Java)  直接购票 / 支付 / Draft hydrate（不经 Agent）
```

**网关（推荐）：** Nginx / Gateway 按路径分流：`/api/v1/agent/**` → ticket-agent；其余 → ticket-api。

### 2.1 票务中台（Java）技术栈

| 层 | 技术 |
|----|------|
| 框架 | Spring Boot 2.7 / Java 8 |
| API | REST + JSON |
| 持久化 | PostgreSQL 16 + MyBatis + Flyway |
| 锁座 TTL | Redis SET + expire |
| 校验 | Bean Validation |
| 测试 | JUnit5 + Testcontainers |

**不含：** LangChain / LLM SDK（均在 Python Agent 服务）。

**连接示例（`application.yml`）：**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/miaoyu_ticket
    username: ticket
    password: ${DB_PASSWORD}
  flyway:
    enabled: true

ticket:
  agent-service-url: ${AGENT_SERVICE_URL:http://localhost:8000}  # 可选：Java BFF 转发 Agent 时用
  public-base-url: https://ticket.example.com
  pay-qr-secret: ${PAY_QR_SECRET}
  qr-secret: ${TICKET_QR_SECRET}
```

驱动：`org.postgresql:postgresql`；迁移脚本放 `src/main/resources/db/migration`（§6.3 DDL）。

### 2.2 Agent 服务（Python）技术栈（摘要，详见 §5.13）

| 层 | 技术 |
|----|------|
| 框架 | FastAPI + Uvicorn |
| Agent | **LangChain** + **LangGraph**（确定性 Router → 子 Agent Tools） |
| Tools | LangChain `@tool` / StructuredTool，内部 **httpx** 调 ticket-api |
| 记忆 | 三层：中台 Draft + Agent DB `agent_message` + LangGraph **PostgresSaver**（§5.13.5） |
| RAG | **Chroma** + Embeddings；仅 FAQ/政策；切片见 §5.9 |
| 配置 | `TICKET_API_*`, `DATABASE_URL`, `CHROMA_*`, `OPENAI_*` / `LLM_*`；超时与 §5.10 对齐 |

### 2.3 包结构（Hexagonal，Java 票务中台）

```text
com.miaoyu.ticket
├── domain/
│   ├── cinema/           # 影院 / 厅 / 布局
│   ├── movie/            # 影片
│   ├── show/             # 场次
│   ├── seat/             # 座位图、SeatStatus、SeatLock
│   ├── payment/          # 模拟支付领域规则（无静默）
│   ├── order/            # Order 聚合、状态机
│   ├── user/             # 账号 / 画像 / Token
│   ├── recommend/        # HotScore / PersonalScore / SeatScorer
│   └── booking/          # BookingState, BookingDraft（持久化用例；无 LangChain）
├── application/
│   ├── cinema/           # SearchCinemas, GetCinema
│   ├── movie/            # SearchMovies, GetMovie, WantSee
│   ├── show/             # ListShows, GetShow
│   ├── seat/             # GetSeatMap, LockSeats, UnlockSeats
│   ├── payment/          # MockPay, GetPayQr, GetPaySession, VerifyTicket
│   ├── order/            # CreateOrder, CancelOrder, GetOrder
│   ├── user/             # Login, Me, Profile
│   ├── recommend/        # WeeklyHot, PersonalReco, RecommendSeats
│   └── booking/          # GetDraft, PatchDraft, AppendAgentMessages
├── adapter/
│   ├── web/              # REST Controllers（业务 + Draft；**不含** Agent Turn 实现）
│   ├── persistence/
│   ├── redis/
│   ├── http/             # OutboundHttpClient（Agent 服务若走 Java BFF 转发）
│   └── security/         # JWT（用户 RBAC；无 Agent M2M Key）
└── bootstrap/
```

依赖方向：**adapter → application → domain**。

> **v4.4：** `ProcessAgentTurn`、LangChain、Tool 编排 **不在 Java 工程**；位于 **ticket-agent（Python）**。Java 仅暴露 §3.2 各 Tool 对应的 REST，供 Python 回调。

### 2.4 API 契约约定（入参 / 出参）

| 约定 | 说明 |
|------|------|
| 协议 | REST + **JSON**；请求 Body / 成功 `data` / 错误 `data` 均为 JSON |
| 包络 | Spring Boot 统一 `{ code, message, data, traceId }`；成功 **200**（`ResponseBodyAdvice`），失败 **-1**（`@RestControllerAdvice`，见 §7.0.1） |
| 文档写法 | 每个接口同时给出 **入参 JSON** 与 **出参 JSON**（Query/Path 亦给出等价 JSON 示意） |
| 命名 | **DTO** = 接前端入参；**VO** = 返前端出参（见 §7.0.5） |
| 幂等 | 写接口支持 `Idempotency-Key`（见 §5.11）；支付额外禁静默 |

---

## 3. 模块划分与子 Agent

票务中台按业务切模块；Agent Core 编排，领域检索/查询由**子 Agent** 封装为 Tools。子 Agent **不持有库存真相**，一律调用对应模块 Use Case。

### 3.1 模块总览

```text
┌──────────────────────────────────────────────────────────────────────┐
│                         Agent Core（编排）                             │
│  RAG · 意图/槽位 · Planner · 网络治理 · 幂等 · 禁静默支付              │
│         │                                                            │
│  ┌──────┴──────┬──────────┬──────────┬──────────┬──────────┐         │
│  ▼             ▼          ▼          ▼          ▼          ▼         │
│ CinemaAgent  MovieAgent  ShowAgent  SeatAgent  OrderAgent (无 PayTool)│
└──────┬───────────┬──────────┬──────────┬──────────┬──────────┘         │
       ▼           ▼          ▼          ▼          ▼                   │
┌─影院管理─┐ ┌─影片管理─┐ ┌─场次管理─┐ ┌─座位管理─┐ ┌─订单管理─┐ ┌─支付管理─┐
│ Cinema  │ │ Movie   │ │ Show    │ │ Seat    │ │ Order   │ │ Payment │
└─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └────▲────┘
                                                                  │
                         仅用户显式 REST POST /orders/{id}/pay ────┘
┌─用户管理─┐ ┌─热门推荐系统─┐
│ User    │ │ Recommend   │ ← 纯算法，不经 LLM 出榜
└─────────┘ └─────────────┘
```

| 模块 | 职责 | 子 Agent | 主要 Use Case / API |
|------|------|----------|---------------------|
| 影院管理 | 影院/厅/标签；附近排序；**运营** | **CinemaAgent**（C 端只读 Tool） | `GET /cinemas`；运营 `POST /admin/cinemas`（**仅 admin**）、`PUT /admin/cinemas`（admin 任意 / **staff 仅本影院**）、`GET|PUT /admin/halls`（staff **本影院**） |
| 影片管理 | 热映待映/详情/想看；**目录 CRUD** | **MovieAgent** | `GET /movies`；运营 `POST|PUT /admin/movies`（**staff/admin**，影片为**全平台目录**） |
| 场次管理 | 排片查询/余座摘要；**运营排片** | **ShowAgent** | `GET /shows`；运营 `POST|PUT /admin/shows`（staff **本影院** / admin 全量） |
| 座位管理 | 座位图建图/推荐座/锁解锁 | **SeatAgent** | 购票 Tool；运营 `seat-maps`/`halls`（staff **本影院**） |
| 支付管理 | 模拟支付出票（二维码） | **无子 Agent**（禁止 Tool） | `GET pay-qrcode` / `GET pay-session` / `POST pay` / `GET tickets/verify` |
| 订单管理 | 创建/取消/查询；**运营协助查** | **OrderAgent** | `createOrder` / `getOrder`；运营 `GET /admin/orders`（staff **本影院场次订单**） |
| 用户管理 | 登录/我的/画像；**admin 账号** | — | `/auth/*` `/me/*`；`GET|POST|PUT /admin/users`（**admin**；创建 staff 须绑 `cinemaId`） |
| 热门推荐系统 | 周热门/个人/座位打分 | 被 Movie/Seat Agent 调用 | `/reco/*`；运营权重 **仅 admin**（§10.11） |
| **Agent 服务** | 对话编排 + LangGraph 子 Agent + RAG | 调度 Tools 回调中台 REST | **`POST /agent/turns`（Python 部署）**；实现见 **§3.3** |

### 3.2 子 Agent 契约（统一）

每个子 Agent 对外暴露 Tool：`name`、`input`（JSON Schema）、`output`（JSON）、`sideEffect`、`timeoutMs`、`idempotent`。

> **文档约定：**  
> 1. 本节入参/出参 JSON 示例使用 `json` 代码块；字段旁 `// …` 仅为文档注释。  
> 2. **Tool 入参/出参必须与 §7 对应 REST 的 Query/Path/Body 及响应 `data` 字段一致**（Tool 名是对 Use Case 的别名，不另造字段）。  
> 3. 出参 = 中台响应包络中的 **`data`**（不含外层 `code/message/traceId`）。  
> 4. 写操作幂等键统一走 Header `Idempotency-Key`（不进 Body），与 §7.0.2 / §5.11 一致。

| Tool | 中台 API | 入参对应 |
|------|----------|----------|
| `searchMovies` | `GET /movies` | Query |
| `getMovie` | `GET /movies/{movieId}` | Path |
| `recommendMovies` | `GET /reco/personal` | Query（鉴权可选） |
| `searchCinemas` | `GET /cinemas` | Query |
| `getCinema` | `GET /cinemas/{cinemaId}` | Path |
| `listShows` | `GET /shows` | Query |
| `getShow` | `GET /shows/{showId}` | Path |
| `getSeatMap` | `GET /shows/{showId}/seat-map` | Path |
| `recommendSeats` | `POST /reco/seats` | Body |
| `lockSeats` | `POST /locks` | Body + Header `Idempotency-Key` |
| `unlockSeats` | `DELETE /locks/{lockId}` | Path + Query |
| `createOrder` | `POST /orders` | Body |
| `getOrder` | `GET /orders/{orderId}` | Path |
| `cancelOrder` | `POST /orders/{orderId}/cancel` | Path + Body |
| ~~`payMock`~~ | `POST /orders/{orderId}/pay` | **禁止注册为 Tool**；仅前端 REST |

#### 3.2.1 CinemaAgent

| Tool | 副作用 | 超时 | 幂等 |
|------|--------|------|------|
| `searchCinemas` | 无 | 800ms | 是 |
| `getCinema` | 无 | 500ms | 是 |

**searchCinemas → `GET /cinemas` 入参（Query 等价 JSON）**

```json
{
  "movieId": "m100",  // 可选；有则只返回有该片排片的影院，且 minPrice 按该片计算
  "lat": 31.2989,  // 条件必填；用户纬度 WGS84；sort=distance 时必填
  "lng": 121.5140,  // 条件必填；用户经度；与 lat 成对
  "radiusMeters": 5000,  // 可选；默认 5000；只返回距离 ≤ 该值的影院
  "sort": "distance",  // 可选；默认 distance；distance | price
  "page": 1,  // 可选；默认 1
  "size": 10  // 可选；默认 20，最大 50
}
```

> 位置只接受 **经纬度**，不接受商圈名等文案。服务端用影院表 `lat`/`lng` 算直线距离。`minPrice` 取自该影院当前可售场次最低价（有 `movieId` 则限定该片），**不依赖 date**。

**searchCinemas 出参（= `data`）**

```json
{
  "items": [  // CinemaVO 列表（已按 sort 排序）
    {
      "cinemaId": "c12",  // 影院 ID
      "name": "万达影城（五角场店）",  // 影院名称
      "address": "淞沪路 77 号",  // 详细地址
      "distanceMeters": 1200,  // 相对入参 lat/lng 的直线距离（米）
      "minPrice": 45.0  // 可售场次最低票价；有 movieId 时为该片最低价
    },
    {
      "cinemaId": "c15",  // 影院 ID
      "name": "金逸影城（大学路店）",  // 影院名称
      "address": "大学路 297 号",  // 详细地址
      "distanceMeters": 2100,  // 相对入参 lat/lng 的直线距离（米）
      "minPrice": 42.0  // 可售场次最低票价；有 movieId 时为该片最低价
    }
  ],
  "page": 1,  // 当前页码
  "size": 10,  // 每页条数
  "total": 2  // 符合条件的总条数（半径内）
}
```

**getCinema → `GET /cinemas/{cinemaId}` 入参**

```json
{
  "cinemaId": "c12"  // Path；影院 ID
}
```

**getCinema 出参（= `data`，CinemaVO + 扩展）**

```json
{
  "cinemaId": "c12",  // 影院 ID
  "name": "万达影城（五角场店）",  // 影院名称
  "address": "淞沪路 77 号",  // 详细地址
  "distanceMeters": null,  // 详情未带用户坐标时为 null
  "minPrice": 45.0,  // 该影院当前可售场次最低票价
  "trafficNote": "地铁 10 号线五角场站",  // 交通提示（详情扩展）
  "halls": [  // 下属影厅列表（详情扩展）
    { "hallId": "h1", "name": "1号厅" }  // hallId：影厅 ID；name：影厅名称
  ]
}
```

#### 3.2.2 MovieAgent

| Tool | 副作用 | 超时 | 幂等 |
|------|--------|------|------|
| `searchMovies` | 无 | 800ms | 是 |
| `getMovie` | 无 | 500ms | 是 |
| `recommendMovies` | 无 | 1000ms | 是 |

**searchMovies → `GET /movies` 入参（Query 等价 JSON）**

```json
{
  "status": "hot_showing",  // 可选；默认 hot_showing；取值 hot_showing | coming_soon
  "q": null,  // 可选；片名模糊搜索关键字，最长 64；null 表示不按名搜
  "genre": "喜剧",  // 可选；类型包含匹配，如喜剧/科幻
  "page": 1,  // 可选；页码，从 1 起，默认 1
  "size": 10  // 可选；每页条数，默认 20，最大 50
}
```

**searchMovies 出参（= `data`）**

```json
{
  "items": [  // MovieVO 列表
    {
      "movieId": "m201",  // 影片 ID
      "title": "年会不能停！",  // 片名
      "posterUrl": "https://cdn.example/m201.jpg",  // 海报 URL
      "genres": ["喜剧"],  // 类型标签
      "rating": 8.2,  // 评分；无评分可为 null
      "durationMin": 118,  // 片长（分钟）
      "releaseDate": "2026-01-10",  // 上映日 YYYY-MM-DD
      "status": "hot_showing",  // 热映/待映/下架：hot_showing | coming_soon | off
      "description": "打工人逆袭……",  // 简介（列表可截断）
      "cast": "大鹏 / 白客",  // 主演文案
      "wantSeeCount": 5600  // 想看人数
    },
    {
      "movieId": "m202",  // 影片 ID
      "title": "热辣滚烫",  // 片名
      "posterUrl": "https://cdn.example/m202.jpg",  // 海报 URL
      "genres": ["喜剧"],  // 类型标签
      "rating": 8.0,  // 评分
      "durationMin": 133,  // 片长（分钟）
      "releaseDate": "2026-02-10",  // 上映日
      "status": "hot_showing",  // 上映状态
      "description": "减肥逆袭……",  // 简介
      "cast": "贾玲 / 雷佳音",  // 主演文案
      "wantSeeCount": 4200  // 想看人数
    }
  ],
  "page": 1,  // 当前页码
  "size": 10,  // 每页条数
  "total": 2  // 符合条件的总条数
}
```

**getMovie → `GET /movies/{movieId}` 入参**

```json
{
  "movieId": "m100"  // Path；影片 ID
}
```

**getMovie 出参（= `data`，MovieVO；详情 `description`/`cast` 完整）**

```json
{
  "movieId": "m100",  // 影片 ID
  "title": "流浪地球 3",  // 片名
  "posterUrl": "https://cdn.example/m100.jpg",  // 海报 URL
  "genres": ["科幻", "冒险"],  // 类型标签
  "rating": 9.1,  // 评分
  "durationMin": 173,  // 片长（分钟）
  "releaseDate": "2026-02-01",  // 上映日
  "status": "hot_showing",  // 上映状态
  "description": "太阳危急，联合政府启动移山计划……",  // 完整简介
  "cast": "刘德华 / 吴京",  // 主演文案
  "wantSeeCount": 12890  // 想看人数
}
```

**recommendMovies → `GET /reco/personal` 入参（Query 等价 JSON）**

```json
{
  "limit": 10,  // 可选；返回条数，默认 10，最大 20
  "excludeMovieIds": null  // 可选；排除已展示的影片 ID，逗号分隔如 "m100,m201"
}
```

> 用户身份来自 Header `Authorization`（未登录则 `mode=fallback_hot`），**不**在 Body/Query 传 `userId`。按类型筛片用 `searchMovies.genre`，不用本 Tool 私造 `genre` 字段。

**recommendMovies 出参（= `data`）**

```json
{
  "mode": "personalized",  // personalized：已登录个性化；fallback_hot：未登录兜底热门
  "items": [  // 推荐项列表
    {
      "movie": {  // 影片快照 MovieVO
        "movieId": "m201",  // 影片 ID
        "title": "年会不能停！",  // 片名
        "posterUrl": "https://cdn.example/m201.jpg",  // 海报 URL
        "genres": ["喜剧"],  // 类型标签
        "rating": 8.2,  // 评分
        "durationMin": 118,  // 片长（分钟）
        "releaseDate": "2026-01-10",  // 上映日
        "status": "hot_showing",  // 上映状态
        "description": "打工人逆袭……",  // 简介
        "cast": "大鹏 / 白客",  // 主演文案
        "wantSeeCount": 5600  // 想看人数
      },
      "personalScore": 0.86,  // 个人推荐分（0–1）
      "reason": "符合你偏好的喜剧"  // 推荐理由；可为 null
    }
  ]
}
```

#### 3.2.3 ShowAgent

| Tool | 副作用 | 超时 | 幂等 |
|------|--------|------|------|
| `listShows` | 无 | 800ms | 是 |
| `getShow` | 无 | 500ms | 是 |

**listShows → `GET /shows` 入参（Query 等价 JSON）**

```json
{
  "cinemaId": "c12",  // 必填；影院 ID
  "movieId": "m100",  // 必填；影片 ID
  "date": "2026-07-28"  // 必填；业务日 YYYY-MM-DD
}
```

> Draft 中的 `timeWindow`（如 afternoon）**不是** API 参数；Agent 在拿到 `items` 后按 `startTime` 本地过滤。

**listShows 出参（= `data`）**

```json
{
  "date": "2026-07-28",  // 回显查询业务日
  "items": [  // ShowVO 列表，按 startTime 升序
    {
      "showId": "s900",  // 场次 ID
      "movieId": "m100",  // 影片 ID
      "cinemaId": "c12",  // 影院 ID
      "hallId": "h1",  // 影厅 ID
      "hallName": "1号厅",  // 影厅名称
      "startTime": "2026-07-28T14:10:00+08:00",  // 开场时间 ISO-8601
      "endTime": "2026-07-28T17:05:00+08:00",  // 散场时间 ISO-8601
      "price": 55.0,  // 单价（元）
      "seatRemain": 86,  // 可售座位数
      "seatRemainLevel": "ample"  // 余座档位：ample | tight | almost_full
    },
    {
      "showId": "s901",  // 场次 ID
      "movieId": "m100",  // 影片 ID
      "cinemaId": "c12",  // 影院 ID
      "hallId": "h1",  // 影厅 ID
      "hallName": "1号厅",  // 影厅名称
      "startTime": "2026-07-28T16:40:00+08:00",  // 开场时间
      "endTime": "2026-07-28T19:35:00+08:00",  // 散场时间
      "price": 55.0,  // 单价（元）
      "seatRemain": 12,  // 可售座位数
      "seatRemainLevel": "tight"  // 余座档位
    }
  ]
}
```

**getShow → `GET /shows/{showId}` 入参**

```json
{
  "showId": "s900"  // Path；场次 ID
}
```

**getShow 出参（= `data`，ShowVO + movie/cinema 简要）**

```json
{
  "showId": "s900",  // 场次 ID
  "movieId": "m100",  // 影片 ID
  "cinemaId": "c12",  // 影院 ID
  "hallId": "h1",  // 影厅 ID
  "hallName": "1号厅",  // 影厅名称
  "startTime": "2026-07-28T14:10:00+08:00",  // 开场时间
  "endTime": "2026-07-28T17:05:00+08:00",  // 散场时间
  "price": 55.0,  // 单价（元）
  "seatRemain": 86,  // 可售座位数
  "seatRemainLevel": "ample",  // 余座档位
  "movie": {  // 影片简要 MovieVO
    "movieId": "m100",  // 影片 ID
    "title": "流浪地球 3",  // 片名
    "posterUrl": "https://cdn.example/m100.jpg",  // 海报 URL
    "genres": ["科幻", "冒险"],  // 类型标签
    "rating": 9.1,  // 评分
    "durationMin": 173,  // 片长（分钟）
    "releaseDate": "2026-02-01",  // 上映日
    "status": "hot_showing",  // 上映状态
    "description": "太阳危急……",  // 简介
    "cast": "刘德华 / 吴京",  // 主演文案
    "wantSeeCount": 12890  // 想看人数
  },
  "cinema": {  // 影院简要 CinemaVO
    "cinemaId": "c12",  // 影院 ID
    "name": "万达影城（五角场店）",  // 影院名称
    "address": "淞沪路 77 号",  // 地址
    "distanceMeters": null,  // 未传用户坐标时为 null
    "minPrice": 45.0  // 可售场次最低票价
  }
}
```

#### 3.2.4 SeatAgent

| Tool | 副作用 | 超时 | 幂等 |
|------|--------|------|------|
| `getSeatMap` | 无 | 800ms | 是 |
| `recommendSeats` | 无 | 1200ms | 是 |
| `lockSeats` | 写 | 1500ms | 否（须 Header `Idempotency-Key`） |
| `unlockSeats` | 写 | 800ms | 是（已释放再调仍成功） |

**getSeatMap → `GET /shows/{showId}/seat-map` 入参**

```json
{
  "showId": "s900"  // Path；场次 ID
}
```

**getSeatMap 出参（= `data`）**

```json
{
  "showId": "s900",  // 场次 ID
  "rows": 12,  // 包围盒行数（稀疏图；≠ 实座数）
  "cols": 16,  // 包围盒列数
  "screenLabel": "银幕",  // 银幕文案
  "price": 55.0,  // 本场单价（元），冗余字段
  "legend": {  // 图例：状态/区域 → 展示文案
    "available": "可选",
    "locked": "锁定中",
    "sold": "已售",
    "unavailable": "不可选",
    "golden": "黄金区"
  },
  "seats": [  // 稀疏 SeatVO（仅有座格子；缺口不返回）
    {
      "seatId": "sm1:6:7",  // 系统键；锁座/下单 seatIds 用此值
      "seatName": "6排7座",  // 对号文案（票面/底栏）
      "rowNo": 6,  // 业务排号
      "colNo": 7,  // 业务座号
      "graphRow": 6,  // 画布行
      "graphCol": 7,  // 画布列
      "type": "normal",  // 座位类型：normal | couple | disabled
      "zone": "golden",  // 区域：normal | golden
      "status": "available",  // 占用：available | locked | sold | unavailable
      "couplePairId": null  // 情侣座成对 ID；非情侣座为 null
    }
  ]
}
```

**recommendSeats → `POST /reco/seats` 入参（Body）**

```json
{
  "showId": "s900",  // 必填；场次 ID
  "count": 2,  // 必填；票数 1–4
  "preferRow": "middle",  // 可选；默认 middle；front | middle | back
  "preferSide": "center",  // 可选；默认 center；center | aisle | edge
  "together": true  // 可选；默认 true；是否要求连座
}
```

**recommendSeats 出参（= `data`）**

```json
{
  "showId": "s900",  // 场次 ID
  "plans": [  // 推荐方案 1–3 个；完全无解可为 []
    {
      "planId": "sp_1",  // 方案 ID
      "seatIds": ["sm1:6:7", "sm1:6:8"],  // 系统 seatId（已过滤不可售）
      "score": 91.2,  // 方案得分 0–100
      "explain": "黄金区居中连座",  // 方案解释文案
      "seats": [  // 可选展开的 SeatVO
        {
          "seatId": "sm1:6:7",
          "seatName": "6排7座",
          "rowNo": 6,
          "colNo": 7,
          "graphRow": 6,
          "graphCol": 7,
          "type": "normal",
          "zone": "golden",
          "status": "available",
          "couplePairId": null
        },
        {
          "seatId": "sm1:6:8",
          "seatName": "6排8座",
          "rowNo": 6,
          "colNo": 8,
          "graphRow": 6,
          "graphCol": 8,
          "type": "normal",
          "zone": "golden",
          "status": "available",
          "couplePairId": null
        }
      ]
    }
  ],
  "compromise": null  // 无理想解时的折中建议；有解时为 null。结构：{ suggestion, altShowIds }
}
```

**lockSeats → `POST /locks` 入参（Body）**

```json
{
  "showId": "s900",  // 必填；场次 ID
  "seatIds": ["sm1:6:7", "sm1:6:8"],  // 必填；系统 seatId 列表，长度 1–4
  "ttlSeconds": 900,  // 可选；锁座 TTL 秒，默认 900（15min），范围 60–900
  "sessionId": "sess_1"  // 可选；传入则回写 BookingDraft 锁字段
}
```

> 另传 Header：`Idempotency-Key: idem_lock_sess1_s900_sm1_6_7_8`（**不**写入 Body）。

**lockSeats 出参（= `data`，LockVO）**

```json
{
  "lockId": "lk_01HZX",  // 锁座凭证 ID
  "showId": "s900",  // 场次 ID
  "seatIds": ["sm1:6:7", "sm1:6:8"],  // 已锁系统 seatId
  "userId": "u1",  // 锁归属用户
  "expireAt": "2026-07-28T15:05:00+08:00",  // 锁到期时间 ISO-8601
  "ttlSeconds": 900,  // 本次 TTL（秒）
  "status": "active"  // 锁状态：active | expired | consumed | released
}
```

**unlockSeats → `DELETE /locks/{lockId}` 入参**

```json
{
  "lockId": "lk_01HZX",  // Path；锁座凭证 ID
  "sessionId": "sess_1"  // 可选 Query；成功后清 Draft 的 lock/seat 字段
}
```

**unlockSeats 出参（= `data`）**

```json
{
  "lockId": "lk_01HZX",  // 锁座凭证 ID
  "released": true  // 是否已释放（幂等：已释放/已过期也返回 true）
}
```

#### 3.2.5 OrderAgent

| Tool | 副作用 | 超时 | 幂等 |
|------|--------|------|------|
| `createOrder` | 写 | 1000ms | 是（同 `lockId` 天然唯一，返回已有单） |
| `getOrder` | 无 | 500ms | 是 |
| `cancelOrder` | 写 | 800ms | 是 |
| ~~`payMock`~~ | — | — | **禁止注册** |

**createOrder → `POST /orders` 入参（Body）**

```json
{
  "lockId": "lk_01HZX",  // 必填；有效锁座凭证，须属当前用户且未过期
  "sessionId": "sess_1"  // 可选；回写 Draft.orderId
}
```

**createOrder 出参（= `data`，OrderVO）**

```json
{
  "orderId": "o_1001",  // 订单 ID
  "userId": "u1",  // 下单用户
  "showId": "s900",  // 场次 ID
  "movieTitle": "流浪地球 3",  // 影片名快照（下单后不变）
  "cinemaName": "万达影城（五角场店）",  // 影院名快照
  "hallName": "1号厅",  // 影厅名快照
  "startTime": "2026-07-28T14:10:00+08:00",  // 开场时间快照
  "seatIds": ["sm1:6:7", "sm1:6:8"],  // 系统 seatId 快照（展示用 seatName，见 SeatVO）
  "unitPrice": 55.0,  // 单价快照（元）
  "amount": 110.0,  // 订单金额 = unitPrice × 座位数
  "status": "pending_pay",  // pending_pay | issued | cancelled
  "ticketCode": null,  // 取票码；出票后填充
  "qrPayload": null,  // 取票 QR 签名载荷；出票后填充
  "lockId": "lk_01HZX",  // 关联锁座 ID（一锁一单）
  "expireAt": "2026-07-28T15:05:00+08:00",  // 待支付截止（通常=锁 expireAt）
  "createdAt": "2026-07-28T14:50:00+08:00",  // 创建时间
  "payAt": null  // 支付时间；未支付为 null
}
```

**getOrder → `GET /orders/{orderId}` 入参**

```json
{
  "orderId": "o_1001"  // Path；订单 ID
}
```

**getOrder 出参：** 同 `createOrder` 出参（OrderVO 全字段）。

**cancelOrder → `POST /orders/{orderId}/cancel` 入参**

```json
{
  "orderId": "o_1001",  // Path；订单 ID
  "reason": "user_cancel"  // 可选 Body；取消原因，如 user_cancel
}
```

**cancelOrder 出参（= `data`，OrderVO；`status=cancelled`）**

```json
{
  "orderId": "o_1001",  // 订单 ID
  "userId": "u1",  // 下单用户
  "showId": "s900",  // 场次 ID
  "movieTitle": "流浪地球 3",  // 影片名快照
  "cinemaName": "万达影城（五角场店）",  // 影院名快照
  "hallName": "1号厅",  // 影厅名快照
  "startTime": "2026-07-28T14:10:00+08:00",  // 开场时间快照
  "seatIds": ["sm1:6:7", "sm1:6:8"],  // 系统 seatId 快照
  "unitPrice": 55.0,  // 单价快照
  "amount": 110.0,  // 订单金额
  "status": "cancelled",  // 已取消
  "ticketCode": null,  // 未出票
  "qrPayload": null,
  "lockId": "lk_01HZX",  // 原锁 ID（取消时会释放仍 active 的锁）
  "expireAt": null,  // 取消后可清空
  "createdAt": "2026-07-28T14:50:00+08:00",  // 创建时间
  "payAt": null  // 未支付
}
```

#### 3.2.6 支付管理（无子 Agent · 二维码模拟支付）

支付只走中台 REST；Agent Core **不得**调用、注册、间接触发支付。MVP 采用 **双二维码**：

| 二维码 | 生成时机 | 编码内容 | 用途 |
|--------|----------|----------|------|
| **支付 QR** | 进入 `PayMock` / `pending_pay` | H5 支付页 URL + 短期 `payToken` | 手机扫码 → 展示订单摘要 → 用户**显式**点「确认付款」 |
| **取票 QR** | `POST /pay` 成功后 | 签名 `qrPayload`（含 orderId/userId/payAt/ticketCode） | 影院验票 / 用户自助出示 |

**硬约束（仍满足 B-G4）：**

1. `payToken` **不能**代替用户点击；手机页须点按钮才调 `POST /pay`。
2. Agent Tool 白名单**不含** pay；`pay_mock` 卡只展示 `payUrl` / 金额 / 倒计时。
3. PC 端可通过轮询 `GET /orders/{id}` 感知手机支付完成（MVP 推荐）。

**相关 REST（均禁止注册为 Tool）：**

| 接口 | 作用 |
|------|------|
| `GET /orders/{orderId}/pay-qrcode` | 生成支付页 URL + token（PC 画 QR） |
| `GET /orders/{orderId}/pay-session?t=` | 手机页拉订单摘要（验 token，无需 JWT） |
| `POST /orders/{orderId}/pay` | 确认模拟支付并出票 |
| `GET /tickets/verify?payload=` | 核验取票 QR（P0 可选，运营/演示） |

**`GET /orders/{orderId}/pay-qrcode` 出参（= `data`，PayQrVO）**

```json
{
  "orderId": "o_1001",
  "amount": 110.0,
  "expireAt": "2026-07-28T15:05:00+08:00",
  "payUrl": "https://ticket.example.com/m/pay/o_1001?t=eyJhbGciOiJIUzI1NiJ9...",
  "pollIntervalMs": 2000
}
```

**`GET /orders/{orderId}/pay-session` 入参（Query）**

```json
{
  "orderId": "o_1001",
  "t": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**`GET /orders/{orderId}/pay-session` 出参（= `data`，PaySessionVO）**

```json
{
  "orderId": "o_1001",
  "amount": 110.0,
  "expireAt": "2026-07-28T15:05:00+08:00",
  "movieTitle": "流浪地球 3",
  "cinemaName": "万达影城（五角场店）",
  "hallName": "1号厅",
  "startTime": "2026-07-28T14:10:00+08:00",
  "seatIds": ["sm1:6:7", "sm1:6:8"],
  "status": "pending_pay"
}
```

**`POST /orders/{orderId}/pay` 入参**

```json
{
  "orderId": "o_1001",
  "mockResult": "success",
  "channel": "mobile_qr"
}
```

> 鉴权二选一：`Authorization: Bearer`（本人 JWT）**或** Header `X-Pay-Token: {payToken}`（须与 Path `orderId` 匹配且未过期）。  
> 另可带 Header `Idempotency-Key`；**不**在 Body 传 `idempotencyKey`。  
> `channel`：`desktop_button` \| `mobile_qr`（审计用，默认 `desktop_button`）。

**出参（= `data`，OrderVO；支付成功）**

```json
{
  "orderId": "o_1001",
  "userId": "u1",
  "showId": "s900",
  "movieTitle": "流浪地球 3",
  "cinemaName": "万达影城（五角场店）",
  "hallName": "1号厅",
  "startTime": "2026-07-28T14:10:00+08:00",
  "seatIds": ["sm1:6:7", "sm1:6:8"],
  "unitPrice": 55.0,
  "amount": 110.0,
  "status": "issued",
  "ticketCode": "TKT-20260728-1001",
  "qrPayload": "MIAOYU|v1|o_1001|u1|1722159090|TKT-20260728-1001|a1b2c3d4e5f67890",
  "lockId": "lk_01HZX",
  "expireAt": null,
  "payChannel": "mobile_qr",
  "createdAt": "2026-07-28T14:50:00+08:00",
  "payAt": "2026-07-28T14:51:30+08:00"
}
```

**取票 QR 载荷格式（`qrPayload`，出票时生成并落库）：**

```text
MIAOYU|v1|{orderId}|{userId}|{payAtUnix}|{ticketCode}|{sig16}

canonical = "v1|{orderId}|{userId}|{payAtUnix}|{ticketCode}"
sig16     = hex(HMAC-SHA256(canonical, ticket.qr-secret))[0:16]
```

**`ticketCode` 规则：** `TKT-{yyyyMMdd}-{orderId 末 4 位}`，如 `TKT-20260728-1001`。

**`payToken` 规则（JWT 或等价 signed blob）：**

```json
{
  "jti": "pt_01HZX",
  "orderId": "o_1001",
  "userId": "u1",
  "exp": 1722162300
}
```

- 签名密钥：`ticket.pay-qr-secret`（配置项，与取票密钥分离）
- `exp` = min(`order.expire_at`, `now + 900s`)
- 签发时写 Redis `pay:token:{jti}` TTL = 剩余秒；`POST /pay` 成功后 DEL（防重放）
- `payUrl` = `{ticket.public-base-url}/m/pay/{orderId}?t={payToken}`

**`GET /tickets/verify?payload=` 出参（= `data`，TicketVerifyVO）**

```json
{
  "valid": true,
  "orderId": "o_1001",
  "ticketCode": "TKT-20260728-1001",
  "movieTitle": "流浪地球 3",
  "cinemaName": "万达影城（五角场店）",
  "hallName": "1号厅",
  "startTime": "2026-07-28T14:10:00+08:00",
  "seatIds": ["sm1:6:7", "sm1:6:8"],
  "payAt": "2026-07-28T14:51:30+08:00",
  "userId": "u1"
}
```

#### 3.2.7 用户管理 / 热门推荐系统

| 模块 | Agent 关系 | 说明 |
|------|------------|------|
| 用户管理 | 横切鉴权 | 锁座/下单前 `needLogin`；画像供 `GET /reco/personal` |
| 热门推荐系统 | MovieAgent / SeatAgent 经上表 Tool 调中台 | `GET /reco/weekly-hot`、`GET /reco/personal`、`POST /reco/seats`；禁止 LLM 生成榜单 |

### 3.3 子 Agent 实现规格（LangGraph · ticket-agent）

> 本节写清 **怎么做**，与 §3.2 Tool 契约、§5 Planner、ADR-0001 对齐。购票子 Agent **只读/写中台 REST**，不持有库存真相。

#### 3.3.1 总架构：Router + 领域子图（禁止纯 ReAct 自由写库）

```text
POST /agent/turns
        │
        ▼
┌─ BookingGraph (LangGraph StateGraph) ─────────────────────────────┐
│  load_context → nlp_slots → planner → router → subagent* → compose │
│       │              │         │         │                         │
│       │              │         │         ├─ movie_node   (MovieAgent tools)   │
│       │              │         │         ├─ cinema_node  (CinemaAgent tools)  │
│       │              │         │         ├─ show_node    (ShowAgent tools)    │
│       │              │         │         ├─ seat_node    (SeatAgent tools)    │
│       │              │         │         ├─ order_node   (OrderAgent tools)   │
│       │              │         │         └─ faq_node     (retrieve_faq only)  │
│       └─ Draft(中台)  └─ IntentSlotDTO   └─ 确定性 next_step（§5.3）          │
└───────────────────────────────────────────────────────────────────┘
```

| 组件 | 实现要点 |
|------|----------|
| **图框架** | LangGraph `StateGraph`；`compile(checkpointer=PostgresSaver)`；`thread_id = sessionId` |
| **状态** | `BookingGraphState`：`session_id`、`intent`、`slot_patch`、`draft_snapshot`、`tool_results[]`、`cards[]`、`reply`、`need_login` |
| **NLP** | LangChain `with_structured_output(IntentSlotDTO)`；超时 → 规则/词典降级（§5.2） |
| **Planner** | **纯 Python 状态机**（§5.3），产出 `PlanAction`：`SHOW_CARDS` / `ASK_SLOT` / `CALL_TOOLS` / `SKIP_TO` / `CancelFlow`；**不**把下一步交给 LLM ReAct |
| **Router** | 按 `PlanAction` + `intent` 选中 **一个** 子节点；同轮可顺序多跳（如 cinema→show），由 Planner 批次决定，单跳内 Tool 可并行只读 |
| **子 Agent 节点** | 绑定 **白名单 Tools**（§5.5）；节点内可用小模型决定「调哪个 Tool、参数」**或**由 Planner 直接给出 `tool_calls`（MVP 推荐后者，更稳） |
| **FAQ 节点** | 仅 `retrieve_faq`；结果进 `reply`/`ask`，**禁止**写 Draft 的 `lockId`/`orderId` |
| **CardComposer** | 确定性代码组装 §5.6 卡片；不经 LLM 生成 JSON schema |

#### 3.3.2 子 Agent ↔ Tool 绑定表

| 子 Agent 节点 | 绑定 Tools | 何时进入 |
|---------------|------------|----------|
| `movie_node` | `searchMovies`, `getMovie`, `recommendMovies` | SelectMovie / browse / 片名消歧 |
| `cinema_node` | `searchCinemas`, `getCinema` | SelectCinema；缺 lat/lng 则先 ASK |
| `show_node` | `listShows`, `getShow` | SelectShow；按 date/timeWindow 过滤可在节点后处理 |
| `seat_node` | `getSeatMap`, `recommendSeats`, `lockSeats`, `unlockSeats` | SelectSeat / 锁失败兜底 |
| `order_node` | `createOrder`, `getOrder` | ConfirmOrder；**无 pay** |
| `faq_node` | `retrieve_faq` | intent=`chitchat` 或政策类问句；或主路径缺知识时旁路 |

**路由伪代码（确定性）：**

```python
def route(plan: PlanAction, intent: str) -> str:
    if intent == "chitchat" or plan.needs_faq:
        return "faq_node"
    if plan.action == "CancelFlow":
        return "seat_node"  # 可能 unlock；否则 compose 清 Draft
    step = plan.target_step  # SelectMovie|SelectCinema|SelectShow|SelectSeat|ConfirmOrder
    return {
        "SelectMovie": "movie_node",
        "SelectCinema": "cinema_node",
        "SelectShow": "show_node",
        "SelectSeat": "seat_node",
        "ConfirmOrder": "order_node",
    }[step]
```

#### 3.3.3 目录与文件落点（与 §5.13.2 对齐）

```text
app/langchain/
├── graph/
│   ├── booking_graph.py      # StateGraph 组装 + compile(checkpointer)
│   ├── state.py              # BookingGraphState TypedDict
│   └── nodes/
│       ├── load_context.py   # Draft GET + messages SELECT
│       ├── nlp.py
│       ├── planner.py        # §5.3
│       ├── router.py
│       ├── movie_agent.py
│       ├── cinema_agent.py
│       ├── show_agent.py
│       ├── seat_agent.py
│       ├── order_agent.py
│       └── faq_agent.py      # → rag.retriever
├── tools/                    # 各 @tool → httpx 中台
└── rag/                      # §5.9
```

#### 3.3.4 硬约束（子 Agent）

1. 子 Agent **不得**直连中台 PostgreSQL / Redis；一律 httpx REST。
2. 写 Tool（`lockSeats`/`createOrder`/`unlockSeats`）必须透传用户 JWT + `Idempotency-Key`。
3. **禁止**注册 pay 类 Tool（§5.12）。
4. FAQ/RAG hits **不得**直接写入 `lockId`/`orderId`/`showId` 作为「已选定事实」——场次/座位必须以 Tool 响应为准。
5. 运营写接口（`/admin/*`）**不**进入购票子 Agent Tool 白名单（Agent 面向 C 端购票）。

---

## 4. BookingDraft 与共享状态机

本章定义双模式共用的购票草稿与状态机。

### 1. 一句话定义

**BookingDraft（购票草稿）** 是「这一轮购票」在服务端保存的**结构化进度单**：记录用户已经选到哪一步、选了什么（影片/影院/场次/座位…）、以及从哪来的（手动 / Agent / 混合）。

它不是订单（Order），也不是锁座记录（SeatLock）。

| 概念 | 是什么 | 何时产生 | 可否支付 |
|------|--------|----------|----------|
| BookingDraft | 购票进行中的临时选择与状态 | 打开购票或 Agent 会话 | 否 |
| SeatLock | 座位占用凭证（TTL 15min） | 确认选座成功 | 否（但是下单前提） |
| Order | 待支付/已出票单据 | 锁座后 createOrder | 是 |

类比：Draft = 购物车进度；Lock = 货架暂扣；Order = 收银台小票。

### 2. 为什么需要它

1. **双模式共用进度**：普通页面栈与 Agent 对话推进的是同一份 Draft，切换不丢片/院/场。
2. **Agent 槽位载体**：NLP 抽出的 `genre/date/count…` 先写入 Draft，Planner 按完备度追问或跳步。
3. **回退有依据**：用户改影院时，按依赖关系清空场次/座位/锁/单。
4. **指代上下文**：`listContext` 记住「当前列表第几个」，支持「换成第 2 个」。

### 3. 完整字段定义

```json
{
  "sessionId": "sess_01HZX...",
  "userId": "u1",
  "source": "agent",
  "state": "SelectShow",
  "intent": "buy_ticket",
  "movieId": "m100",
  "filmTitle": "流浪地球 3",
  "genre": "科幻",
  "date": "2026-07-28",
  "timeWindow": "afternoon",
  "lat": 31.2989,
  "lng": 121.5140,
  "cinemaId": "c12",
  "showId": "s900",
  "count": 2,
  "seatIds": ["A5","A6"],
  "preferRow": "middle",
  "preferSide": "center",
  "together": true,
  "budgetMax": null,
  "lockId": null,
  "orderId": null,
  "expireAt": null,
  "listContext": {
    "type": "shows",
    "ids": ["s900","s901","s902"],
    "labels": ["14:10 1号厅","16:40 1号厅","19:20 2号厅"]
  },
  "version": 7,
  "updatedAt": "2026-07-27T16:00:00+08:00"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | string | 是 | 会话主键；浏览器 localStorage 持久化 |
| userId | string | 否 | 登录后写入；锁座前必须有 |
| source | enum | 是 | `manual` / `agent` / `hybrid` |
| state | enum | 是 | 见 §4 BookingState |
| intent | enum | 否 | buy_ticket / browse / modify / cancel / chitchat |
| movieId | string | 条件 | SelectMovie 完备后必有 |
| filmTitle | string | 否 | NLP 原文片名，解析前暂存 |
| genre | string | 否 | 模糊选片条件 |
| date | string | 否 | `YYYY-MM-DD` 业务日 |
| timeWindow | string | 否 | morning/afternoon/evening/night 或具体时刻 |
| lat | number | 否 | 用户纬度；搜附近影院时由客户端写入 |
| lng | number | 否 | 用户经度；与 lat 成对 |
| cinemaId | string | 条件 | SelectCinema 完备 |
| showId | string | 条件 | SelectShow 完备 |
| count | int | 是 | 票数，默认 1，范围 1–4 |
| seatIds | string[] | 条件 | 与 count 一致且情侣座成对 |
| preferRow | enum | 否 | front / middle / back |
| preferSide | enum | 否 | center / aisle / edge |
| together | bool | 否 | 是否连座，默认 true |
| budgetMax | number | 否 | 预算上限（改口「太贵了」用） |
| lockId | string | 条件 | 锁座成功后 |
| orderId | string | 条件 | 创建订单后 |
| expireAt | datetime | 条件 | 锁座/待支付截止 |
| listContext | object | 否 | 当前卡片列表，供指代 |
| version | long | 是 | 乐观锁；冲突 → code=-1, errorCode=DRAFT_CONFLICT |
| updatedAt | datetime | 是 | 最后更新 |

### 4. 与 BookingState 的关系

```text
state 表示「当前焦点停在哪一步」（= firstIncompleteStep）
字段完备度决定「能不能离开这一步」
```

**片 / 院两步可互换顺序：** `SelectMovie` 与 `SelectCinema` 无固定先后；`movieId` 与 `cinemaId` **均完备**后才可进入 `SelectShow`。UI 进度条仍展示五步「选片→影院→场次→选座→支付」，已完成的那一步在条上标记为 done，与 `state` 当前焦点可不一致（例如院→片路径下已选院、正在选片时，`state=SelectMovie`，进度条「影院」步已打勾）。

| state | 进入条件 | 离开本步最少要有 | 离开后副作用 |
|-------|----------|------------------|--------------|
| Idle | 打开站点，或购票结束 / 取消 | 产生购票意图 | 可清空临时选择 |
| SelectMovie | 缺 `movieId`（含院→片：已有 `cinemaId` 仍可能在本态） | 唯一 `movieId` | 记录影片；若 `cinemaId` 已有则下一步为 `SelectShow` |
| SelectCinema | 缺 `cinemaId`（含片→院：已有 `movieId` 仍可能在本态） | `cinemaId` | 记录影院；若 `movieId` 已有则下一步为 `SelectShow` |
| SelectShow | `movieId` **且** `cinemaId` 均已确定 | `showId` | 准备座位图 |
| SelectSeat | `showId` 已确定；锁座前须登录 | `seatIds` 合法 + lock 成功 | `lockId`、`expireAt` |
| ConfirmOrder | 锁座成功 | 用户点「去支付」 | `state→PayMock` |
| PayMock | 用户从确认进入支付 | 用户点「确认支付」且 pay 成功 | order issued |
| TicketIssued | 支付成功 | — | 终态 |

**`firstIncompleteStep` 判定（Planner / 写 Draft 共用）：**

```text
if !movieId:        return SelectMovie
if !cinemaId:       return SelectCinema
if !showId:         return SelectShow
if !lockId:         return SelectSeat
if !orderId:        return ConfirmOrder  // 或 PayMock，视是否已点去支付
...
```

### 5. 依赖与回退清空规则

```text
movieId ─┐
         ├──▶ showId ◀── seatIds / lockId / orderId
cinemaId ─┘
         ↑ 改 movieId 或 cinemaId，必须清空 show 及以下所有依赖
```

| 用户改动 | 清空字段 | 回到 state |
|----------|----------|------------|
| 改 movieId | cinema/show/seats/lock/order…（保守清空 cinema） | `firstIncompleteStep`（通常 `SelectCinema`；院→片且保留 cinema 时为 `SelectMovie`） |
| 改 cinemaId | show/seats/lock/order（`movieId` 可保留） | `firstIncompleteStep`（通常 `SelectMovie` 或 `SelectShow`） |
| 改 showId | seats/lock/order | SelectSeat |
| 改 seatIds（未锁） | — | 仍 SelectSeat |
| 取消待支付 | 释放 lock；清 lock/order/seats（可保留 show） | SelectSeat 或 SelectShow |
| 锁座超时 | 同上 | SelectSeat |

### 6. source 三种来源

| source | 含义 | 典型路径 |
|--------|------|----------|
| manual | 全程页面点选 | **片→院**：热映→详情→院→场→手选→支付；**院→片**：影院 Tab→选院→选片→场→手选→支付 |
| agent | 全程对话+卡 | Chip→卡片链→支付 |
| hybrid | 两边都动过 | 手动到场次再开 Agent 选座；或 Agent 后「自己选」改座 |

手调座位后若原为 agent，应变为 `hybrid`。

### 7. 生命周期

**主链路**（`SelectShow` 之后固定顺序）与 **选片/选院阶段**（顺序任意，在复合态内完成）分开表达；改片/院/场的回退规则见 §4.5，不在图中逐条画边。

```mermaid
stateDiagram-v2
  direction TB

  [*] --> Idle
  Idle --> PickFilmCinema: 购票意图

  state PickFilmCinema {
    direction LR
    [*] --> SelectMovie: 片→院
    [*] --> SelectCinema: 院→片
    SelectMovie --> SelectCinema: 缺 cinema
    SelectCinema --> SelectMovie: 缺 movie
  }

  PickFilmCinema --> SelectShow: movieId ∧ cinemaId
  SelectShow --> SelectSeat: showId
  SelectSeat --> ConfirmOrder: lockSeats
  ConfirmOrder --> PayMock: 去支付
  PayMock --> TicketIssued: pay 成功
  TicketIssued --> Idle: 结束

  SelectShow --> PickFilmCinema: 改片/院
  SelectSeat --> SelectShow: 改场次
  ConfirmOrder --> SelectSeat: 取消
  PayMock --> SelectSeat: 超时/取消
```

### 8. 读写入口

| 入口 | 谁写 Draft |
|------|------------|
| 普通页选择 | 前端 debounce `PUT /booking-drafts/{sessionId}` |
| Agent 文本/点卡 | `POST /agent/turns` 服务端合并后返回最新 draft |
| 锁座/下单成功 | 中台 Use Case 回写 lockId/orderId（经 turn 或前端再 GET） |
| 模式切换 | `GET /booking-drafts/{sessionId}` hydrate |

**并发：** 带 `version`；服务端 CAS 更新，失败返回 `DRAFT_CONFLICT`，客户端拉最新再合并。

### 9. 与前端/Agent 的边界

- 前端：**不**自己发明第二套进度字段；UI 进度条只映射 `draft.state`。
- Agent Planner：只读 Draft 完备度做决策，写回也只改 Draft + 调 Tools。
- 中台库存：**不**信任 Draft 里的「假装已锁」；锁座以 `seat_lock` 表为准。

### 10. 反例（易错）

| 错误做法 | 正确做法 |
|----------|----------|
| Draft 当订单持久化出票 | 出票只认 Order.status=issued |
| LLM 直接改 seat 为 sold | 必须 lockSeats Tool / API |
| Agent 与页面各存一份选座 | 单一 sessionId → 一份 Draft |
| 改影院不清空座位 | 按依赖表清空 |

---

## 5. Agent 协作设计（Python LangChain 服务 + 中台 REST）

> **实现位置：** 本章流程在 **ticket-agent（Python + LangChain）** 运行；Java 票务中台提供 Tool REST、Draft 持久化与鉴权。对话 `agent_message` 落在 **Agent 自有库**。对外 **Turn 契约不变**（§7.8.1），前端无感。

Agent 服务负责：RAG、意图/槽位、规划、LangChain Tool 调度、Card 组装、**出站调中台**、禁静默支付校验。中台负责：库存真相、Draft CAS、JWT、写 Tool 幂等。

### 5.1 一轮对话的处理流水线（Python Agent 服务内）

```text
Client POST /api/v1/agent/turns   →  ticket-agent (Python)
  body: { sessionId, message?, cardAction?, clientDraftVersion? }
        │
        ▼
┌─ ProcessAgentTurn / BookingGraph（Python · LangGraph）──────────────────┐
│ 1. GET 中台 /booking-drafts/{sessionId}                                   │
│ 2. SELECT agent_message … LIMIT 20（Agent 自有库）                        │
│ 3. PostgresSaver 恢复 thread_id=sessionId 短期图状态（可选）              │
│ 4. Apply cardAction / draftPatch（确定性，优先于 NLP）                    │
│ 5. NLP Structured Output → intent + SlotPatch（超时 → 规则降级）          │
│ 6. Planner 确定性状态机 → PlanAction（§5.3）                              │
│ 7. Router → 子 Agent 节点（§3.3）；FAQ 意图可先 retrieve_faq（§5.9）       │
│ 8. Tools → HTTP ticket-api（透传用户 JWT；写操作 Idempotency-Key）        │
│ 9. Merge Draft；PUT 中台 draft（version CAS）                             │
│10. CardComposer → cards + replyText                                       │
│11. INSERT agent_message；checkpointer 落盘；返回 AgentTurnResponse        │
└──────────────────────────────────────────────────────────────────────────┘
```

**硬约束：**

1. LangChain / RAG 不得直接返回「已锁座/已支付」；写库存必须 **HTTP 调中台** 且以响应为准。
2. LangChain Tool 白名单 **不含** pay；支付仍走中台 `POST /orders/{id}/pay`（前端显式）。
3. **禁止** Agent 直连**中台** PostgreSQL；Draft 只经中台 API。对话 `agent_message` / checkpoint 仅在 **Agent 自有库**。
4. 中台 Tool 超时/错误映射见 §5.10；写 Tool 带 `Idempotency-Key`。
5. 子 Agent 路由与 Tool 绑定见 **§3.3**；不得绕过 Planner 自由 ReAct 写锁座。

### 5.2 NLP 模块

#### 5.2.1 意图分类（F-NLP-01）

| intent | 触发示例 | 后续 |
|--------|----------|------|
| buy_ticket | 「帮我订两张…」 | 进入/推进购票状态机 |
| browse | 「有什么好看的」 | 推推荐卡，可转 buy |
| modify | 「太贵了」「换第 2 个」 | 回退受影响状态 |
| cancel | 「取消吧」 | 解锁/取消待支付 → Idle 或 SelectShow |
| chitchat | 闲聊 | 短回 + 拉回购票 |

**实现策略（MVP）：** **Python LangChain Structured Output**（`with_structured_output` / Pydantic）→ `IntentSlotDTO`；失败或超时 → **规则 + 词典降级**。intent 必须映射到 §5.2.1 枚举。

#### 5.2.2 槽位提取（F-NLP-02）

从单句抽取多槽，输出 `SlotPatch`（仅含置信度 ≥ 阈值的字段）：

```text
「两张明天下午最近《流浪地球 3》」
→ count=2, date=tomorrow, timeWindow=afternoon,
  filmTitle=流浪地球 3
```

后处理：

1. 片名 → `searchMovies` 解析为 `movieId`（唯一则填；多候选 → 留 SelectMovie + 影片卡）
2. 「明天」→ 业务日 `date`
3. 「最近」→ 调用 `searchCinemas` 时使用客户端已写入 Draft 的 `lat`/`lng`（不解析商圈文案）
4. Schema：`count ∈ [1,4]`；非法丢弃并追问；缺 `lat`/`lng` 时先向客户端要定位再搜影院

#### 5.2.3 模糊意图 → 推荐（F-NLP-03）

`genre`/`mood` 有值但无 `movieId` → Tool `searchMovies(genre=…)`（内容检索）；要个性化榜单 → `recommendMovies`（`GET /reco/personal`）。

### 5.3 Planning 模块（F-PLAN-*）

Planner 是**确定性状态机驱动器**，不把「下一步」交给 LLM 自由发挥。

```text
function plan(draft):
  if intent == cancel: return CancelFlow
  missing = firstIncompleteStep(draft)
  if missing needs user choice among candidates:
      return SHOW_CARDS(step)
  if missing needs clarification (no candidates):
      return ASK_SLOT(slot)
  if canAutoResolve(missing):  # 如唯一影院/唯一场次
      return CALL_TOOL + advance
  if P1 and deeperSlotsFilled:
      return SKIP_TO(deepestExecutable)  # 自动跳步
  return SHOW_CARDS(missing)
```

| 完备度 | 行为 |
|--------|------|
| 缺必需槽且无候选 | 短追问 + 建议 Chip |
| 缺必需槽但有召回 | 推对应卡片，等用户点选 |
| 当前步满 | CALL_TOOL，进下一步 |
| 后续多步本轮已抽出（P1） | 跳过中间追问，直达最深可执行 |
| 用户修正 | 回退最早受影响节点，重规划 |
| 来自普通流程半程 | 只补缺失槽 |

**跳步示例：** 一句话含 film+date+count（客户端已带 lat/lng）→ 解析 movieId 后连续 `searchCinemas(lat,lng,…)` → `listShows` → 若场次唯一或用户已点选 → 进入 `recommendSeats`，中间不追问「要不要选影院」。

### 5.4 Memory 模块（F-MEM-*）

| 层级 | 存储 | 内容 | 优先级 |
|------|------|------|--------|
| 工作记忆 | Redis/DB session | BookingDraft + listContext | P0 |
| 对话记忆 | DB `agent_message` 近 20 轮 | role/content/cards 摘要 | P0 |
| 指代 | draft.listContext | 「第 N 个」「那个喜剧」 | P1 |
| 跨会话偏好 | user_profile | preferGenres/row/side | P1 |

指代消解算法（P1）：

1. 正则匹配「第(\d+)个」→ 取 `listContext.ids[n-1]`
2. 「换便宜的」→ intent=modify，对当前 show 列表按 price 重排
3. 失败 → 追问「你指的是哪一个？」并重推卡

### 5.5 Tools 白名单与契约

Agent Tool = LangChain Tool，内部 **HTTP 调用 ticket-api** 对应 REST（§3.2）。Tool `name` 与中台白名单一致。

| Tool | 子 Agent | 副作用 | 鉴权 | 说明 |
|------|----------|--------|------|------|
| searchMovies | MovieAgent | 无 | 公开 | = `GET /movies` Query：status/q/genre/page/size |
| recommendMovies | MovieAgent | 无 | 可选登录 | = `GET /reco/personal` Query：limit/excludeMovieIds |
| getMovie | MovieAgent | 无 | 公开 | = `GET /movies/{movieId}` |
| searchCinemas | CinemaAgent | 无 | 公开 | = `GET /cinemas` Query：lat/lng/radiusMeters/… |
| listShows | ShowAgent | 无 | 公开 | = `GET /shows` Query：cinemaId/movieId/date |
| getSeatMap | SeatAgent | 无 | 登录（购票路径） | = `GET /shows/{showId}/seat-map` |
| recommendSeats | SeatAgent | 无 | 登录 | = `POST /reco/seats` Body |
| lockSeats | SeatAgent | 写 | 登录 | = `POST /locks`；Header `Idempotency-Key` |
| unlockSeats | SeatAgent | 写 | 登录+归属 | = `DELETE /locks/{lockId}` |
| createOrder | OrderAgent | 写 | 登录+lock 归属 | = `POST /orders` |
| getOrder | OrderAgent | 无 | 本人 | = `GET /orders/{orderId}` |
| ~~payMock~~ | — | — | — | **禁止**；支付仅 `POST /orders/{id}/pay` |

Tool 执行结果写入本轮 `toolTraces[]`（调试/埋点），卡片只暴露用户可读字段。入参/出参 JSON 示例见 §3.2。

### 5.6 CardComposer（动态卡片协议）

后端决定「推什么卡」，前端只渲染。统一信封：

```json
{
  "cardId": "c_01",
  "type": "movie_list | cinema_list | show_list | seat_plans | order_confirm | pay_mock | ticket_issued | error | ask",
  "title": "为你找到这些喜剧",
  "payload": { },
  "actions": [
    { "actionId": "select", "label": "选这部", "draftPatch": { "movieId": "m1" } }
  ]
}
```

| type | payload 要点 | 主 action |
|------|--------------|-----------|
| movie_list | movies[] 海报/名/评分/类型 | select → movieId |
| cinema_list | cinemas[] 名/距离/最低价/厅标签 | select → cinemaId |
| show_list | shows[] 时间/厅/价/余座 | select → showId |
| seat_plans | plans[1..3] seats+score+explain | confirm / swap / manual |
| order_confirm | 快照 + expireAt | go_pay / cancel |
| pay_mock | amount + expireAt + **payUrl** | **仅展示 QR/链接**；手机扫码后用户显式确认；PC 可轮询订单状态 |
| ticket_issued | ticketCode + qr | view_order |
| error | code + message + alternatives? | retry / reselect |
| ask | slot + suggestions[] | fill_slot |

### 5.7 AgentTurn API

**接口：** `POST /api/v1/agent/turns`  
**权限：** 浏览可匿名；涉及 lock/order 须用户登录  

**入参：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | string | 否 | 空则服务端创建；前端宜自持并回传（与 §8.1 一致） |
| message | string | 否 | 用户文本；与 cardAction 至少一 |
| cardAction | object | 否 | `{ cardId, actionId, itemId?, draftPatch? }` |
| clientDraftVersion | number | 否 | 乐观并发；冲突 → `code=-1, errorCode=DRAFT_CONFLICT` |
| debug | boolean | 否 | `true` 时出参含 `toolTraces`（与 §8.1 一致） |

**出参（data）— 与 §8.1 正式 API 一致：**
| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | string | |
| replyText | string | 助手话术 |
| draft | BookingDraft | 最新草稿 |
| cards | Card[] | 本轮卡片 |
| progress | object | `{ steps: string[], currentIndex: int, state: string }`（勿再用 string[]） |
| needLogin | boolean | 下一步需登录时 true |
| toolTraces | object[] | 可选，调试开关开启时返回 |
| events | string[] | 埋点：intent_parsed 等 |

### 5.8 核心场景时序

#### 场景 A：模糊意图

```text
User: 周末想看个喜剧
  NLP → intent=buy_ticket, genre=喜剧, date≈weekend
  Plan → SHOW_CARDS(SelectMovie)
  Tool searchMovies(genre=喜剧)
  Card movie_list + listContext 写入
User: 点「选这部」
  cardAction → movieId 写入 → Plan SelectCinema → searchCinemas(lat,lng,…) → cinema_list
```

#### 场景 B：信息完备跳步（P1）

```text
User: 两张明天下午最近《流浪地球 3》
  抽槽齐全 → 解析 movieId 唯一
  SKIP: 不追问选片
  searchCinemas(lat,lng,movieId) → 若 Top1 置信高可自动选或推 1–3 家
  listShows(cinemaId,movieId,date) → Agent 按 timeWindow=afternoon 过滤 startTime → show_list 或唯一 show 直达
  recommendSeats({showId,count:2,…}) → seat_plans
```

#### 场景 C：锁座失败兜底

```text
lockSeats → SEAT_TAKEN
  Plan 留在 SelectSeat
  recommendSeats 再算相邻方案
  Card error + seat_plans(alternatives)
```

#### 场景 D：支付（禁止静默 · 二维码）

```text
ConfirmOrder 卡「去支付」
  → draft.state = PayMock + 推 pay_mock 卡（含 payUrl / 金额 / expireAt）
PC 支付页
  → GET /api/v1/orders/{orderId}/pay-qrcode → 展示支付 QR
  → 轮询 GET /api/v1/orders/{orderId} 直至 status=issued（pollIntervalMs 见 PayQrVO）
手机扫码
  → 打开 {publicBase}/m/pay/{orderId}?t=payToken（前端 H5，路由见前端系分）
  → GET /api/v1/orders/{orderId}/pay-session?t=… → 展示摘要
用户点击「确认付款」（必须显式点击，禁止静默）
  → POST /api/v1/orders/{orderId}/pay
     Header: X-Pay-Token 或 Authorization（二选一）
     Body: { channel: "mobile_qr" }
  → 出票：ticketCode + qrPayload（签名取票 QR）
PC / 手机
  → 展示出票页；可选 POST turn { cardAction: payment_done } 刷新 Agent 出票卡
```

> Agent **不得**调用 pay Tool；`payToken` **不得**自动触发 pay，仅用于打开 H5 与 pay-session 鉴权。

### 5.9 RAG（检索增强）— 落地规格

Agent **不**用 LLM 编造影片/场次/座位；**库存与排片事实**一律经子 Agent Tool → 中台。RAG **仅**覆盖 FAQ / 购票政策 / 退改签说明等非结构化文档。

#### 5.9.1 三层检索分工

| 层 | 来源 | 实现 | 用途 |
|----|------|------|------|
| 结构化检索（主路径） | Movie/Cinema/Show/Reco Use Case | LangChain Tools → REST | 槽位解析、列表卡、锁座 |
| 文档检索 RAG（P1） | FAQ/政策 Markdown | **Chroma** + Embeddings + `retrieve_faq` | 闲聊/政策问答 |
| 记忆检索 | `agent_message` 近 N 轮 + Draft 摘要 | SQLAlchemy + Draft GET | 指代、改口 |

#### 5.9.2 技术选型

| 项 | 选择 | 说明 |
|----|------|------|
| 向量库 | **Chroma**（持久化目录） | `CHROMA_PERSIST_DIR`，默认 `storage/vectorstore/chroma` |
| Collection | `faq_v1` | 单集合；按 `doc_type` metadata 过滤 |
| Embeddings | `text-embedding-3-small`（有 Key） | 无 Key → ingest 跳过，`retrieve_faq` 返回 `[]`，Turn 不失败 |
| Retriever | `similarity_search_with_score`，`top_k=3`（`RAG_TOP_K`） | 可选 score 阈值：余弦距离归一后 `< 0.45` 丢弃（按实际 embedding 调） |
| 语料目录 | `RAG_DOCUMENTS_DIR`（默认 `storage/documents`） | `.md` / `.txt`；PDF 可选（需额外 loader） |

#### 5.9.3 切片（Chunking）方法 — **必须按此实现**

采用 **「FAQ 优先整段 + 长文递归切分」** 双策略，避免中文被按空格误切。

**策略 A — FAQ / Q&A（推荐主语料形态）**

1. 语料用 Markdown，按二级标题或显式分隔符切「一问一答」为 **1 个 Document**：
   - 分隔：`## ` 标题，或行 `---`，或 `Q:`/`A:` 对
2. **不做二次切片**：每个 Q&A = 1 chunk（通常 100–600 汉字）
3. `page_content` = `问题 + "\n" + 答案`（检索时问题与答案同向量，命中率更高）
4. metadata：`{"source": "refund.md", "doc_type": "faq", "section": "退票规则", "chunk_id": "refund#1", "content_hash": "<sha256前16>"}`

**策略 B — 长政策文档（无清晰 Q&A 时）**

使用 LangChain `RecursiveCharacterTextSplitter`，**中文分隔符优先**：

```python
from langchain_text_splitters import RecursiveCharacterTextSplitter

splitter = RecursiveCharacterTextSplitter(
    chunk_size=500,          # 字符，约 250–400 汉字
    chunk_overlap=80,        # 保留跨段指代
    separators=[
        "\n## ", "\n### ", "\n\n", "\n",
        "。", "！", "？", "；", " ", ""
    ],
    length_function=len,
)
```

| 参数 | 值 | 理由 |
|------|-----|------|
| `chunk_size` | **500** | 政策短段够用；过大易引入噪声 |
| `chunk_overlap` | **80** | ~15% overlap，避免「跨句规则」被切断 |
| separators | 标题 → 空行 → 句读 | 适配中文无空格分词 |

**禁止：**

- 按固定 token 窗口硬切且无 overlap（易切断条文编号）
- 把整份影片/场次 JSON dump 进向量库（事实走 Tools）
- 将含 `lockId`/`orderId` 的会话日志向量化后当「已完成购票」证据

#### 5.9.4 入库（Ingest）流程

```text
启动 lifespan 或脚本 python -m app.scripts.ingest_rag
  1. 扫描 RAG_DOCUMENTS_DIR
  2. 按文件 content_hash 判断是否变更（幂等）
  3. 策略 A 或 B 产出 Documents
  4. embeddings.embed_documents → Chroma upsert（id = chunk_id）
  5. 删除源文件已不存在的旧 chunk（可选，MVP 可整集重建）
```

索引时机：集合为空则启动时全量 ingest；日常用管理脚本增量。样例 FAQ 至少含：退改签、取票、锁座 TTL=15min、禁止静默支付说明。

#### 5.9.5 检索 Tool：`retrieve_faq`

**入参（内部 / Tool）**

```json
{
  "query": "锁座多久会释放？",
  "topK": 3,
  "filters": { "doc_type": "faq" }
}
```

**出参**

```json
{
  "hits": [
    {
      "source": "booking_policy.md",
      "refId": "booking_policy#ttl",
      "score": 0.88,
      "snippet": "锁座成功后座位保留 15 分钟，超时自动释放……"
    }
  ],
  "suggestedTools": [],
  "fallback": false
}
```

| 规则 | 说明 |
|------|------|
| 调用时机 | `faq_node`；或 Planner 判定政策问句；主购票路径**不强制**每轮都 RAG |
| 写入 Draft | **禁止**用 hit 直接写 `lockId`/`orderId`/`showId` |
| 空结果 | `hits=[]`，`fallback=true` → 短回「我不太确定，建议查看帮助或转人工」+ 拉回购票 Chip |
| 与结构化冲突 | 若用户问「还有票吗」→ **必须** `listShows`/`getSeatMap`，禁止仅用 RAG 回答 |

#### 5.9.6 配置项

| Env | 默认 | 说明 |
|-----|------|------|
| `CHROMA_PERSIST_DIR` | `storage/vectorstore/chroma` | 持久化 |
| `RAG_DOCUMENTS_DIR` | `storage/documents` | 语料 |
| `RAG_TOP_K` | `3` | |
| `RAG_CHUNK_SIZE` | `500` | 策略 B |
| `RAG_CHUNK_OVERLAP` | `80` | 策略 B |
| `EMBEDDING_MODEL` | `text-embedding-3-small` | |

硬约束汇总：RAG hit **不得**直接写入 `lockId`/`orderId`；必须再调 SeatAgent/OrderAgent 才能改变购票事实。

### 5.10 出站网络治理（超时 / 错误）

所有 LangChain Tool → ticket-api REST，统一经 **httpx** + 超时/重试/错误映射包装：

| 策略 | 规则 |
|------|------|
| 超时 | 只读 Tool 默认 800–1200ms；写 Tool 1500ms；LLM 1500ms，超时 → 规则 NLP 降级 |
| 重试 | 仅幂等只读可重试 1 次；写操作默认不重试（靠幂等键） |
| 错误映射 | 中台 `data.errorCode` 原样进 `toolTraces` + 用户可读 `error` 卡；`INTERNAL_ERROR` → 降级文案 |
| 熔断 | 同 Tool 连续失败 ≥5 → 本会话短路 30s，推 ask/error |
| 并发 | 只读可并行；写（lock/createOrder）串行 |

**Tool 失败出参 JSON 示例**

```json
{
  "success": false,
  "tool": "lockSeats",
  "code": "SEAT_TAKEN",
  "message": "seats already taken: sm1:6:7",
  "details": { "conflictSeatIds": ["sm1:6:7"], "showId": "s900" },
  "latencyMs": 42,
  "retried": false
}
```

### 5.11 幂等性

| 操作 | 幂等键 | 行为 |
|------|--------|------|
| lockSeats | Header `Idempotency-Key` | 同键同参数 → 返回同一 `lockId`；参数冲突 → `CONFLICT` |
| createOrder | 天然：`lock_id` UNIQUE | 同 lock 重复创建 → 返回已有订单 |
| unlock / cancel | 状态机幂等 | 已释放/已取消再调仍返回 `code=200` |
| mockPay | Header `Idempotency-Key` + orderId | 已 `issued` 再付 → 返回同一出票结果（不重复扣） |
| payQrToken | Redis `pay:token:{jti}` + JWT `exp` | 同 token 仅可成功 pay 一次；过期 `PAY_TOKEN_EXPIRED` |
| Agent Turn | `sessionId` + `clientDraftVersion` | Draft CAS；冲突 `DRAFT_CONFLICT` |

Redis：`idem:{scope}:{key}` → 响应摘要，TTL 24h。

### 5.12 禁止静默支付（控制面）

| 检查点 | 规则 |
|--------|------|
| Tool 注册表 | 启动断言：白名单 **不含** `payMock` / `mockPay` |
| Planner | 不得产出 `CALL_TOOL(pay*)`；ConfirmOrder 只推 `pay_mock` **展示卡** |
| CardComposer | `pay_mock` 卡 actions **不含**自动支付；仅展示 **payUrl/QR** + 引导手机扫码或 PC 轮询 |
| 审计 | 若检测到 Agent 路径触发 pay Use Case → 拒绝并打 `SILENT_PAY_BLOCKED` |
| LangChain Tool 扫描（Python 启动） | 注册 Tool **不含** pay*；与 §5.5 白名单一致 |
| LangChain Agent | **禁止**纯 ReAct 自动循环调写 Tool；由 **Planner 确定性** 决定 Tool 批次；LC 不直连 DB |
| 中台审计 | Agent 服务 HTTP 调 pay 接口时，中台仍校验用户 JWT（无 M2M 旁路） |

### 5.13 Python LangChain Agent 服务（独立部署）

> **ticket-agent** 为独立 Python 进程，**不**与 Spring Boot 同 JVM。LangChain 只存在于该服务；Java 票务中台 **零 LangChain 依赖**。

#### 5.13.1 职责划分

| ticket-agent（Python） | ticket-api（Java 中台） |
|------------------------|-------------------------|
| `POST /api/v1/agent/turns` | 全部业务 REST（§7） |
| LangChain NLP / RAG / Tool 编排 | 锁座、订单、支付、库存真相 |
| Planner、CardComposer（确定性代码） | **BookingDraft** 落库（`/booking-drafts`） |
| httpx 调中台 Tool（透传用户 JWT） | JWT 签发与校验、用户 RBAC |
| **自有库** SQLAlchemy：`agent_session` / `agent_message` | **禁止** 嵌入 LLM；**不再**存对话消息 |
| — | **废除** `X-Internal-Api-Key` |

**一句话：** Python 负责「听懂、编排与对话记忆」；Java 负责「交易与购票 Draft」。

#### 5.13.2 推荐目录结构（Python）

```text
ticket-agent/
├── app/
│   ├── main.py                 # FastAPI + lifespan（DB / Chroma / PostgresSaver.setup）
│   ├── turn/process_turn.py    # 编排入口 → booking_graph.ainvoke
│   ├── memory/
│   │   ├── checkpointer.py     # PostgresSaver | MemorySaver
│   │   └── chat_history.py     # 近 N 轮 → graph input
│   ├── langchain/
│   │   ├── graph/              # §3.3 BookingGraph + 子 Agent nodes
│   │   ├── tools/              # @tool → httpx 中台
│   │   └── rag/
│   │       ├── chroma_store.py # 工厂 + ingest
│   │       ├── chunking.py     # §5.9.3 策略 A/B
│   │       └── retriever.py    # retrieve_faq
│   ├── planner/planner.py      # 确定性状态机（§5.3）
│   ├── nlp/langchain_nlp.py    # Structured Output
│   ├── composer/card_composer.py
│   ├── db/                     # SQLAlchemy：agent_session / agent_message
│   └── clients/ticket_api.py   # httpx：Draft + Tools（透传 JWT）
├── storage/
│   ├── documents/              # FAQ 语料
│   └── vectorstore/chroma/
├── tests/
└── requirements.txt            # langchain, langgraph, chromadb, httpx, fastapi, sqlalchemy, …
```

#### 5.13.3 LangChain Tool → 中台 REST 映射

每个 Tool **仅** HTTP 转发；参数/响应与 §3.2 JSON 对齐。

| Tool name | 中台 HTTP | 鉴权 |
|-----------|-----------|------|
| searchMovies | `GET /api/v1/movies?...` | 透传用户 `Authorization` |
| lockSeats | `POST /api/v1/locks` | 用户 JWT + `Idempotency-Key` |
| createOrder | `POST /api/v1/orders` | 用户 JWT |
| … | 见 §5.5 | 写操作必透传用户 JWT |

**Python Tool 示例（伪代码）：**

```python
@tool
def lock_seats(show_id: str, seat_ids: list[str], session_id: str | None = None) -> dict:
    return ticket_api.post(
        "/locks",
        json={"showId": show_id, "seatIds": seat_ids, "sessionId": session_id},
        headers={"Authorization": ctx.user_jwt, "Idempotency-Key": ctx.idem_key()},
    )
```

**禁止注册：** `payMock`, `mockPay`, `pay` 及任何支付路径。

#### 5.13.4 跨服务调用与鉴权

| 调用方 | 被调方 | Header |
|--------|--------|--------|
| 浏览器 | ticket-agent | `Authorization: Bearer {userJwt}`（Turn 入口） |
| ticket-agent | ticket-api（Tool） | **透传** 同一 `Authorization`（锁座/下单须本人） |
| ticket-agent | ticket-api（Draft） | 透传 `Authorization`（本人/会话） |
| 浏览器 | ticket-api（购票/支付） | 用户 JWT；**不经过** Agent |

- Agent 服务 **不得** 持有用户 refresh token；只透传 access token。
- **废除** `X-Internal-Api-Key`：对话消息不经中台，无需 M2M。
- 业务 Tool 仍须用户 JWT。

#### 5.13.5 Draft / Memory 持久化（三件套）

| 层级 | 存储 | 职责 |
|------|------|------|
| 购票槽位真相 | 中台 `BookingDraft` REST | 与 C 端 hydrate 共用；CAS `version` |
| 对话可展示历史 | Agent DB `agent_message` | 前端历史列表、指代上下文 |
| 图短期状态 | LangGraph **PostgresSaver**（`thread_id=sessionId`） | intent/plan 中间态；与 messages **不互相替代** |

每轮 Turn：

```text
1. GET  /api/v1/booking-drafts/{sessionId}          # 中台
2. SELECT agent_message … LIMIT 20                  # Agent DB
3. graph.ainvoke(..., config={thread_id: sessionId}) # PostgresSaver
4. （可选）retrieve_faq → Chroma                     # §5.9
5. Tools → 中台 REST
6. PUT  /api/v1/booking-drafts/{sessionId}          # 中台 CAS
7. INSERT agent_message (user + assistant)          # Agent DB
```

Draft 依赖清空、version 冲突 `DRAFT_CONFLICT` 由 **中台 PUT** 执行；Python 收到冲突后拉最新 draft 重试或返回给前端。`DATABASE_URL` 必填 PostgreSQL；`CHECKPOINT_DATABASE_URL` 缺省同库。

#### 5.13.6 RAG（P1，仅 Python）

完整切片 / 入库 / 检索规格见 **§5.9**。摘要：

- 影片/场次/座位 **事实**：必须 Tool → 中台 REST
- FAQ / 购票政策：**Chroma**；FAQ 用策略 A（一问一答一块）；长文用 `RecursiveCharacterTextSplitter(chunk_size=500, overlap=80, 中文 separators)`
- **不得** 编造或用向量命中直接写入 `lockId`/`orderId`

#### 5.13.7 部署与网关

```mermaid
flowchart LR
  FE[浏览器]
  GW[API Gateway]
  AG[ticket-agent Python]
  API[ticket-api Java]
  PG[(中台 PostgreSQL)]
  ADB[(Agent DB)]
  RD[(Redis)]

  FE --> GW
  GW -->|/api/v1/agent/*| AG
  GW -->|/api/v1/* 其余| API
  AG -->|httpx Tools + Draft| API
  AG --> ADB
  API --> PG
  API --> RD
  AG -.->|LLM API| LLM[OpenAI / Ollama]
```

#### 5.13.8 中台需新增/调整的 API（v4.4）

| 变更 | 说明 |
|------|------|
| `POST /agent/turns` | **从 Java 移除**（或 Java 仅 307 转发至 Agent 服务） |
| `GET/POST …/messages` | **由 Agent 自有库实现**；中台**不再**提供 AppendMessages / M2M Key |
| 其余 §7 API | **不变**；Agent Tool 直接复用；Draft 仍在中台 |

#### 5.13.9 不必因 LangChain 改动的部分

| 项 | 原因 |
|----|------|
| Turn 入参/出参 JSON | 契约稳定，Python 实现同一 `AgentTurnResponse` |
| BookingDraft 字段 / 状态机 §4 | 中台仍为真相源 |
| 锁座/订单/支付事务 §6 | 仍在 Java |
| Card 协议 §5.6 | Python CardComposer 输出同一 schema |
| 禁止静默支付 §5.12 | Python 侧同样遵守 |

---

## 6. 数据库设计与 ER

本章为逻辑 ER、物理 DDL、Redis 与核心事务。

### 1. 设计原则

| # | 原则 | 说明 |
|---|------|------|
| 1 | 中台唯一真相 | 座位占用以 `seat_status` 为准；订单以 `order_ticket` 为准；Draft 不得冒充库存 |
| 2 | 快照不可变 | 下单后影片/影院/厅/开场时间/座位写入订单快照列，后续改排片不影响已出票 |
| 3 | 一锁一单 | `order_ticket.lock_id` UNIQUE；同一 `seat_lock` 只能生成一张有效单 |
| 4 | 乐观 + 悲观并用 | Draft 用 `version` CAS；锁座用行级 `SELECT … FOR UPDATE` |
| 5 | TTL 双写 | DB `expire_at` 为权威截止；Redis key 加速过期触发 |
| 6 | **无物理外键** | DDL **不**建 `FOREIGN KEY`；用同名逻辑关联列 + 索引表达关系；完整性由应用层校验 |

**命名约定：** 表名小写下划线；主键业务 ID（`VARCHAR`）便于演示与跨表引用；时间一律 `TIMESTAMPTZ(3)`；金额 `DECIMAL(10,2)`。

**DDL 方言：** 以下 SQL 为 **PostgreSQL 16** 语法（`TIMESTAMPTZ` / `JSONB` / `CHECK`；索引可随 Flyway 脚本单独 `CREATE INDEX`）。

**逻辑关联约定：** 列注释或文档写作 `逻辑→表.列`；查询用应用 JOIN / 二次查询；删除/更新由 Use Case 保证级联语义（非 DB CASCADE）。

---

### 2. ER 图（逻辑完整）

**可编辑大图（draw.io，与下方 mermaid 字段/关系一致）：** [`docs/diagrams/booking-er.drawio`](diagrams/booking-er.drawio)

> 若 `~/Code/...` 路径报 `EACCES`，请用工作区相对路径 `docs/diagrams/booking-er.drawio` 打开，或使用副本 `/home/rei/diagrams/booking-er.drawio`。

```mermaid
erDiagram
  USER_ACCOUNT ||--o| USER_PROFILE : has_profile
  USER_ACCOUNT ||--o{ WANT_SEE : collects
  USER_ACCOUNT ||--o{ SEAT_LOCK : locks
  USER_ACCOUNT ||--o{ ORDER_TICKET : places
  USER_ACCOUNT ||--o{ AGENT_SESSION : owns
  CINEMA ||--o{ USER_ACCOUNT : staff_of

  MOVIE ||--o{ SHOW : schedules
  MOVIE ||--o{ WANT_SEE : wanted
  MOVIE ||--o| RECO_STATS : stats

  CINEMA ||--o{ HALL : contains
  CINEMA ||--o{ SHOW : hosts
  HALL ||--|| SEAT_MAP : layout
  SEAT_MAP ||--o{ SEAT : has

  SHOW ||--o{ SEAT_STATUS : occupancy
  SEAT ||--o{ SEAT_STATUS : state_of
  SHOW ||--o{ SEAT_LOCK : locked_by
  SHOW ||--o{ ORDER_TICKET : ordered

  SEAT_LOCK ||--o| ORDER_TICKET : creates
  AGENT_SESSION ||--o{ AGENT_MESSAGE : logs
  AGENT_SESSION ||--o| SEAT_LOCK : may_ref
  AGENT_SESSION ||--o| ORDER_TICKET : may_ref

  USER_ACCOUNT {
    varchar user_id PK
    varchar nickname UK
    varchar phone UK
    varchar password_hash
    varchar role
    varchar cinema_id FK
    smallint status
  }
  USER_PROFILE {
    varchar user_id PK
    json prefer_genres_json
    varchar prefer_row
    varchar prefer_side
  }
  MOVIE {
    varchar movie_id PK
    varchar title
    varchar status
    decimal rating
    json genres_json
  }
  CINEMA {
    varchar cinema_id PK
    varchar city_id
    varchar name
    decimal lat
    decimal lng
  }
  HALL {
    varchar hall_id PK
    varchar cinema_id FK
    varchar seat_map_id FK
  }
  SEAT_MAP {
    varchar seat_map_id PK
    varchar cinema_id FK
    int rows_n
    int cols_n
  }
  SEAT {
    varchar seat_id PK
    varchar seat_map_id FK
    int graph_row
    int graph_col
    int row_no
    int col_no
    varchar seat_name
    varchar seat_type
    varchar zone
    varchar couple_pair_id
  }
  SHOW {
    varchar show_id PK
    varchar movie_id FK
    varchar cinema_id FK
    varchar hall_id FK
    timestamptz start_time
    decimal price
    varchar status
  }
  SEAT_STATUS {
    varchar show_id PK
    varchar seat_id PK
    varchar status
    varchar lock_id
    varchar user_id
    timestamptz expire_at
  }
  SEAT_LOCK {
    varchar lock_id PK
    varchar show_id FK
    varchar user_id FK
    json seat_ids_json
    varchar status
    timestamptz expire_at
  }
  ORDER_TICKET {
    varchar order_id PK
    varchar user_id FK
    varchar show_id FK
    varchar lock_id UK
    varchar status
    decimal amount
    varchar ticket_code
  }
  AGENT_SESSION {
    varchar session_id PK
    varchar user_id FK
    varchar state
    json draft_json
    bigint version
  }
  AGENT_MESSAGE {
    varchar message_id PK
    varchar session_id FK
    varchar role
    text content
    json cards_json
  }
  RECO_STATS {
    varchar movie_id PK
    int week_orders
    int week_clicks
    decimal hot_score
  }
  WANT_SEE {
    varchar user_id PK
    varchar movie_id PK
  }
  RECO_WEIGHT {
    varchar city_id PK
    decimal w_orders
    decimal w_clicks
    decimal w_rating
    decimal w_fresh
  }
```

> **无物理外键：** 上图关系均为**逻辑关联**；DDL 仅建主键/唯一键/普通索引，不建 `FOREIGN KEY`。应用层保证引用存在与级联语义。

#### 2.1 关系 cardinality 说明

| 关系 | 基数 | 业务含义 |
|------|------|----------|
| Cinema → Hall | 1:N | 一家影院多个厅 |
| Hall → SeatMap | 1:1 | 每厅一张座位布局 |
| SeatMap → Seat | 1:N | 布局含全部座位定义 |
| Movie × Cinema → Show | N:M via Show | 排片实体 |
| Show × Seat → SeatStatus | 复合 PK | 场次级占用，开场时展开 |
| SeatLock → OrderTicket | 1:0..1 | 锁可未下单；下单后一对一 |
| User → AgentSession | 1:N | 可多会话；匿名 session.user_id 可空 |

---

### 3. 表设计（DDL 级）

#### 3.1 账号与鉴权

##### user_account

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| user_id | VARCHAR(32) | PK | 内部主键，如 `u1`；**不**用于登录 |
| nickname | VARCHAR(64) | NOT NULL, **UK** | 登录账号之一；展示名 |
| phone | VARCHAR(20) | NULL, **UK** | 登录账号之一；大陆 11 位手机号；未绑定时 NULL |
| password_hash | VARCHAR(128) | NOT NULL | bcrypt 等；种子用户预置演示密码 |
| role | VARCHAR(16) | NOT NULL | `user` / `staff` / `admin` |
| cinema_id | VARCHAR(32) | NULL, INDEX | **staff 必填**：所属影院（逻辑→cinema）；`user`/`admin` 必须为 NULL |
| avatar_url | VARCHAR(512) | NULL | |
| status | SMALLINT | NOT NULL DEFAULT 1 | 1=启用 0=禁用 |
| created_at | TIMESTAMPTZ(3) | NOT NULL | |
| updated_at | TIMESTAMPTZ(3) | NOT NULL | |

```sql
CREATE TABLE user_account (
  user_id        VARCHAR(32)  NOT NULL,
  nickname       VARCHAR(64)  NOT NULL,
  phone          VARCHAR(20)  NULL,
  password_hash  VARCHAR(128) NOT NULL,
  role           VARCHAR(16)  NOT NULL,
  cinema_id      VARCHAR(32)  NULL,
  avatar_url     VARCHAR(512) NULL,
  status         SMALLINT      NOT NULL DEFAULT 1,
  created_at     TIMESTAMPTZ(3)  NOT NULL,
  updated_at     TIMESTAMPTZ(3)  NOT NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT uk_user_nickname UNIQUE (nickname),
  CONSTRAINT uk_user_phone UNIQUE (phone),
  CONSTRAINT chk_user_role CHECK (role IN ('user','staff','admin')),
  CONSTRAINT chk_staff_cinema CHECK (
    (role = 'staff' AND cinema_id IS NOT NULL)
    OR (role IN ('user','admin') AND cinema_id IS NULL)
  )
);
CREATE INDEX idx_user_cinema ON user_account (cinema_id);
```

> `user_id` 仅系统内部与 JWT `sub` 使用；用户登录用 **nickname 或 phone**（见 §7.1）。
> **v4.8 影院隔离：** staff 通过 `cinema_id` 绑定唯一影院；运营写接口在 Service 层校验资源 `cinemaId == staff.cinema_id`（admin 跳过）。改绑影院仅 admin 可通过 `PUT /admin/users/{id}`；改绑后建议重新登录以刷新 JWT `cinemaId`。

##### user_profile

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| user_id | VARCHAR(32) | PK, 逻辑→user_account.user_id | |
| prefer_genres_json | JSONB | NOT NULL | `["喜剧","科幻"]` |
| prefer_row | VARCHAR(16) | NULL | `front`/`middle`/`back` |
| prefer_side | VARCHAR(16) | NULL | `center`/`aisle`/`edge` |
| updated_at | TIMESTAMPTZ(3) | NOT NULL | |

```sql
CREATE TABLE user_profile (
  user_id             VARCHAR(32) NOT NULL,
  prefer_genres_json  JSONB        NOT NULL,
  prefer_row          VARCHAR(16) NULL,
  prefer_side         VARCHAR(16) NULL,
  updated_at          TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (user_id)
);
```

##### JWT 鉴权（Access 对前端 + Refresh 仅服务端）

登录态采用 **双 Token**：前端只持有 **Access Token**；**Refresh Token 不落客户端**（仅存 Redis，按登录会话 `sid` 索引）。Access 过期时，网关/Filter **静默续期** 后继续执行业务，并在响应包络中附带新 Access Token。

| 项 | 约定 |
|----|------|
| Access Token | JWT，`Authorization: Bearer <accessToken>`；默认 TTL **3600s**；Claims：`sub`(userId)、`role`、`cinemaId`（staff 有值，其余可省略/null）、`sid`(登录会话)、`jti`、`exp` |
| Refresh Token | **仅服务端** Redis `auth:refresh:{sid}`；客户端**不**接收、**不**存储、**不**上传 |
| 静默续期 | Access **签名有效但已过期** → 用 JWT 内 `sid` 查 Redis Refresh → 签发新 Access → 正常返回业务 `data` + 包络可选字段 `accessToken` |
| 签名 | HS256（单服务）或 RS256；密钥来自配置中心 |
| 校验 | Filter 验签；已登出查 `auth:deny:{jti}`；`user_account.status=0` 拒绝续期 |
| 彻底失效 | Refresh 过期/被删、签名错误、无 `sid` → `401`，前端引导重新登录 |

**静默续期流程：**

```mermaid
sequenceDiagram
  participant FE as 前端
  participant F as AuthFilter
  participant R as Redis
  participant API as Controller

  FE->>F: 业务请求 Bearer accessToken(可能已过期)
  alt Access 未过期
    F->>API: 放行
    API-->>FE: { code:200, data, traceId }
  else Access 过期但签名合法
    F->>F: 解析 sid
    F->>R: GET auth:refresh:{sid}
    alt Refresh 有效
      R-->>F: userId, meta
      F->>F: 签发新 accessToken
      F->>API: 放行(注入新 token)
      API-->>FE: { code:200, data, accessToken, traceId }
    else Refresh 无效
      F-->>FE: 401 需重新登录
    end
  else 签名非法
    F-->>FE: 401
  end
```

**公开 API：**

```text
POST /auth/login     account + password → accessToken（无 refreshToken）
POST /auth/logout    Bearer accessToken  → 删 auth:refresh:{sid}；可选 deny jti
GET  /auth/me        Bearer accessToken  → 用户信息（可走静默续期）
```

> **无** `POST /auth/refresh` 公开接口；续期在任意需登录/带 Token 的请求中由 Filter 自动完成。

**Redis Key（见 §6.5）：** `auth:refresh:{sid}`、`auth:deny:{jti}`

---

#### 3.2 目录：影片 / 影院 / 厅 / 座位布局

##### movie

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| movie_id | VARCHAR(32) | PK | |
| title | VARCHAR(128) | NOT NULL | |
| poster_url | VARCHAR(512) | NOT NULL | |
| genres_json | JSONB | NOT NULL | `["科幻","冒险"]` |
| rating | DECIMAL(3,1) | NULL | 无评分 null |
| duration_min | INT | NOT NULL | |
| release_date | DATE | NOT NULL | |
| status | VARCHAR(32) | NOT NULL | `hot_showing`/`coming_soon`/`off` |
| description | TEXT | NOT NULL | |
| cast_text | VARCHAR(512) | NULL | |
| want_see_count | INT | NOT NULL DEFAULT 0 | |
| created_at / updated_at | TIMESTAMPTZ(3) | NOT NULL | |

```sql
CREATE TABLE movie (
  movie_id        VARCHAR(32)   NOT NULL,
  title           VARCHAR(128)  NOT NULL,
  poster_url      VARCHAR(512)  NOT NULL,
  genres_json     JSONB          NOT NULL,
  rating          DECIMAL(3,1)  NULL,
  duration_min    INT           NOT NULL,
  release_date    DATE          NOT NULL,
  status          VARCHAR(32)   NOT NULL,
  description     TEXT          NOT NULL,
  cast_text       VARCHAR(512)  NULL,
  want_see_count  INT           NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ(3)   NOT NULL,
  updated_at      TIMESTAMPTZ(3)   NOT NULL,
  PRIMARY KEY (movie_id),
  CONSTRAINT chk_movie_status CHECK (status IN ('hot_showing','coming_soon','off'))
);

CREATE INDEX idx_movie_status ON movie (status);
CREATE INDEX idx_movie_title ON movie (title);
```

##### cinema

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| cinema_id | VARCHAR(32) | PK | |
| city_id | VARCHAR(32) | NOT NULL, INDEX | 默认 `city_sh` |
| name | VARCHAR(128) | NOT NULL | |
| address | VARCHAR(256) | NOT NULL | |
| lat / lng | DECIMAL(10,6) | NOT NULL | 影院坐标（WGS84）；与用户 lat/lng 算距离 |
| created_at / updated_at | TIMESTAMPTZ(3) | NOT NULL | |

```sql
CREATE TABLE cinema (
  cinema_id    VARCHAR(32)    NOT NULL,
  city_id      VARCHAR(32)    NOT NULL,
  name         VARCHAR(128)   NOT NULL,
  address      VARCHAR(256)   NOT NULL,
  lat          DECIMAL(10,6)  NOT NULL,
  lng          DECIMAL(10,6)  NOT NULL,
  created_at   TIMESTAMPTZ(3)    NOT NULL,
  updated_at   TIMESTAMPTZ(3)    NOT NULL,
  PRIMARY KEY (cinema_id)
);

CREATE INDEX idx_cinema_city ON cinema (city_id);
```

##### hall / seat_map / seat

###### seat_map（座位布局模板）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| seat_map_id | VARCHAR(32) | PK | 布局 ID，如 `sm_rect_1` / `sm_convex_1` |
| cinema_id | VARCHAR(32) | NOT NULL, INDEX | **所属影院**（staff 建图强制为本影院；逻辑→cinema） |
| rows_n | INT | NOT NULL | 画布包围盒行数（逻辑网格最大行，非实座行数） |
| cols_n | INT | NOT NULL | 画布包围盒列数 |
| screen_label | VARCHAR(32) | NOT NULL DEFAULT `银幕` | 银幕侧文案（运营画布展示用） |

###### hall（影厅）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| hall_id | VARCHAR(32) | PK | 影厅 ID |
| cinema_id | VARCHAR(32) | NOT NULL, 逻辑→cinema.cinema_id, INDEX | 所属影院 |
| name | VARCHAR(64) | NOT NULL | 厅名，如 `1号厅` / `IMAX厅` |
| seat_map_id | VARCHAR(32) | NOT NULL, 逻辑→seat_map.seat_map_id, INDEX | 该厅绑定的座位布局（MVP 约定一厅一图） |

###### seat（布局上的单个座位定义）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| seat_id | VARCHAR(64) | PK | **系统键**；建议 `seat_map_id:graph_row:graph_col`；锁座/下单用，对用户不可见 |
| seat_map_id | VARCHAR(32) | NOT NULL, 逻辑→seat_map.seat_map_id, INDEX | 所属布局 |
| graph_row | INT | NOT NULL | 画布行 `1..rows_n`；稀疏网格落点 |
| graph_col | INT | NOT NULL | 画布列 `1..cols_n` |
| row_no | INT | NOT NULL | 业务排号（对号入座，对齐椅背） |
| col_no | INT | NOT NULL | 业务座号（同排内从左到右编号，跳过缺口） |
| seat_name | VARCHAR(32) | NOT NULL | 展示文案，如 `6排7座`（= `{row_no}排{col_no}座`） |
| seat_type | VARCHAR(16) | NOT NULL | `normal` / `couple` / `disabled` |
| zone | VARCHAR(16) | NOT NULL | 座位图自定义分区 code（自由字符串，无枚举/正则）；同场同区同价，价在 `show_zone_price` |
| couple_pair_id | VARCHAR(32) | NULL, INDEX | 情侣座成对 ID；同 ID 恰好 2 座且须同批选中 |
| default_status | VARCHAR(16) | NOT NULL DEFAULT `available` | 建图默认态：`available` / `unavailable`（坏座/柱位等） |

> 某 `(graph_row, graph_col)` **无 seat 行** = 该格为空（过道/缺口/异形外轮廓），与 `default_status=unavailable`（有座但不可售）不同。

```sql
CREATE TABLE seat_map (
  seat_map_id   VARCHAR(32) NOT NULL,
  cinema_id     VARCHAR(32) NOT NULL,
  rows_n        INT         NOT NULL,
  cols_n        INT         NOT NULL,
  screen_label  VARCHAR(32) NOT NULL DEFAULT '银幕',
  PRIMARY KEY (seat_map_id)
);
CREATE INDEX idx_seat_map_cinema ON seat_map (cinema_id);

CREATE TABLE hall (
  hall_id      VARCHAR(32) NOT NULL,
  cinema_id    VARCHAR(32) NOT NULL,
  name         VARCHAR(64) NOT NULL,
  seat_map_id  VARCHAR(32) NOT NULL,
  PRIMARY KEY (hall_id)
);

CREATE INDEX idx_hall_cinema ON hall (cinema_id);
CREATE INDEX idx_hall_seat_map ON hall (seat_map_id);

CREATE TABLE seat (
  seat_id          VARCHAR(64) NOT NULL,
  seat_map_id      VARCHAR(32) NOT NULL,
  graph_row        INT         NOT NULL,
  graph_col        INT         NOT NULL,
  row_no           INT         NOT NULL,
  col_no           INT         NOT NULL,
  seat_name        VARCHAR(32) NOT NULL,
  seat_type        VARCHAR(16) NOT NULL,
  zone             VARCHAR(16) NOT NULL,
  couple_pair_id   VARCHAR(32) NULL,
  default_status   VARCHAR(16) NOT NULL DEFAULT 'available',
  PRIMARY KEY (seat_id),
  CONSTRAINT uk_map_graph UNIQUE (seat_map_id, graph_row, graph_col),
  CONSTRAINT uk_map_biz UNIQUE (seat_map_id, row_no, col_no),
  CONSTRAINT uk_map_seat_name UNIQUE (seat_map_id, seat_name),
  CONSTRAINT chk_seat_type CHECK (seat_type IN ('normal','couple','disabled')),
  CONSTRAINT chk_seat_default CHECK (default_status IN ('available','unavailable'))
);

CREATE INDEX idx_seat_map ON seat (seat_map_id);
CREATE INDEX idx_seat_couple ON seat (couple_pair_id);
```

**座位三坐标约定（ADR-0005，对齐猫眼）：**

| 字段 | 用途 |
|------|------|
| `seat_id` | 锁座/下单 `seatIds[]`、`BookingDraft.seatIds`；对用户不可见 |
| `seat_name` | 票面/底栏/订单展示；`"{row_no}排{col_no}座"` |
| `graph_row` / `graph_col` | 稀疏画布落点 |
| `row_no` / `col_no` | 业务排座号（建图时写入，对齐椅背） |

同一场次的 `seat_status.seat_id` 引用本表 PK。锁座 Body 传系统 `seatId`，服务端按 `show.seat_map_id` 解析。

##### 稀疏座位图与自定义建图（含凸形等异形厅）

**模型：包围盒 + 稀疏座位（Sparse Grid）+ 业务编号。**

| 概念 | 含义 |
|------|------|
| `rows_n` × `cols_n` | 布局**包围盒**（逻辑网格最大行列），不是「必须铺满的实心矩形」 |
| `seat` 表每一行 | 一个**真实存在**的座位；某 `(graph_row, graph_col)` **无记录 = 该格为空（缺口/过道/异形外轮廓）** |
| `row_no` / `col_no` / `seat_name` | 对号入座文案；**不等于**画布格号（见编号规则） |
| `default_status=unavailable` | 格子存在但不可售（柱子、坏座）；与「无座位行」不同——前端仍可占位画灰座 |
| 前端渲染 | 按包围盒画网格；无 `seat` 的格留空；有座的按 `status` 着色；文案用 `seatName` |

**凸形示例（银幕在上，`·` = 无座位，`#` = 有座位；括号内为默认自动编号后的 seatName）：**

```text
graphCol →  1 2 3 4 5 6 7 8
graphRow 1  · · · # # # · ·     ← 1排1座 1排2座 1排3座（非「1排4座」）
graphRow 2  · # # # # # # ·
graphRow 3  # # # # # # # #
graphRow 4  # # # # # # # #
```

对应建图时：`rows_n=4`，`cols_n=8`；只 INSERT 上图中 `#` 位置；购票按 `graphRow`/`graphCol` 落位，展示 `seatName`。

##### 业务编号规则（seatName 怎么来）

购票时**不算几何**，只拼字符串：

```text
seatName = f"{rowNo}排{colNo}座"
```

**默认自动编号（LTR；未显式传 rowNo/colNo 则应用）：**

```text
bizRow = 0
for gRow in 1..rows_n:
  cells = seats where graphRow==gRow, sort by graphCol asc
  if cells empty: continue          # 过道不占业务排号
  bizRow += 1
  for i, seat in enumerate(cells, start=1):
    seat.rowNo = bizRow
    seat.colNo = i                  # 只数实座，缺口不占号
    seat.seatName = f"{rowNo}排{colNo}座"
    seat.seatId = f"{seatMapId}:{gRow}:{graphCol}"  # 若未指定
```

- 显式传入的 `rowNo`/`colNo` **覆盖**自动编号（对齐椅背 / 单双号等）。
- 同图内 `(rowNo,colNo)`、`seatName`、`(graphRow,graphCol)`、`seatId` 均唯一。

**空排 / 排间过道：** 包围盒保留该 `graphRow`，但不建 seat → 自动编号跳过该行。

- **连座推荐：** MVP 同 `graphRow` 且 `graphCol` 连续、中间无缺口才算 together。

**自定义建图流程（运营 / staff|admin）：**

```text
1. 管理端画布：选 rows×cols → 点选增删座位（前端系分 §9.7）
2. 保存前自动编号（可手改 rowNo/colNo）→ POST /api/v1/seat-maps
3. POST /api/v1/halls         新建影厅并绑 seatMapId
4. 创建场次 show               携带 hall_id（冗余 seat_map_id）
5. 系统展开 seat_status       仅对 seat 表中的座位 INSERT 本场占用
6. 用户 GET …/seat-map        读包围盒 + 稀疏 seats[]（含 seatName）+ 本场 status
```

**规则与校验（建图 Use Case）：**

1. `seats` 非空；每个座位 `graphRow`/`graphCol` 落在 `[1..rows_n]`×`[1..cols_n]`。
2. `(graphRow, graphCol)`、`(rowNo, colNo)`、`seatName`、`seatId` 在同一 `seat_map` 内唯一。
3. `seat_type=couple` 必须成对：同 `couple_pair_id` 恰好 2 个座位，且建议同排相邻画布列。
4. 已有场次引用的 `seat_map` **禁止直接改座位集合**；需新图 + 换绑厅，或仅允许改未排片图。
5. 连座推荐：同排连续可售座才算 together。

**与种子数据关系：** MVP 预置 1 张矩形图 + 1 张凸形图（凸形须体现「画布列≠业务座号」）；正式运营走 §7.5.0。详见 **ADR-0005**。

---

#### 3.3 排片 show

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| show_id | VARCHAR(32) | PK | |
| movie_id | VARCHAR(32) | FK, INDEX | |
| cinema_id | VARCHAR(32) | FK, INDEX | |
| hall_id | VARCHAR(32) | FK | |
| seat_map_id | VARCHAR(32) | NOT NULL | 冗余，避免联查 |
| start_time / end_time | TIMESTAMPTZ(3) | NOT NULL | |
| price | DECIMAL(10,2) | NOT NULL | **冗余最低区价** `= MIN(show_zone_price.price)`；列表「¥xx起」；真相源为区价表 |
| status | VARCHAR(16) | NOT NULL | `on_sale` / `cancelled` |
| created_at / updated_at | TIMESTAMPTZ(3) | NOT NULL | |

**`show_zone_price`（场次×区单价）**

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| show_id | VARCHAR(32) | PK | |
| zone | VARCHAR(16) | PK | = `seat.zone` |
| price | DECIMAL(10,2) | NOT NULL, >0 | 该场次该区单价 |

创建场次时 `zone` 集合须与对应座位图 `DISTINCT seat.zone` **完全一致**。

```sql
CREATE TABLE show_zone_price (
  show_id  VARCHAR(32)   NOT NULL,
  zone     VARCHAR(16)   NOT NULL,
  price    DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (show_id, zone),
  CONSTRAINT chk_show_zone_price CHECK (price > 0)
);
```

```sql
CREATE TABLE show_schedule (
  show_id      VARCHAR(32)    NOT NULL,
  movie_id     VARCHAR(32)    NOT NULL,
  cinema_id    VARCHAR(32)    NOT NULL,
  hall_id      VARCHAR(32)    NOT NULL,
  seat_map_id  VARCHAR(32)    NOT NULL,
  start_time   TIMESTAMPTZ(3)    NOT NULL,
  end_time     TIMESTAMPTZ(3)    NOT NULL,
  price        DECIMAL(10,2)  NOT NULL,
  status       VARCHAR(16)    NOT NULL DEFAULT 'on_sale',
  created_at   TIMESTAMPTZ(3)    NOT NULL,
  updated_at   TIMESTAMPTZ(3)    NOT NULL,
  PRIMARY KEY (show_id),
  CONSTRAINT chk_show_status CHECK (status IN ('on_sale','cancelled'))
);

CREATE INDEX idx_show_cinema_movie_time ON show_schedule (cinema_id, movie_id, start_time);
CREATE INDEX idx_show_movie_time ON show_schedule (movie_id, start_time);
```

> 表名物理库用 `show_schedule`（规避 SQL 保留字 `SHOW`）；API / 领域模型仍称 Show，字段 `showId`。

---

#### 3.4 库存：seat_status / seat_lock

##### seat_status（场次级占用 — 锁座热点表）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| show_id | VARCHAR(32) | PK | |
| seat_id | VARCHAR(64) | PK | |
| status | VARCHAR(16) | NOT NULL | `available`/`locked`/`sold`/`unavailable` |
| lock_id | VARCHAR(32) | NULL | locked 时填充 |
| user_id | VARCHAR(32) | NULL | 锁归属 |
| expire_at | TIMESTAMPTZ(3) | NULL | locked 截止 |
| updated_at | TIMESTAMPTZ(3) | NOT NULL | |

```sql
CREATE TABLE seat_status (
  show_id     VARCHAR(32) NOT NULL,
  seat_id     VARCHAR(64) NOT NULL,
  status      VARCHAR(16) NOT NULL,
  lock_id     VARCHAR(32) NULL,
  user_id     VARCHAR(32) NULL,
  expire_at   TIMESTAMPTZ(3) NULL,
  updated_at  TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (show_id, seat_id),
  CONSTRAINT chk_seat_status CHECK (status IN ('available','locked','sold','unavailable'))
);

CREATE INDEX idx_seat_status_lock ON seat_status (lock_id);
CREATE INDEX idx_seat_status_expire ON seat_status (status, expire_at);
CREATE INDEX idx_seat_status_user ON seat_status (user_id, status);
```

**初始化：** 场次创建后，按该场 `seat_map_id` 下 **`seat` 表已有行** 全量 INSERT `seat_status`（稀疏：无座位行不生成库存）；`default_status=unavailable` 的座位直接写 `unavailable`，其余写 `available`。

##### seat_lock

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| lock_id | VARCHAR(32) | PK | |
| show_id | VARCHAR(32) | NOT NULL, INDEX | |
| user_id | VARCHAR(32) | NOT NULL, INDEX | |
| seat_ids_json | JSONB | NOT NULL | `["sm1:6:7","sm1:6:8"]` 系统 seatId 列表 |
| status | VARCHAR(16) | NOT NULL | `active`/`expired`/`consumed`/`released` |
| ttl_seconds | INT | NOT NULL | 默认 900 |
| expire_at | TIMESTAMPTZ(3) | NOT NULL | |
| session_id | VARCHAR(64) | NULL | 回写 Draft 用 |
| created_at / updated_at | TIMESTAMPTZ(3) | NOT NULL | |

```sql
CREATE TABLE seat_lock (
  lock_id         VARCHAR(32) NOT NULL,
  show_id         VARCHAR(32) NOT NULL,
  user_id         VARCHAR(32) NOT NULL,
  seat_ids_json   JSONB        NOT NULL,
  status          VARCHAR(16) NOT NULL,
  ttl_seconds     INT         NOT NULL,
  expire_at       TIMESTAMPTZ(3) NOT NULL,
  session_id      VARCHAR(64) NULL,
  created_at      TIMESTAMPTZ(3) NOT NULL,
  updated_at      TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (lock_id),
  CONSTRAINT chk_lock_status CHECK (status IN ('active','expired','consumed','released'))
);

CREATE INDEX idx_lock_show ON seat_lock (show_id);
CREATE INDEX idx_lock_user ON seat_lock (user_id);
CREATE INDEX idx_lock_expire ON seat_lock (status, expire_at);
```

---

#### 3.5 订单 order_ticket

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| order_id | VARCHAR(32) | PK | |
| user_id | VARCHAR(32) | NOT NULL, INDEX | |
| show_id | VARCHAR(32) | NOT NULL | |
| lock_id | VARCHAR(32) | NOT NULL, **UK** | 一锁一单 |
| movie_title | VARCHAR(128) | NOT NULL | 快照 |
| cinema_name | VARCHAR(128) | NOT NULL | 快照 |
| hall_name | VARCHAR(64) | NOT NULL | 快照 |
| start_time | TIMESTAMPTZ(3) | NOT NULL | 快照 |
| seat_ids_json | JSONB | NOT NULL | 快照系统 seatId[]；展示另存 seat_names 或联查 |
| unit_price | DECIMAL(10,2) | NOT NULL | **deprecated**：所选座均价；新客户端读 seat_price_snapshot |
| amount | DECIMAL(10,2) | NOT NULL | = Σ seat_price_snapshot.price |
| seat_price_snapshot | JSONB | NOT NULL | `[{seatId,zone,price,seatName?},...]` |
| status | VARCHAR(16) | NOT NULL | `pending_pay`/`issued`/`cancelled` |
| ticket_code | VARCHAR(64) | NULL | 出票后 |
| qr_payload | VARCHAR(256) | NULL | 取票 QR 签名载荷（见 §3.2.6） |
| pay_channel | VARCHAR(16) | NULL | `desktop_button` / `mobile_qr`；出票后填充 |
| expire_at | TIMESTAMPTZ(3) | NULL | 待支付截止（=锁 expire） |
| pay_at | TIMESTAMPTZ(3) | NULL | |
| cancel_reason | VARCHAR(64) | NULL | |
| session_id | VARCHAR(64) | NULL | |
| created_at / updated_at | TIMESTAMPTZ(3) | NOT NULL | |

```sql
CREATE TABLE order_ticket (
  order_id         VARCHAR(32)    NOT NULL,
  user_id          VARCHAR(32)    NOT NULL,
  show_id          VARCHAR(32)    NOT NULL,
  lock_id          VARCHAR(32)    NOT NULL,
  movie_title      VARCHAR(128)   NOT NULL,
  cinema_name      VARCHAR(128)   NOT NULL,
  hall_name        VARCHAR(64)    NOT NULL,
  start_time       TIMESTAMPTZ(3)    NOT NULL,
  seat_ids_json    JSONB           NOT NULL,
  unit_price       DECIMAL(10,2)  NOT NULL,
  amount           DECIMAL(10,2)  NOT NULL,
  status           VARCHAR(16)    NOT NULL,
  ticket_code      VARCHAR(64)    NULL,
  qr_payload       VARCHAR(256)   NULL,
  pay_channel      VARCHAR(16)    NULL,
  expire_at        TIMESTAMPTZ(3)    NULL,
  pay_at           TIMESTAMPTZ(3)    NULL,
  cancel_reason    VARCHAR(64)    NULL,
  session_id       VARCHAR(64)    NULL,
  created_at       TIMESTAMPTZ(3)    NOT NULL,
  updated_at       TIMESTAMPTZ(3)    NOT NULL,
  PRIMARY KEY (order_id),
  CONSTRAINT uk_order_lock UNIQUE (lock_id),
  CONSTRAINT chk_order_status CHECK (status IN ('pending_pay','issued','cancelled')),
  CONSTRAINT chk_pay_channel CHECK (pay_channel IS NULL OR pay_channel IN ('desktop_button','mobile_qr'))
);

CREATE INDEX idx_order_user_status ON order_ticket (user_id, status, created_at);
CREATE INDEX idx_order_expire ON order_ticket (status, expire_at);
```

---

#### 3.6 推荐 / 想看

###### want_see（用户想看）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| user_id | VARCHAR(32) | PK, 逻辑→user_account.user_id | 哪个用户 |
| movie_id | VARCHAR(32) | PK, 逻辑→movie.movie_id, INDEX | 哪部片 |
| created_at | TIMESTAMPTZ(3) | NOT NULL | 标记想看时间 |

> 复合主键 `(user_id, movie_id)`：同一用户对同一影片至多一条；取消想看即 DELETE 该行。`movie.want_see_count` 可异步或触发器维护计数。

###### reco_stats（每周热门 — 影片维度物化分）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| movie_id | VARCHAR(32) | PK, 逻辑→movie.movie_id | 哪部片 |
| week_orders | INT | NOT NULL DEFAULT 0 | 近 7 天购票订单数（原始计数，公式前可再归一化） |
| week_clicks | INT | NOT NULL DEFAULT 0 | 近 7 天详情/购票点击数 |
| rating_norm | DECIMAL(6,4) | NOT NULL DEFAULT 0 | 评分归一化到 0–1（如 8.5 分 → 0.85） |
| freshness | DECIMAL(6,4) | NOT NULL DEFAULT 0 | 新鲜度 0–1；上映越近越高；待映可用预售量替代 |
| hot_score | DECIMAL(12,4) | NOT NULL DEFAULT 0, INDEX DESC | 物化热门分；按 `reco_weight` 加权算出，供榜单排序 |
| computed_at | TIMESTAMPTZ(3) | NOT NULL | 本次重算时间 |

> 一行一片；定时 Job 重算后 UPSERT。`GET /reco/weekly-hot` 按 `hot_score DESC` 取 Top-N。

###### reco_weight（热门公式权重 — 城市维度）

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| city_id | VARCHAR(32) | PK | 城市 ID，如 `city_sh`；与 `cinema.city_id` 对齐 |
| w_orders | DECIMAL(6,4) | NOT NULL DEFAULT 0.4500 | 购票量权重 |
| w_clicks | DECIMAL(6,4) | NOT NULL DEFAULT 0.2500 | 点击量权重 |
| w_rating | DECIMAL(6,4) | NOT NULL DEFAULT 0.1500 | 评分权重 |
| w_fresh | DECIMAL(6,4) | NOT NULL DEFAULT 0.1500 | 新鲜度权重 |
| updated_at | TIMESTAMPTZ(3) | NOT NULL | 权重最后调整时间 |

> 每城一行；四权重之和通常为 1.0。MVP 可只 seed 一条 `city_sh` 默认权重。

```sql
CREATE TABLE want_see (
  user_id     VARCHAR(32) NOT NULL,
  movie_id    VARCHAR(32) NOT NULL,
  created_at  TIMESTAMPTZ(3) NOT NULL,
  PRIMARY KEY (user_id, movie_id)
);

CREATE INDEX idx_want_movie ON want_see (movie_id);

CREATE TABLE reco_stats (
  movie_id      VARCHAR(32)     NOT NULL,
  week_orders   INT             NOT NULL DEFAULT 0,
  week_clicks   INT             NOT NULL DEFAULT 0,
  rating_norm   DECIMAL(6,4)    NOT NULL DEFAULT 0,
  freshness     DECIMAL(6,4)    NOT NULL DEFAULT 0,
  hot_score     DECIMAL(12,4)   NOT NULL DEFAULT 0,
  computed_at   TIMESTAMPTZ(3)     NOT NULL,
  PRIMARY KEY (movie_id)
);

CREATE INDEX idx_reco_hot ON reco_stats (hot_score DESC);

CREATE TABLE reco_weight (
  city_id    VARCHAR(32)    NOT NULL,
  w_orders   DECIMAL(6,4)   NOT NULL DEFAULT 0.4500,
  w_clicks   DECIMAL(6,4)   NOT NULL DEFAULT 0.2500,
  w_rating   DECIMAL(6,4)   NOT NULL DEFAULT 0.1500,
  w_fresh    DECIMAL(6,4)   NOT NULL DEFAULT 0.1500,
  updated_at TIMESTAMPTZ(3)    NOT NULL,
  PRIMARY KEY (city_id)
);
```

**热门分公式（物化到 `hot_score`）：**

```text
hotScore = w_orders * weekOrders
         + w_clicks * weekClicks
         + w_rating * ratingNorm
         + w_fresh  * freshness
```

---

#### 3.7 Agent Session / Message（**部署在 ticket-agent 自有库**，非中台 PG）

> v4.7：下列表由 **FastAPI + SQLAlchemy** 管理；中台 PostgreSQL **不再**包含 `agent_message`。BookingDraft 仍以中台 `/booking-drafts` 为准（可与 session 仅共享 `session_id`）。

```sql
CREATE TABLE agent_session (
  session_id   VARCHAR(64)  NOT NULL,
  user_id      VARCHAR(32)  NULL,
  source       VARCHAR(16)  NOT NULL,
  state        VARCHAR(32)  NOT NULL,
  draft_json   JSONB         NOT NULL,
  version      BIGINT       NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ(3)  NOT NULL,
  updated_at   TIMESTAMPTZ(3)  NOT NULL,
  PRIMARY KEY (session_id),
  CONSTRAINT chk_session_source CHECK (source IN ('manual','agent','hybrid'))
);

CREATE INDEX idx_session_user ON agent_session (user_id);
CREATE INDEX idx_session_updated ON agent_session (updated_at);

CREATE TABLE agent_message (
  message_id   VARCHAR(64)  NOT NULL,
  session_id   VARCHAR(64)  NOT NULL,
  role         VARCHAR(16)  NOT NULL,
  content      TEXT         NOT NULL,
  cards_json   JSONB         NULL,
  events_json  JSONB         NULL,
  created_at   TIMESTAMPTZ(3)  NOT NULL,
  PRIMARY KEY (message_id),
  CONSTRAINT chk_msg_role CHECK (role IN ('user','assistant','system'))
);

CREATE INDEX idx_msg_session_time ON agent_message (session_id, created_at);
```

**`draft_json` 最小结构（与 API BookingDraftVO 对齐）：**

```json
{
  "sessionId": "sess_xxx",
  "userId": "u1",
  "source": "agent",
  "state": "SelectSeat",
  "intent": "buy_ticket",
  "movieId": "m100",
  "cinemaId": "c12",
  "showId": "s900",
  "count": 2,
  "seatIds": ["sm1:6:7", "sm1:6:8"],
  "lockId": null,
  "orderId": null,
  "listContext": { "type": "shows", "ids": ["s900", "s901"] },
  "version": 7,
  "updatedAt": "2026-07-28T14:00:00+08:00"
}
```

字段级定义见本文 §4。

---

### 4. 物理表清单总览

| # | 物理表 | 领域模块 | 预估行量（演示） |
|---|--------|----------|------------------|
| 1 | user_account | 用户管理 | ~10 |
| 2 | user_profile | 用户管理 | ~10 |
| 3 | movie | 影片管理 | ≥12 |
| 4 | cinema | 影院管理 | ≥5 |
| 5 | hall | 影院管理 | ~15 |
| 6 | seat_map | 座位管理 | ~5 |
| 7 | seat | 座位管理 | ~500/map |
| 8 | show_schedule | 场次管理 | 数百/周 |
| 9 | seat_status | 座位管理 | show×seat |
| 10 | seat_lock | 座位管理 | 高频写 |
| 11 | order_ticket | 订单/支付 | 中频写 |
| 12 | want_see | 影片/用户 | 稀疏 |
| 13 | reco_stats | 热门推荐系统 | = movie 数 |
| 14 | reco_weight | 热门推荐系统 | 每城 1 行 |
| 15 | agent_session | Agent Core | 会话级 |
| 16 | agent_message | Agent Core | 会话×轮次 |

#### 4.1 逻辑关联矩阵（无物理 FK）

| 从表.列 | 逻辑指向 | 索引 |
|---------|----------|------|
| user_profile.user_id | user_account.user_id | PK |
| user_account.cinema_id | cinema.cinema_id | idx_user_cinema（仅 staff） |
| hall.cinema_id | cinema.cinema_id | idx_hall_cinema |
| seat_map.cinema_id | cinema.cinema_id | idx_seat_map_cinema |
| hall.seat_map_id | seat_map.seat_map_id | idx_hall_seat_map |
| seat.seat_map_id | seat_map.seat_map_id | idx_seat_map |
| show_schedule.movie_id | movie.movie_id | idx_show_movie_time |
| show_schedule.cinema_id | cinema.cinema_id | idx_show_cinema_movie_time |
| show_schedule.hall_id | hall.hall_id | （随查） |
| seat_status.(show_id,seat_id) | show_schedule / seat | 复合 PK |
| seat_lock.show_id / user_id | show_schedule / user_account | idx_lock_* |
| order_ticket.lock_id | seat_lock.lock_id | uk_order_lock |
| order_ticket.user_id / show_id | user_account / show_schedule | idx_order_user_status |
| want_see.user_id / movie_id | user_account / movie | PK + idx_want_movie |
| reco_stats.movie_id | movie.movie_id | PK |
| agent_message.session_id | agent_session.session_id | idx_msg_session_time |
| agent_session.user_id | user_account.user_id | idx_session_user |

---

### 5. Redis Key 设计

| Key | 类型 | TTL | Value | 说明 |
|-----|------|-----|-------|------|
| `lock:ttl:{lockId}` | STRING | = 锁剩余秒 | `1` | 过期触发释放任务 |
| `seat:mux:{showId}:{seatId}` | STRING | 3–5s | `lockId` | 可选短互斥，防热点 |
| `draft:cache:{sessionId}` | STRING | 30m | Draft JSON | 热读；落库为准 |
| `auth:refresh:{sid}` | STRING | 7d | JSON `{userId,role,refreshJti}` | **Refresh 仅服务端**；登录写入；静默续期读；登出 DEL |
| `auth:deny:{jti}` | STRING | ≤ access 剩余 TTL | `1` | Access 黑名单（登出/强制下线） |
| `reco:weekly:{cityId}` | STRING | 1h | 榜单 JSON | 周热门缓存 |
| `reco:personal:{userId}` | STRING | 15m | 列表 JSON | 可选 |
| `idem:{scope}:{key}` | STRING | 24h | 响应摘要 JSON | 写操作幂等 |
| `pay:token:{jti}` | STRING | = payToken 剩余秒 | `orderId` | 支付 QR 防重放；pay 成功后 DEL |

**释放路径：** Redis keyspace notification **或** 每 30s 扫 `seat_lock`/`order_ticket` 中 `expire_at < now AND status IN (...)`。MVP 推荐定时扫描，部署更简单。

**配置项（`application.yml` → `ticket.*`）：**

```yaml
ticket:
  public-base-url: https://ticket.example.com   # 支付 H5 域名，生成 payUrl
  pay-qr-secret: ${PAY_QR_SECRET}               # payToken 签名
  qr-secret: ${TICKET_QR_SECRET}                # 取票 qrPayload HMAC
```

---

### 6. 核心事务

购票写路径四段事务：**锁座 → 下单 → 支付出票 → 过期释放**。下图总览，各节用时序图展开。

```mermaid
flowchart LR
  A[6.1 锁座] --> B[6.2 创建订单]
  B --> C[6.3 模拟支付]
  A --> D[6.4 过期释放]
  B --> D
  C --> E([出票完成])
  D --> F([座位回 available])
```

| 事务 | 触发 | 涉及表 | 失败码（典型） |
|------|------|--------|----------------|
| 6.1 锁座 | `POST /locks` | seat_status, seat_lock, agent_session? | `SEAT_TAKEN`, `COUPLE_RULE` |
| 6.2 下单 | `POST /orders` | seat_lock, order_ticket | `UNAUTHORIZED`, `LOCK_EXPIRED`, `CONFLICT` |
| 6.3 支付 | `POST /orders/{id}/pay` | order_ticket, seat_lock, seat_status, Redis pay:token | `LOCK_EXPIRED`, `PAY_TOKEN_*`, 幂等重试 OK |
| 6.4 释放 | 定时 Job / TTL | seat_status, seat_lock, order_ticket, draft | — |

---

#### 6.1 锁座（防超卖）

```mermaid
sequenceDiagram
  autonumber
  participant C as 客户端
  participant API as 锁座 API
  participant DB as PostgreSQL
  participant R as Redis

  C->>API: POST /locks<br/>showId, seatIds[], sessionId?
  API->>API: 解析 seatIds，校验情侣座成对
  API->>DB: BEGIN
  API->>DB: SELECT seat_status<br/>WHERE show_id AND seat_id IN (...)<br/>FOR UPDATE
  alt 任一 status ≠ available
    API->>DB: ROLLBACK
    API-->>C: code=-1 errorCode=SEAT_TAKEN
  else 全部可售
    API->>DB: INSERT seat_lock (active, expire_at)
    API->>DB: UPDATE seat_status → locked<br/>写 lock_id, user_id, expire_at
    opt 有 sessionId
      API->>DB: UPDATE agent_session.draft_json<br/>lockId, seatIds, expireAt, version++
    end
    API->>DB: COMMIT
    API->>R: SET lock:ttl:{lockId} EX 900
    API-->>C: 200 LockVO
  end
```

**前置校验**

| 步骤 | 条件 |
|------|------|
| 登录 | 须有效 JWT |
| 情侣座 | `seat_type=couple` 时同 `couple_pair_id` 须同批选中 |
| 行锁 | `seat_status` 复合主键 `(show_id, seat_id)` 行级锁 |

**并发：** 两用户争同一座，后到的事务在 `FOR UPDATE` 后看到 `status≠available` → 整单回滚，`SEAT_TAKEN`。

---

#### 6.2 创建订单

```mermaid
sequenceDiagram
  autonumber
  participant C as 客户端
  participant API as 订单 API
  participant DB as PostgreSQL

  C->>API: POST /orders { lockId, sessionId? }
  API->>DB: BEGIN
  API->>DB: SELECT seat_lock WHERE lock_id=? FOR UPDATE
  alt 锁无效
    API->>DB: ROLLBACK
    API-->>C: code=-1 errorCode=LOCK_EXPIRED（未登录则为 HTTP 401）
  else 锁有效
    API->>DB: 断言 active + 归属本人 + 未过期
    API->>DB: 断言无 order_ticket.lock_id 重复
    API->>DB: 读 show/movie/cinema/hall 拼快照
    API->>DB: INSERT order_ticket<br/>status=pending_pay, seat_ids_json 快照
    opt sessionId
      API->>DB: draft 写 orderId
    end
    API->>DB: COMMIT
    API-->>C: 200 OrderVO (pending_pay)
  end
```

**快照写入：** `movie_title`, `cinema_name`, `hall_name`, `start_time`, `seat_ids_json`, `amount` — 下单后改排片不影响本单。

**一锁一单：** `order_ticket.lock_id` UNIQUE；同 `lockId` 重复请求返回已有订单。

---

#### 6.3 模拟支付（唯一出票入口 · 二维码）

**PC 获取支付 QR：**

```mermaid
sequenceDiagram
  participant PC as PC 前端
  participant API as Pay API
  participant DB as PostgreSQL
  participant R as Redis

  PC->>API: GET /orders/{id}/pay-qrcode<br/>Authorization Bearer
  API->>DB: SELECT order_ticket<br/>pending_pay + 本人
  API->>API: 签发 payToken(jti, orderId, userId, exp)
  API->>R: SET pay:token:{jti} EX ttl
  API-->>PC: PayQrVO(payUrl, pollIntervalMs)
  PC->>PC: 渲染支付 QR；启动轮询 GET /orders/{id}
```

**手机扫码确认支付：**

```mermaid
sequenceDiagram
  autonumber
  participant M as 手机 H5
  participant API as Pay API
  participant DB as PostgreSQL
  participant R as Redis

  M->>API: GET /orders/{id}/pay-session?t=payToken
  API->>API: 验签 + Redis pay:token:{jti}
  API->>DB: 读 order 摘要
  API-->>M: PaySessionVO
  M->>M: 用户点击「确认付款」
  M->>API: POST /orders/{id}/pay<br/>X-Pay-Token + channel=mobile_qr
  API->>DB: BEGIN
  API->>DB: SELECT order_ticket FOR UPDATE
  alt 已 issued（幂等）
    API->>DB: COMMIT
    API-->>M: 200 原出票结果
  else pending_pay 且未过期
    API->>DB: SELECT seat_lock FOR UPDATE
    API->>DB: seat_status locked→sold
    API->>DB: seat_lock→consumed
    API->>DB: order→issued<br/>ticketCode, qrPayload, payAt, pay_channel
    opt sessionId
      API->>DB: draft.state→TicketIssued
    end
    API->>DB: COMMIT
    API->>R: DEL pay:token(jti)
    API->>R: DEL lock:ttl(lockId)
    API-->>M: OrderVO + qrPayload
  else 已过期
    API->>DB: ROLLBACK
    API-->>M: code=-1 errorCode=LOCK_EXPIRED
  end
```

**取票 QR 生成（Use Case 内，pay 成功分支）：**

```text
payAtUnix  = order.pay_at.toEpochSecond()
ticketCode = TKT-{yyyyMMdd}-{orderId suffix}
canonical  = v1|{orderId}|{userId}|{payAtUnix}|{ticketCode}
sig16      = hex(HMAC-SHA256(canonical, ticket.qr-secret))[0:16]
qrPayload  = MIAOYU|{canonical}|{sig16}
```

> Agent Tool 白名单**不含** pay；支付必须由用户在 H5/PC **显式点击**触发本接口。  
> PC 备用路径：仍可用 `Authorization: Bearer` + `channel=desktop_button` 直接 `POST /pay`（不扫码）。

**取票核验（可选 P0）：** `GET /tickets/verify?payload=` → 解析 `qrPayload` → 验签 → 查 `order.status=issued` → 返回 TicketVerifyVO。

---

#### 6.4 过期释放（定时 Job）

```mermaid
flowchart TB
  J[定时扫描 每 30s] --> Q{seat_lock<br/>status=active<br/>且 expire_at 小于 now}
  Q -->|每条| T[BEGIN 事务]
  T --> S1[seat_status: locked → available<br/>清 lock_id / user_id / expire_at]
  S1 --> S2[seat_lock: active → expired]
  S2 --> S3[order_ticket: pending_pay → cancelled<br/>cancel_reason=ttl]
  S3 --> S4{有 sessionId?}
  S4 -->|是| S5[draft 清 lock/order/seat<br/>state → SelectSeat]
  S4 -->|否| S6[COMMIT]
  S5 --> S6
  Q -->|无| J
```

**扫描条件（等价 SQL 思路）：**

```sql
-- seat_lock: status='active' AND expire_at < NOW()
-- 联动 order_ticket: 同 lock_id 且 status='pending_pay'

```

释放后用户需重新选座；已 `issued` 的订单不受影响。

---

### 7. 状态枚举对照

#### 7.1 座位 seat_status.status

```mermaid
stateDiagram-v2
  [*] --> available
  available --> locked: lockSeats
  locked --> available: unlock / TTL
  locked --> sold: mockPay
  available --> unavailable: 布局维护
  sold --> [*]
  unavailable --> [*]
```

#### 7.2 锁 seat_lock.status

| 值 | 含义 |
|----|------|
| active | 有效占用中 |
| released | 用户主动解锁 |
| expired | TTL 到期 |
| consumed | 已支付出票 |

#### 7.3 订单 order_ticket.status

```mermaid
stateDiagram-v2
  [*] --> pending_pay: createOrder
  pending_pay --> issued: pay
  pending_pay --> cancelled: cancel / TTL
  issued --> [*]
  cancelled --> [*]
```

---

### 8. 种子数据建议

| 数据 | 数量 / 内容 |
|------|-------------|
| 用户 | 见下表；密码均为 `demo123456`（bcrypt 落库） |

**种子用户（登录用 nickname 或 phone 均可）：**

| user_id | nickname | phone | role | 剧本 |
|---------|----------|-------|------|------|
| u1 | 演示用户甲 | 13800000001 | user | 普通购票 |
| u2 | 演示用户乙 | 13800000002 | user | 抢座冲突 |
| u_pref | 偏好用户 | 13800000003 | user | 喜剧+中排居中 |
| staff1 | 运营小王 | 13900000001 | staff | 建图；**cinema_id=c12**（仅本影院） |
| admin1 | 系统管理员 | 13900000099 | admin | 全平台；cinema_id=NULL |
| 影片 | ≥12：热映含喜剧/科幻；待映 ≥3 |
| 影院 | ≥5 |
| 座位图 | ≥2 张：1 张常规矩形（含 golden + couple）；**1 张凸形稀疏图**（验证缺口不建座） |
| 场次 | 每片每院每天 ≥4；含 afternoon 场次；至少 1 场绑凸形图 |
| 售罄剧本 | 1 个 show：仅剩边角或不可连座，测 compromise |
| reco_stats | 全片物化；周榜可验证排序 |

---

### 9. 迁移与演进（MVP）

| 阶段 | 动作 |
|------|------|
| M1 | Flyway 初始化 PostgreSQL 表；建目录+库存+订单表；**JWT 静默续期**；**seat-map 建图 API**；锁座/支付事务；**支付 QR + 取票 QR 签名**（`pay-qrcode` / `pay-session` / `pay` / `tickets/verify`） |
| M2 | 种子含凸形图；联调前端运营画布（前端系分 §9.7）与购票稀疏 SeatMap |
| M3 | reco_stats / reco_weight；定时重算 hot_score |
| M4 | Draft version CAS（中台）；agent_session/message 在 **Agent 自有库**（无 M2M） |
| M5+ | seat 热点可拆分库（远期） |

**不做（P2）：** 分库分表、CDC、真实票源同步表。

---

## 7. API 接口详细设计

Base URL：`/api/v1`。本章按模块列出 REST；**每个接口均给出入参 JSON 与出参 JSON**（Query/Path 用等价 JSON 示意）。**运营管理写接口**见 **§10**（须 **staff / admin** 角色）。

### 0. 通用约定

#### 0.1 统一响应包络（Spring Boot）

成功与失败共用同一 JSON 结构，由 **adapter 层统一产出**：

| 路径 | Spring Boot 机制 | Body.code |
|------|------------------|-----------|
| 成功 | Controller 返回 **VO**，经 `ResponseBodyAdvice`（或显式 `Result.ok(vo)`）包装 | **`200`** |
| 失败 | `@RestControllerAdvice` 全局异常处理，捕获校验/业务/系统异常后序列化 | **`-1`** |

**业务成败只看 Body 的 `code`（int）**：成功 **`200`**，失败 **`-1`**。子类型（如 `SEAT_TAKEN`）不进 `code`，放在 `data.errorCode` 与场景字段；**参数校验失败**走 Spring Boot Bean Validation 映射，见下。

> **文档约定：** 本章 JSON 示例字段旁 `// …` 仅为注释；实际 HTTP Body 为标准 JSON（不含注释）。**不使用** Spring Boot 默认 `BasicErrorController` 的 `{ timestamp, status, error, path }` 形态。

##### 成功

```json
{
  "code": 200,
  "message": "success",
  "data": {},
  "accessToken": null,
  "traceId": "tr_01HZXABCDEF"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | int | 是 | 成功固定 **`200`** |
| message | string | 是 | 人类可读说明 |
| data | object \| array \| null | 是 | 业务载荷 |
| accessToken | string \| null | 否 | **仅静默续期时**返回新 Access Token；否则省略或 `null` |
| traceId | string | 是 | 链路追踪 ID |

##### 失败（`@RestControllerAdvice` 统一报错格式）

所有未捕获异常与 `BusinessException` 均转为同一包络；HTTP 状态建议仍为 **200**（由客户端读 Body.code），鉴权失败可由 Security 直返 401/403。

**Bean Validation 校验失败**（`MethodArgumentNotValidException` / `BindException`）示例——即 Spring Boot 参数校验的标准输出形态：

```json
{
  "code": -1,
  "message": "count must be between 1 and 4",
  "data": {
    "errorCode": "VALIDATION_ERROR",
    "fieldErrors": [
      {
        "field": "count",
        "rejectedValue": 5,
        "message": "must be <= 4"
      }
    ]
  },
  "traceId": "tr_xxx"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | int | 是 | 失败固定 **`-1`** |
| message | string | 是 | 异常摘要（首条校验消息或业务文案） |
| data.errorCode | string | 失败时 | 异常分类，见 §0.4 |
| data.fieldErrors | array | 校验时 | 字段级错误；来自 `FieldError` / `ObjectError` |
| traceId | string | 是 | 链路追踪 ID |

**实现要点（adapter）：**

```text
@RestControllerAdvice
  ├─ MethodArgumentNotValidException / BindException → code=-1, errorCode=VALIDATION_ERROR, fieldErrors[]
  ├─ BusinessException（domain/application 抛出，含 errorCode）→ code=-1, data 带业务字段
  ├─ AccessDeniedException / AuthenticationException → errorCode=FORBIDDEN / UNAUTHORIZED
  └─ Exception → errorCode=INTERNAL_ERROR
```

业务 Use Case 抛 `BusinessException(errorCode, message, details)`，**不**在 Controller 手写失败 JSON。

#### 0.2 请求头

| Header | 必填 | 说明 |
|--------|------|------|
| `Content-Type` | Body 时 | `application/json` |
| `Authorization` | 需登录接口 | `Bearer <accessToken>`（JWT） |
| `X-Session-Id` | 否 | 与 booking session 对齐，便于埋点 |
| `X-City-Id` | 否 | 默认 `city_sh` |
| `X-Trace-Id` | 否 | 客户端传入则原样回传 |
| `Idempotency-Key` | 写接口建议 | 锁座/下单/支付幂等（见 §5.11） |
| `X-Pay-Token` | 手机 H5 支付 | `POST /orders/{id}/pay` 扫码支付；与 Path `orderId` 匹配（见 §7.6.2.3） |

#### 0.3 分页

| Query | 类型 | 默认 | 约束 | 说明 |
|-------|------|------|------|------|
| page | int | 1 | ≥1 | 从 1 起 |
| size | int | 20 | 1–50 | |

列表成功 `data`：

```json
{
  "items": [],  // 列表项
  "page": 1,  // 页码（从 1 起）
  "size": 20,  // 每页条数
  "total": 100  // 总条数
}
```

#### 0.4 错误码一览

**包络 `code`（`@RestControllerAdvice` 固定映射）：**

| Body.code | 含义 | HTTP（建议） |
|-----------|------|--------------|
| `200` | 成功 | `200` |
| `-1` | 失败（含校验、业务、系统异常） | `200`；Security 层可直返 `401`/`403` |

**`data.errorCode`（ExceptionHandler 映射，字符串，可扩展）：**

| errorCode | Spring Boot / 异常来源 | 典型触发 |
|-----------|------------------------|----------|
| VALIDATION_ERROR | `MethodArgumentNotValidException`、`BindException`、`ConstraintViolationException` | `@Valid` / `@Validated` 参数校验 |
| UNAUTHORIZED | `AuthenticationException`、JWT 失效 | 锁座、支付 |
| FORBIDDEN | `AccessDeniedException` | 操作他人锁/单；staff 越权非本影院 |
| CINEMA_SCOPE_DENIED | `BusinessException` | staff 访问/修改非 `user_account.cinema_id` 资源 |
| NOT_FOUND | `BusinessException` | 错误 id |
| CONFLICT | `BusinessException` | 锁已关联订单 |
| SEAT_TAKEN | `BusinessException` | 锁座竞争 |
| COUPLE_RULE | `BusinessException` | 只选一侧情侣座 |
| SEAT_INVALID | `BusinessException` | 座位不属于该场 |
| SOLD_OUT | `BusinessException` | 推荐无解 |
| DRAFT_CONFLICT | `BusinessException` | Draft CAS 失败 |
| LOCK_EXPIRED | `BusinessException` | TTL |
| PAY_TOKEN_INVALID | `BusinessException` | payToken 验签失败或与 orderId 不匹配 |
| PAY_TOKEN_EXPIRED | `BusinessException` | payToken 过期或 Redis jti 已消费 |
| TICKET_INVALID | `BusinessException` | 取票 QR 验签失败或订单非 issued |
| INTERNAL_ERROR | 未捕获 `Exception` | 500 类未预期错误 |

#### 0.5 命名约定：DTO 与 VO

| 类型 | 含义 | 包路径（建议） | 用于 |
|------|------|----------------|------|
| **DTO** | Data Transfer Object | `adapter.web.dto` | **接收前端入参** — `@RequestBody` / Query 绑定、Bean Validation |
| **VO** | View Object | `adapter.web.vo` | **返回前端出参** — 包络 `data` 内的 JSON 结构 |

```text
前端 ──JSON Body──► XxxDTO ──► Controller ──► UseCase ──► XxxVO ──► Result.ok(vo) ──► 前端
```

- Domain / Application 层用领域对象或 Command，**不**把 DTO/VO 传进 Use Case 深处（在 adapter 边界转换）。
- 文档「入参 Body / Query 表」对应 **DTO**；「出参 data」对应 **VO**。
- Tool 出参 JSON 与 REST **VO 字段对齐**，外层 Tool 包装另述（§5.10）。

**Controller 示例：**

```java
@PostMapping("/locks")
public Result<LockVO> lock(@Valid @RequestBody LockSeatsDTO body) { ... }
```

##### 常用入参 DTO（节选）

| DTO | 接口 | 说明 |
|-----|------|------|
| LoginDTO | `POST /auth/login` | `account`（nickname 或 phone）+ `password` |
| LockSeatsDTO | `POST /locks` | showId, seatIds[], sessionId? |
| CreateOrderDTO | `POST /orders` | lockId, sessionId? |
| PayOrderDTO | `POST /orders/{id}/pay` | mockResult?, channel?, sessionId? |
| DraftPatchDTO | `PUT /booking-drafts/{id}` | version + patch |
| AgentTurnDTO | `POST /agent/turns` | sessionId, message?, cardAction?, draftPatch? |
| SeatMapCreateDTO | `POST /seat-maps` | 运营建图（rows/cols/seats[]） |
| SeatMapUpdateDTO | `PUT /seat-maps/{id}` | 运营改图（仅 mutable=true） |
| MovieCreateDTO / MovieUpdateDTO | `POST|PUT /admin/movies` | 运营维护影片 |
| CinemaCreateDTO / CinemaUpdateDTO | `POST|PUT /admin/cinemas` | 新建仅 admin；更新 staff 限本影院 |
| ShowCreateDTO / ShowUpdateDTO | `POST|PUT /admin/shows` | 运营排片 |
| AdminUserCreateDTO / AdminUserUpdateDTO | `POST|PUT /admin/users` | 系统管理员账号 |

##### 公共 VO（出参 `data`）

##### MovieVO

| 字段 | 类型 | 说明 |
|------|------|------|
| movieId | string | |
| title | string | |
| posterUrl | string | |
| genres | string[] | |
| rating | number \| null | |
| durationMin | int | |
| releaseDate | string | `YYYY-MM-DD` |
| status | string | `hot_showing` \| `coming_soon` \| `off` |
| description | string | 列表可截断；详情完整 |
| cast | string | |
| wantSeeCount | int | |

```json
{
  "movieId": "m100",  // 影片 ID
  "title": "流浪地球 3",  // 片名或卡片标题
  "posterUrl": "https://cdn.example/m100.jpg",  // 海报 URL
  "genres": [  // 类型标签列表
    "科幻",
    "冒险"
  ],
  "rating": 9.1,  // 评分；无评分可为 null
  "durationMin": 173,  // 片长（分钟）
  "releaseDate": "2026-02-01",  // 上映日 YYYY-MM-DD
  "status": "hot_showing",  // 状态（锁 active/expired…；订单 pending_pay/issued/cancelled）
  "description": "太阳危急……",  // 简介
  "cast": "刘德华 / 吴京",  // 主演文案
  "wantSeeCount": 12890  // 想看人数
}
```

##### CinemaVO

| 字段 | 类型 | 说明 |
|------|------|------|
| cinemaId | string | |
| name | string | |
| address | string | |
| distanceMeters | int \| null | 相对请求 lat/lng 的直线距离（米）；未传坐标则为 null |
| minPrice | number \| null | 可售场次最低票价；有 movieId 时为该片最低价；无排片可为 null |

##### ShowVO

| 字段 | 类型 | 说明 |
|------|------|------|
| showId | string | |
| movieId | string | |
| cinemaId | string | |
| hallId | string | |
| hallName | string | |
| startTime | string | ISO-8601 |
| endTime | string | ISO-8601 |
| price | number | 单价（元）；**最低区价**，列表展示「¥xx起」 |
| zonePrices | `{ zone, price }[]` | 本场各区单价 |
| seatRemain | int | 可售座位数 |
| seatRemainLevel | string | `ample` \| `tight` \| `almost_full` |

`seatRemainLevel` 规则（服务端）：`ample` 余座≥40%；`tight` 15%–40%；`almost_full` <15%。

##### SeatVO

| 字段 | 类型 | 说明 |
|------|------|------|
| seatId | string | **系统键**（锁座/下单/`BookingDraft.seatIds`）；对用户不可见 |
| seatName | string | **对号文案**，如 `1排4座`（=`"{rowNo}排{colNo}座"`） |
| rowNo | int | 业务排号（椅背「第几排」，银幕侧起算） |
| colNo | int | 业务座号（该排内「第几座」） |
| graphRow | int | 画布行坐标，范围 `1..rows`（包围盒） |
| graphCol | int | 画布列坐标，范围 `1..cols` |
| type | string | 座位**结构类型**，见下表 |
| zone | string | 座位**分区等级**，见下表 |
| status | string | **本场占用**（仅场次座位图）；模板接口无此字段，见下表 |
| couplePairId | string \| null | 情侣座成对 ID；同 ID 的两个座须一起选/锁；非情侣为 `null` |
| defaultStatus | string | 仅**座位图模板**（`/seat-maps`）：开场次时写入库存的默认占用，见下表 |

**`type`（结构类型）**

| 值 | 含义 |
|----|------|
| `normal` | 普通单人座 |
| `couple` | 情侣座（须与同 `couplePairId` 的另一座成对） |
| `disabled` | 无障碍/残障友好座（结构类型；是否可售仍看 `status`/`defaultStatus`） |

**`zone`（分区）**

| 值 | 含义 |
|----|------|
| 自由字符串 | 座位图自定义分区 code（如 `A`/`B`/`C`、`VIP`）；**无枚举/正则限制**；展示默认「{code}区」，可扩展 label/color |
| （历史）`normal` / `golden` | 旧种子；迁移建议映射为 `B` / `A` |

**`status`（场次占用，购票读图）**

| 值 | 含义 | 前端 |
|----|------|------|
| `available` | 可售可选 | 可点选 |
| `locked` | 已被他人（或本人未完成单）锁定 | 不可选 |
| `sold` | 已售出 | 不可选 |
| `unavailable` | 本场不可售（坏座、柱子占位等） | 不可选 |

**`defaultStatus`（模板默认，建图）**

| 值 | 含义 |
|----|------|
| `available` | 开场次后默认可售 |
| `unavailable` | 开场次后直接不可售（仍占画布格） |

> 对齐猫眼三坐标，见 **ADR-0005**。UI/票面只展示 `seatName`；画布用 `graphRow`/`graphCol`；协议键用 `seatId`。

##### SeatPlanVO

| 字段 | 类型 | 说明 |
|------|------|------|
| planId | string | 方案 ID |
| seatIds | string[] | 系统键列表；已过滤不可售 |
| score | number | 综合得分，文档统一 **0–100** |
| explain | string | 方案短解释，如「黄金区居中连座」 |
| seats | SeatVO[] | 可选展开的座位明细 |
##### OrderVO

| 字段 | 类型 | 说明 |
|------|------|------|
| orderId | string | |
| userId | string | |
| showId | string | |
| movieTitle | string | 快照 |
| cinemaName | string | 快照 |
| hallName | string | 快照 |
| startTime | string | 快照 ISO-8601 |
| seatIds | string[] | 快照 |
| unitPrice | number | |
| amount | number | |
| status | string | `pending_pay` \| `issued` \| `cancelled` |
| ticketCode | string \| null | 出票后 |
| qrPayload | string \| null | 取票 QR 签名载荷（§3.2.6） |
| payChannel | string \| null | `desktop_button` \| `mobile_qr` |
| lockId | string | |
| expireAt | string \| null | 待支付截止 |
| createdAt | string | |
| payAt | string \| null | |

##### PayQrVO

| 字段 | 类型 | 说明 |
|------|------|------|
| orderId | string | |
| amount | number | |
| expireAt | string | 待支付截止 ISO-8601 |
| payUrl | string | H5 支付页完整 URL（编码为支付 QR） |
| pollIntervalMs | int | PC 轮询订单状态建议间隔 |

##### PaySessionVO

| 字段 | 类型 | 说明 |
|------|------|------|
| orderId | string | |
| amount | number | |
| expireAt | string | |
| movieTitle | string | 快照 |
| cinemaName | string | 快照 |
| hallName | string | 快照 |
| startTime | string | 快照 |
| seatIds | string[] | 系统 seatId 快照（UI 展示名见 seat-map / 前端 seatNameById） |
| status | string | 通常为 `pending_pay` |

##### TicketVerifyVO

| 字段 | 类型 | 说明 |
|------|------|------|
| valid | boolean | 是否通过验签且订单已出票 |
| reason | string | `valid=false` 时，如 `TICKET_INVALID` |
| orderId | string | valid 时 |
| ticketCode | string | valid 时 |
| movieTitle | string | valid 时 |
| cinemaName | string | valid 时 |
| hallName | string | valid 时 |
| startTime | string | valid 时 |
| seatIds | string[] | valid 时 |
| payAt | string | valid 时 |
| userId | string | valid 时 |

##### BookingDraftVO

完整字段见本文 §4。下文接口出参中的 `draft` 均为此结构。

##### AgentCardVO / CardAction

| 字段 | 类型 | 说明 |
|------|------|------|
| cardId | string | |
| type | string | 见 §7.8.1 payload 表 |
| title | string | |
| payload | object | 随 type 变化 |
| actions | CardAction[] | |

**CardAction**

| 字段 | 类型 | 说明 |
|------|------|------|
| actionId | string | `select` / `confirm` / `go_pay` / `cancel` / `manual` / `fill_slot` / `payment_done` … |
| label | string | 按钮文案 |
| draftPatch | object \| null | 建议合并字段 |
| itemId | string \| null | 列表项 ID |

##### LockVO

| 字段 | 类型 | 说明 |
|------|------|------|
| lockId | string | |
| showId | string | |
| seatIds | string[] | |
| userId | string | |
| expireAt | string | ISO-8601 |
| ttlSeconds | int | |
| status | string | `active` \| `expired` \| `consumed` \| `released` |

---

### 1. 认证（JWT）

#### 1.1 登录

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /auth/login` |
| 权限 | 公开 |
| 说明 | 使用 **昵称或手机号** + 密码校验；签发 Access Token；**Refresh 写入 Redis，不返回前端** |

**副作用：** 生成登录会话 `sid`；`SET auth:refresh:{sid}` TTL 7d

**入参 Body（LoginDTO）**

| 字段 | 类型 | 必填 | 约束 | 说明 |
|------|------|------|------|------|
| account | string | 是 | 1–64 | **昵称**或**大陆手机号**（二选一填入本字段） |
| password | string | 是 | 8–64 | 与 `user_account.password_hash` 校验 |

**账号识别规则（服务端）：**

```text
account = trim(account)
若匹配 ^1[3-9]\d{9}$  → SELECT * FROM user_account WHERE phone = account
否则                   → SELECT * FROM user_account WHERE nickname = account
```

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| accessToken | string | JWT；客户端持久化；后续请求放 `Authorization: Bearer` |
| tokenType | string | 固定 `Bearer` |
| expiresIn | int | Access TTL（秒），默认 3600 |
| userId | string | 内部 ID（JWT `sub`） |
| nickname | string | |
| phone | string \| null | 脱敏，如 `138****0001` |
| role | string | `user` / `staff` / `admin` |
| cinemaId | string \| null | **staff 必有**所属影院；user/admin 为 null |

> 响应**不含** `refreshToken`。Refresh 仅存 Redis `auth:refresh:{sid}`，`sid` 写入 Access JWT Claims。

**入参 JSON 示例 A — 昵称登录**

```json
{
  "account": "演示用户甲",
  "password": "demo123456"
}
```

**入参 JSON 示例 B — 手机号登录**

```json
{
  "account": "13800000001",
  "password": "demo123456"
}
```

**请求（HTTP）**

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "account": "演示用户甲",
  "password": "demo123456"
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "userId": "u1",
    "nickname": "演示用户甲",
    "phone": "138****0001",
    "role": "user",
    "cinemaId": null
  },
  "traceId": "tr_login_1"
}
```

**错误：** `NOT_FOUND`（账号不存在）；`UNAUTHORIZED`（密码错误）；`VALIDATION_ERROR`

---

#### 1.2 静默续期（无公开 Refresh API）

Refresh Token **仅服务端**持有；**不**提供 `POST /auth/refresh` 给前端调用。

**触发：** 任意携带 `Authorization: Bearer <accessToken>` 的请求（含 `GET /auth/me`、锁座、下单等）。

**Filter 逻辑（Spring `OncePerRequestFilter`）：**

| 步骤 | 行为 |
|------|------|
| 1 | Access 未过期 → 正常鉴权放行 |
| 2 | Access 已过期但签名合法 → 读 Claim `sid` → 查 `auth:refresh:{sid}` |
| 3 | Refresh 有效 → 签发新 Access（同一 `sid`）→ 执行业务 → 响应包络带 `accessToken` |
| 4 | Refresh 无效/缺失 → `401`，`errorCode=UNAUTHORIZED`，前端跳转登录 |

**续期成功响应示例（业务接口通用）：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": "o_1001",
    "status": "pending_pay"
  },
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "traceId": "tr_silent_refresh_1"
}
```

**前端约定：** 拦截器检查响应根字段 `accessToken`；非空则替换本地 Access Token，**无需**二次请求。

**实现：** `ResponseBodyAdvice` 在 Filter 写入 `request` attribute 后，把新 token 填入包络顶层 `accessToken`。

---

#### 1.3 登出

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /auth/logout` |
| 权限 | 登录（Bearer accessToken，允许已过期但签名合法以解析 `sid`） |
| 说明 | `DEL auth:refresh:{sid}`；可选 `SET auth:deny:{jti}` |

**入参：** Header `Authorization` 即可，**无 Body**

**出参 data：** `{ "loggedOut": true }`

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "loggedOut": true
  },
  "traceId": "tr_logout_1"
}
```

**副作用：** 服务端 Refresh 删除后，旧 Access 即使未过期也无法再静默续期。

---

#### 1.4 当前用户

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /auth/me` |
| 权限 | 登录 |

**入参：** 无（Header `Authorization: Bearer <accessToken>`）

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | string | |
| nickname | string | |
| phone | string \| null | 脱敏展示 |
| role | string | |
| cinemaId | string \| null | staff 所属影院；其余 null |
| avatarUrl | string \| null | |

**响应 200**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "userId": "u1",  // 用户 ID
    "nickname": "演示用户甲",  // 昵称
    "phone": "138****0001",  // 脱敏手机号
    "role": "user",  // 角色：user / staff / admin
    "cinemaId": null,  // staff 时为所属影院 ID
    "avatarUrl": null  // 头像 URL；无则为 null
  },
  "traceId": "tr_me_1"  // 链路追踪 ID，用于排障与日志关联
}

```

---

### 2. 影片目录

#### 2.1 搜索 / 列表影片

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /movies` |
| 权限 | 公开 |

**入参 Query**

| 字段 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| status | string | 否 | `hot_showing` | `hot_showing` \| `coming_soon` |
| q | string | 否 | — | 片名模糊，最长 64 |
| genre | string | 否 | — | 类型包含匹配 |
| page | int | 否 | 1 | |
| size | int | 否 | 20 | 最大 50 |

**出参 data：** `{ items: MovieVO[], page, size, total }`

**入参 Query JSON（示意）**

```json
{
  "status": "hot_showing",
  "q": "喜剧",
  "genre": "喜剧",
  "page": 1,
  "size": 10
}
```

**请求**

```http
GET /api/v1/movies?status=hot_showing&genre=喜剧&page=1&size=10
```

**响应 200（节选）**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "items": [  // 列表项
      {
        "movieId": "m201",  // 影片 ID
        "title": "年会不能停！",  // 片名或卡片标题
        "posterUrl": "https://cdn.example/m201.jpg",  // 海报 URL
        "genres": [  // 类型标签列表
          "喜剧"
        ],
        "rating": 8.2,  // 评分；无评分可为 null
        "durationMin": 118,  // 片长（分钟）
        "releaseDate": "2026-01-10",  // 上映日 YYYY-MM-DD
        "status": "hot_showing",  // 影片状态：hot_showing / coming_soon / off
        "description": "打工人逆袭……",  // 简介
        "cast": "大鹏 / 白客",  // 主演文案
        "wantSeeCount": 5600  // 想看人数
      }
    ],
    "page": 1,  // 页码（从 1 起）
    "size": 10,  // 每页条数
    "total": 1  // 总条数；本示例仅展示 1 条
  },
  "traceId": "tr_movies_1"  // 链路追踪 ID，用于排障与日志关联
}

```

---

#### 2.2 影片详情

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /movies/{movieId}` |
| 权限 | 公开 |

**入参 Path**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| movieId | string | 是 | |

**入参 JSON（示意）**

```json
{
  "movieId": "m100"  // 影片 ID
}
```

**出参 data：** MovieVO（`description`、`cast` 完整）

**出参 JSON**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "movieId": "m100",  // 影片 ID
    "title": "流浪地球 3",  // 片名或卡片标题
    "posterUrl": "https://cdn.example/m100.jpg",  // 海报 URL
    "genres": [  // 类型标签列表
      "科幻",
      "冒险"
    ],
    "rating": 9.1,  // 评分；无评分可为 null
    "durationMin": 173,  // 片长（分钟）
    "releaseDate": "2026-02-01",  // 上映日 YYYY-MM-DD
    "status": "hot_showing",  // 资源状态（订单/锁/影片等，随接口变化）
    "description": "太阳危急，联合政府启动移山计划……",  // 简介
    "cast": "刘德华 / 吴京",  // 主演文案
    "wantSeeCount": 12890  // 想看人数
  },
  "traceId": "tr_movie_detail_1"  // 链路追踪 ID，用于排障与日志关联
}

```

**错误：** `NOT_FOUND`

---

#### 2.3 想看

##### 添加想看

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /movies/{movieId}/want-see` |
| 权限 | 登录 |

**入参 Path：** `movieId`  
**入参 Body（可选）**

| 字段 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| want | boolean | 否 | true | 仅允许 true；取消用 DELETE |

**入参 JSON（Body 可省略）**

```json
{
  "want": true
}
```

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| movieId | string | |
| wanted | boolean | 当前是否想看 |

**响应 200**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "movieId": "m100",  // 影片 ID
    "wanted": true  // 当前用户是否已想看
  },
  "traceId": "tr_want_1"  // 链路追踪 ID，用于排障与日志关联
}

```

##### 取消想看

| 项 | 内容 |
|----|------|
| 方法/路径 | `DELETE /movies/{movieId}/want-see` |
| 权限 | 登录 |
| 出参 | 同添加，`wanted: false`（幂等） |

**入参 Path JSON（示意）**

```json
{
  "movieId": "m100"
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "movieId": "m100",
    "wanted": false
  },
  "traceId": "tr_unwant_1"
}
```

##### 想看列表

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /me/want-see` |
| 权限 | 登录 |
| 入参 Query | `page`, `size` |
| 出参 data | `{ items: MovieVO[], page, size, total }` |

**入参 Query JSON（示意）**

```json
{
  "page": 1,
  "size": 20
}
```

**响应 200（节选）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "movieId": "m100",
        "title": "流浪地球 3",
        "posterUrl": "https://cdn.example/m100.jpg",
        "genres": ["科幻", "冒险"],
        "rating": 9.1,
        "durationMin": 173,
        "releaseDate": "2026-02-01",
        "status": "hot_showing",
        "description": "太阳危急……",
        "cast": "刘德华 / 吴京",
        "wantSeeCount": 12890
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1
  },
  "traceId": "tr_want_list_1"
}
```

---

### 3. 影院与场次

#### 3.1 影院列表

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /cinemas` |
| 权限 | 公开 |

**入参 Query**

| 字段 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| movieId | string | 否 | — | 片→院：只返回有排片影院；同时决定 `minPrice` 是否按该片计算 |
| lat | number | 条件 | — | 用户纬度；`sort=distance` 或要附近列表时必填 |
| lng | number | 条件 | — | 用户经度；与 lat 成对 |
| radiusMeters | int | 否 | 5000 | 搜索半径（米）；仅返回距离 ≤ 该值的影院 |
| sort | string | 否 | `distance` | `distance` \| `price`；`distance` 须带 lat/lng |
| page | int | 否 | 1 | |
| size | int | 否 | 20 | |

**出参 data：** `{ items: CinemaVO[], page, size, total }`

**请求**

```http
GET /api/v1/cinemas?movieId=m100&lat=31.2989&lng=121.5140&radiusMeters=5000&sort=distance&page=1&size=10
```

**响应 200（节选）**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "items": [  // 列表项
      {
        "cinemaId": "c12",  // 影院 ID
        "name": "万达影城（五角场店）",  // 名称
        "address": "淞沪路 77 号",  // 地址
        "distanceMeters": 1200,  // 相对请求 lat/lng 的直线距离（米）
        "minPrice": 45.0  // 可售场次最低票价；有 movieId 时为该片最低价
      },
      {
        "cinemaId": "c15",  // 影院 ID
        "name": "金逸影城（大学路店）",  // 名称
        "address": "大学路 297 号",  // 地址
        "distanceMeters": 2100,  // 相对请求 lat/lng 的直线距离（米）
        "minPrice": 42.0  // 可售场次最低票价；有 movieId 时为该片最低价
      }
    ],
    "page": 1,  // 页码（从 1 起）
    "size": 10,  // 每页条数
    "total": 2  // 总条数（半径内）
  },
  "traceId": "tr_cinemas_1"  // 链路追踪 ID，用于排障与日志关联
}
```

---

#### 3.2 影院详情（P1）

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /cinemas/{cinemaId}` |
| 权限 | 公开 |

**出参 data：** CinemaVO + 扩展字段

| 字段 | 类型 | 说明 |
|------|------|------|
| trafficNote | string \| null | 交通提示 |
| halls | object[] | `{ hallId, name }` |

**错误：** `NOT_FOUND`

**入参 Path JSON（示意）**

```json
{
  "cinemaId": "c12"
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "cinemaId": "c12",
    "name": "万达影城（五角场店）",
    "address": "淞沪路 77 号",
    "distanceMeters": null,
    "minPrice": 42.0,
    "trafficNote": "地铁 10 号线五角场站 3 号口步行 5 分钟",
    "halls": [
      { "hallId": "h1", "name": "1号厅" },
      { "hallId": "h_convex_1", "name": "2号异形厅" }
    ]
  },
  "traceId": "tr_cinema_detail_1"
}
```

---

#### 3.3 场次列表

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /shows` |
| 权限 | 公开 |

**入参 Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cinemaId | string | 是 | |
| movieId | string | 是 | |
| date | string | 是 | `YYYY-MM-DD` |

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| date | string | |
| items | ShowVO[] | 按 `startTime` 升序 |

**入参 Query JSON（示意）**

```json
{
  "cinemaId": "c12",
  "movieId": "m100",
  "date": "2026-07-28"
}
```

**请求**

```http
GET /api/v1/shows?cinemaId=c12&movieId=m100&date=2026-07-28
```

**响应 200**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "date": "2026-07-28",  // 业务日 YYYY-MM-DD
    "items": [  // 列表项
      {
        "showId": "s900",  // 场次 ID
        "movieId": "m100",  // 影片 ID
        "cinemaId": "c12",  // 影院 ID
        "hallId": "h1",  // 影厅 ID
        "hallName": "1号厅",  // 影厅名称
        "startTime": "2026-07-28T14:10:00+08:00",  // 开场时间 ISO-8601
        "endTime": "2026-07-28T17:05:00+08:00",  // 散场时间 ISO-8601
        "price": 55.0,  // 单价（元）
        "seatRemain": 86,  // 可售座位数
        "seatRemainLevel": "ample"  // 余座档位：ample / tight / almost_full
      },
      {
        "showId": "s901",  // 场次 ID
        "movieId": "m100",  // 影片 ID
        "cinemaId": "c12",  // 影院 ID
        "hallId": "h1",  // 影厅 ID
        "hallName": "1号厅",  // 影厅名称
        "startTime": "2026-07-28T16:40:00+08:00",  // 开场时间 ISO-8601
        "endTime": "2026-07-28T19:35:00+08:00",  // 散场时间 ISO-8601
        "price": 55.0,  // 单价（元）
        "seatRemain": 12,  // 可售座位数
        "seatRemainLevel": "tight"  // 余座档位：ample / tight / almost_full
      }
    ]
  },
  "traceId": "tr_shows_1"  // 链路追踪 ID，用于排障与日志关联
}

```

---

#### 3.4 场次详情

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /shows/{showId}` |
| 权限 | 公开 |

**出参 data：** ShowVO +

| 字段 | 类型 | 说明 |
|------|------|------|
| movie | MovieVO | 简要可用同结构 |
| cinema | CinemaVO | 简要 |

**错误：** `NOT_FOUND`

**入参 Path JSON（示意）**

```json
{
  "showId": "s900"
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "showId": "s900",
    "movieId": "m100",
    "cinemaId": "c12",
    "hallId": "h1",
    "hallName": "1号厅",
    "startTime": "2026-07-28T14:10:00+08:00",
    "endTime": "2026-07-28T17:05:00+08:00",
    "price": 55.0,
    "seatRemain": 86,
    "seatRemainLevel": "ample",
    "movie": {
      "movieId": "m100",
      "title": "流浪地球 3",
      "posterUrl": "https://cdn.example/m100.jpg",
      "genres": ["科幻", "冒险"],
      "rating": 9.1,
      "durationMin": 173,
      "releaseDate": "2026-02-01",
      "status": "hot_showing",
      "description": "太阳危急……",
      "cast": "刘德华 / 吴京",
      "wantSeeCount": 12890
    },
    "cinema": {
      "cinemaId": "c12",
      "name": "万达影城（五角场店）",
      "address": "淞沪路 77 号",
      "distanceMeters": null,
      "minPrice": 55.0
    }
  },
  "traceId": "tr_show_detail_1"
}
```

---

### 4. 推荐

#### 4.1 每周热门

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /reco/weekly-hot` |
| 权限 | 公开 |
| 算法 | `hotScore = 0.45*weekOrders + 0.25*weekClicks + 0.15*ratingNorm + 0.15*freshness`（权重可读 `reco_weight`） |

**入参 Query**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|------|------|------|------|------|------|
| cityId | string | 否 | Header/默认城 | | |
| limit | int | 否 | 10 | 1–20 | |

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| computedAt | string | ISO-8601 |
| items | object[] | 见下 |

**items[]**

| 字段 | 类型 | 说明 |
|------|------|------|
| rank | int | 1..N |
| hotScore | number | |
| heatTag | string | 如「本周爆款」 |
| movie | MovieVO | |

**入参 Query JSON（示意）**

```json
{
  "cityId": "city_sh",
  "limit": 10
}
```

**响应 200（节选）**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "computedAt": "2026-07-28T12:00:00+08:00",  // 热门分计算时间
    "items": [  // 列表项
      {
        "rank": 1,  // 榜单名次
        "hotScore": 92.4,  // 热门分
        "heatTag": "本周爆款",  // 热度文案标签
        "movie": {  // 影片快照 MovieVO
          "movieId": "m100",  // 影片 ID
          "title": "流浪地球 3",  // 片名或卡片标题
          "posterUrl": "https://cdn.example/m100.jpg",  // 海报 URL
          "genres": [  // 类型标签列表
            "科幻",
            "冒险"
          ],
          "rating": 9.1,  // 评分；无评分可为 null
          "durationMin": 173,  // 片长（分钟）
          "releaseDate": "2026-02-01",  // 上映日 YYYY-MM-DD
          "status": "hot_showing",  // 影片状态：hot_showing / coming_soon / off
          "description": "太阳危急……",  // 简介
          "cast": "刘德华 / 吴京",  // 主演文案
          "wantSeeCount": 12890  // 想看人数
        }
      }
    ]
  },
  "traceId": "tr_hot_1"  // 链路追踪 ID，用于排障与日志关联
}

```

---

#### 4.2 个人推荐

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /reco/personal` |
| 权限 | 可选登录（未登录 `mode=fallback_hot`） |
| 算法 | `0.5*genreMatch + 0.2*tagMatch + 0.2*ratingNorm + 0.1*hotScore`；同类型连续 ≤3 |

**入参 Query**

| 字段 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| limit | int | 否 | 10 | 最大 20 |
| excludeMovieIds | string | 否 | — | 逗号分隔，排除已展示 |

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| mode | string | `personalized` \| `fallback_hot` |
| items | object[] | |

**items[]**

| 字段 | 类型 | 说明 |
|------|------|------|
| movie | MovieVO | |
| personalScore | number | |
| reason | string \| null | P1 理由文案 |

**入参 Query JSON（示意）**

```json
{
  "limit": 10,
  "excludeMovieIds": "m100,m201"
}
```

**响应 200**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "mode": "personalized",  // 推荐模式：personalized / fallback_hot 等
    "items": [  // 列表项
      {
        "movie": {  // 影片快照 MovieVO
          "movieId": "m201",  // 影片 ID
          "title": "年会不能停！",  // 片名或卡片标题
          "posterUrl": "https://cdn.example/m201.jpg",  // 海报 URL
          "genres": [  // 类型标签列表
            "喜剧"
          ],
          "rating": 8.2,  // 评分；无评分可为 null
          "durationMin": 118,  // 片长（分钟）
          "releaseDate": "2026-01-10",  // 上映日 YYYY-MM-DD
          "status": "hot_showing",  // 影片状态：hot_showing / coming_soon / off
          "description": "打工人逆袭……",  // 简介
          "cast": "大鹏 / 白客",  // 主演文案
          "wantSeeCount": 5600  // 想看人数
        },
        "personalScore": 0.86,  // 个人推荐分
        "reason": "符合你偏好的喜剧"  // 推荐理由文案
      }
    ]
  },
  "traceId": "tr_personal_1"  // 链路追踪 ID，用于排障与日志关联
}

```

---

#### 4.3 座位推荐

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /reco/seats` |
| 权限 | 登录 |
| 说明 | 在指定场次可售座上搜索 Top-N 连座/偏好方案；**不锁座**（锁座另调 `POST /locks`） |
| 权重 | 居中 0.35 / 视距 0.30 / 过道 0.15 / 连座 0.15 / 边缘惩罚 0.05；结果必须过滤不可售（`sold`/`locked`/`unavailable`） |

**入参 Body**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|------|------|------|------|------|------|
| showId | string | 是 | | 须为可售场次 | 目标场次 ID |
| count | int | 是 | | 1–4 | 需要推荐的座位数（票数） |
| preferRow | string | 否 | `middle` | 见下表 | 前后排偏好（相对银幕→观众方向的业务排区） |
| preferSide | string | 否 | `center` | 见下表 | 左右位置偏好（相对厅宽） |
| together | boolean | 否 | true | | `true`：方案内座位须同排且业务座号连续（中间无缺口）；`false`：允许拆开，仍尽量靠近 |

**`preferRow` 取值**

| 值 | 含义 | 打分倾向 |
|----|------|----------|
| `front` | 前区（靠近银幕一侧约前 1/3 业务排） | 视距近；易偏「太近」时降权 |
| `middle` | 中区（约中间 1/3，默认） | 视距分最高 |
| `back` | 后区（远离银幕约后 1/3） | 偏后排；俯视感更强 |

**`preferSide` 取值**

| 值 | 含义 | 打分倾向 |
|----|------|----------|
| `center` | 厅宽中央（默认） | 「居中」权重最高 |
| `aisle` | 靠过道（行内缺口/左右尽头旁的可售座） | 「过道」权重提高，便于进出 |
| `edge` | 贴边（最左/最右一侧） | 一般带边缘惩罚；仅当用户明确要靠边时选用 |

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| showId | string | 回显请求场次 |
| plans | SeatPlanVO[] | 推荐方案，按 `score` 降序，最多 1–3 条；完全无解时为 `[]` |
| compromise | object \| null | 有可用 `plans` 时为 `null`；无理想解（如无法连座）时给出折中文案与换场建议 |

**plans[]（SeatPlanVO）**

| 字段 | 类型 | 说明 |
|------|------|------|
| planId | string | 本方案 ID（会话内唯一，供前端「选此方案」引用） |
| seatIds | string[] | 本方案座位**系统键**列表（= `SeatVO.seatId`）；长度通常 = `count`；已排除不可售 |
| score | number | 综合得分 **0–100**，越高越符合偏好与权重 |
| explain | string | 给人看的短解释，如「黄金区居中连座」 |
| seats | SeatVO[] | 可选展开的座位明细（含 `seatName`/`zone`/`status` 等）；与 `seatIds` 一一对应 |

**compromise（无理想解时）**

| 字段 | 类型 | 说明 |
|------|------|------|
| suggestion | string | 折中建议文案，如「本场无法 2 连座，建议分开坐或换场」 |
| altShowIds | string[] | 同片同影院可换场次 ID 列表（可空）；前端可跳转重查 |

**请求**

```json
{
  "showId": "s900",           // 场次 ID
  "count": 2,                 // 要几张票（1–4）
  "preferRow": "middle",      // front=前区 / middle=中区 / back=后区
  "preferSide": "center",     // center=居中 / aisle=靠过道 / edge=贴边
  "together": true            // true=必须连座；false=允许拆开
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "showId": "s900",
    "plans": [
      {
        "planId": "sp_1",                    // 方案 ID
        "seatIds": ["sm1:6:7", "sm1:6:8"],   // 系统键；去锁座用这个
        "score": 91.2,                       // 0–100
        "explain": "黄金区居中连座",           // 方案说明
        "seats": [
          {
            "seatId": "sm1:6:7",             // 系统键
            "seatName": "6排7座",            // 对号文案
            "rowNo": 6,                      // 业务排号
            "colNo": 7,                      // 业务座号
            "graphRow": 6,                   // 画布行
            "graphCol": 7,                   // 画布列
            "type": "normal",                // normal=普通 / couple=情侣 / disabled=残障座等结构类型
            "zone": "golden",                // normal=普通区 / golden=黄金区
            "status": "available",           // available=可选 / locked=锁定 / sold=已售 / unavailable=不可售
            "couplePairId": null             // 情侣成对 ID；非情侣为 null
          },
          {
            "seatId": "sm1:6:8",
            "seatName": "6排8座",
            "rowNo": 6,
            "colNo": 8,
            "graphRow": 6,
            "graphCol": 8,
            "type": "normal",
            "zone": "golden",
            "status": "available",
            "couplePairId": null
          }
        ]
      }
    ],
    "compromise": null                       // 有方案时为 null；无解时见 compromise 表
  },
  "traceId": "tr_seat_reco_1"
}
```

---

### 5. 座位与锁座

#### 5.0 自定义座位图（新建 / 查询）

运营侧建图；购票 Agent **不**调用本接口。权限：`staff` / `admin`；**staff 仅本影院**（`seat_map.cinema_id`）。

**管理端画布约定（前端 `SeatMapEditor`，见前端系分 §9.7）：**

1. 运营先填 **`rows` × `cols`**，生成包围盒空画布（格子全是缺口）。
2. 在画布上 **点击添加 / 删除** 座位；可选刷 `zone` / `type`（黄金、情侣等）。
3. 点「保存」时，前端把 **有座格子** 编成 `seats[]`（含 graphRow/graphCol；可选 rowNo/colNo），缺口不传。
4. 服务端自动补全业务编号与 `seatName`（未传则 LTR），校验并落库；**不**负责画布交互。

> **对齐：** 字段与前端 §9.7 契约表一致；`mutable`、列表 `GET /seat-maps`、绑厅仅新建见下小节。

##### 5.0.1 创建座位图

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /seat-maps` |
| 权限 | **staff / admin**；staff 的 `cinemaId` **强制**为本影院（忽略或拒绝他院） |
| 说明 | **稀疏建图**：Body 只提交真实存在的座位；缺口不传。`rows`×`cols` 为包围盒（与画布尺寸一致） |

**入参 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| seatMapId | string | 否 | 不传则服务端生成 |
| cinemaId | string | 条件 | **admin 必填**；staff 可省略（服务端填 JWT.cinemaId） |
| rows | int | 是 | 包围盒行数 ≥1 |
| cols | int | 是 | 包围盒列数 ≥1 |
| screenLabel | string | 否 | 默认「银幕」 |
| seats | object[] | 是 | 非空；仅真实座位 |

**seats[]**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| graphRow | int | 是 | 画布行 1..rows |
| graphCol | int | 是 | 画布列 1..cols |
| rowNo | int | 否 | 业务排号；缺省则按 LTR 自动编号（§6） |
| colNo | int | 否 | 业务座号；缺省则同行内从左连续 |
| seatId | string | 否 | 系统键；缺省 `{seatMapId}:{graphRow}:{graphCol}` |
| type | string | 否 | 默认 `normal`；`normal`/`couple`/`disabled` |
| zone | string | 否 | 默认 `normal`；`normal`/`golden` |
| couplePairId | string | 条件 | `type=couple` 时必填，成对相同 |
| defaultStatus | string | 否 | 默认 `available`；`available`/`unavailable` |

> 服务端落库时生成 `seatName = "{rowNo}排{colNo}座"`。勿再传 `displayCode`。

**入参 JSON（凸形示意；上凸三座自动编为 1排1–3座）**

```json
{
  "seatMapId": "sm_convex_1",
  "rows": 4,
  "cols": 8,
  "screenLabel": "银幕",
  "seats": [
    { "graphRow": 1, "graphCol": 4, "type": "normal", "zone": "normal" },
    { "graphRow": 1, "graphCol": 5, "type": "normal", "zone": "golden" },
    { "graphRow": 1, "graphCol": 6, "type": "normal", "zone": "normal" },
    { "graphRow": 2, "graphCol": 2 },
    { "graphRow": 2, "graphCol": 3 },
    { "graphRow": 2, "graphCol": 4, "zone": "golden" },
    { "graphRow": 2, "graphCol": 5, "zone": "golden" },
    { "graphRow": 2, "graphCol": 6, "zone": "golden" },
    { "graphRow": 2, "graphCol": 7 },
    { "graphRow": 3, "graphCol": 1 },
    { "graphRow": 3, "graphCol": 2, "type": "couple", "couplePairId": "cp_c23" },
    { "graphRow": 3, "graphCol": 3, "type": "couple", "couplePairId": "cp_c23" },
    { "graphRow": 3, "graphCol": 4, "zone": "golden" },
    { "graphRow": 3, "graphCol": 5, "zone": "golden" },
    { "graphRow": 3, "graphCol": 6, "zone": "golden" },
    { "graphRow": 3, "graphCol": 7 },
    { "graphRow": 3, "graphCol": 8 },
    { "graphRow": 4, "graphCol": 1 },
    { "graphRow": 4, "graphCol": 2 },
    { "graphRow": 4, "graphCol": 3 },
    { "graphRow": 4, "graphCol": 4, "zone": "golden" },
    { "graphRow": 4, "graphCol": 5, "zone": "golden" },
    { "graphRow": 4, "graphCol": 6, "zone": "golden" },
    { "graphRow": 4, "graphCol": 7 },
    { "graphRow": 4, "graphCol": 8 }
  ]
}
```

**出参 data（节选；上凸三座已自动编号）**

**响应 200（节选）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "seatMapId": "sm_convex_1",
    "rows": 4,
    "cols": 8,
    "screenLabel": "银幕",
    "seatCount": 25,
    "mutable": true,
    "seats": [
      {
        "seatId": "sm_convex_1:1:4",
        "seatName": "1排1座",
        "rowNo": 1,
        "colNo": 1,
        "graphRow": 1,
        "graphCol": 4,
        "type": "normal",
        "zone": "normal",
        "defaultStatus": "available",
        "couplePairId": null
      }
    ]
  },
  "traceId": "tr_seatmap_create_1"
}
```

**错误：** `VALIDATION_ERROR`（越界/重复 graph 或业务号/情侣座不成对）；`FORBIDDEN`

##### 5.0.2 座位图模板列表

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /seat-maps` |
| 权限 | **工作人员（staff）/ 系统管理员（admin）** |
| 说明 | 运营列表页（前端 `/admin/seat-maps`）；分页 |

**Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 默认 1 |
| size | int | 否 | 默认 20，最大 50 |

**入参 Query JSON（示意）**

```json
{
  "page": 1,
  "size": 20
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "seatMapId": "sm_convex_1",
        "rows": 4,
        "cols": 8,
        "screenLabel": "银幕",
        "seatCount": 25,
        "mutable": true
      },
      {
        "seatMapId": "sm_rect_1",
        "rows": 12,
        "cols": 16,
        "screenLabel": "银幕",
        "seatCount": 120,
        "mutable": false
      }
    ],
    "total": 2,
    "page": 1,
    "size": 20
  },
  "traceId": "tr_seatmap_list_1"
}
```

##### 5.0.3 查询座位图模板

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /seat-maps/{seatMapId}` |
| 权限 | staff / admin（购票侧仍用 `GET /shows/{id}/seat-map`） |
| 出参 | 同创建出参（含 `mutable`；无场次 `status`，仅模板 `defaultStatus`） |

**入参 Path JSON（示意）**

```json
{
  "seatMapId": "sm_convex_1"
}
```

**响应 200（节选）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "seatMapId": "sm_convex_1",
    "rows": 4,
    "cols": 8,
    "screenLabel": "银幕",
    "seatCount": 25,
    "mutable": true,
    "seats": [
      {
        "seatId": "sm_convex_1:1:4",
        "seatName": "1排1座",
        "rowNo": 1,
        "colNo": 1,
        "graphRow": 1,
        "graphCol": 4,
        "type": "normal",
        "zone": "normal",
        "defaultStatus": "available",
        "couplePairId": null
      }
    ]
  },
  "traceId": "tr_seatmap_get_1"
}
```

##### 5.0.4 绑定影厅

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /halls` |
| 权限 | **staff / admin**；staff 的 `cinemaId` 须为本影院，且 `seatMapId` 所属图须同影院 |
| 说明 | MVP **仅新建影厅并绑图**；已排片厅不改绑（避免库存错位）。换图 = 新建厅 + 新场次 |

**入参 JSON**

```json
{
  "hallId": "h_convex_1",  // 可选；不传则生成
  "cinemaId": "c12",  // 必填；所属影院
  "name": "2号异形厅",  // 必填；影厅名称
  "seatMapId": "sm_convex_1"  // 必填；已创建的稀疏座位图
}
```

**出参 JSON**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "hallId": "h_convex_1",
    "cinemaId": "c12",
    "name": "2号异形厅",
    "seatMapId": "sm_convex_1"
  },
  "traceId": "tr_hall_create_1"
}
```

> 之后创建场次指定 `hallId`，系统按该厅 `seat_map_id` **稀疏展开** `seat_status`。已排片的图 `mutable=false`，禁止改 `seats` 集合（见 §6 建图规则）。

##### 5.0.5 更新座位图

| 项 | 内容 |
|----|------|
| 方法/路径 | `PUT /seat-maps/{seatMapId}` |
| 权限 | **工作人员（staff）/ 系统管理员（admin）** |
| 说明 | 仅 `mutable=true`（未被任何场次引用）时可改 `seats[]` / `rows` / `cols`；已排片图返回 `CONFLICT` |

**入参 Body：** 同 §5.0.1 创建（全量替换 seats 集合）

**出参 data：** 同创建出参

**错误：** `FORBIDDEN`；`CONFLICT`（`mutable=false`）；`VALIDATION_ERROR`

##### 5.0.6 删除座位图

| 项 | 内容 |
|----|------|
| 方法/路径 | `DELETE /seat-maps/{seatMapId}` |
| 权限 | **工作人员（staff）/ 系统管理员（admin）** |
| 说明 | 无影厅引用且无场次引用时可删；否则 `CONFLICT` |

**出参 data：** `{ "deleted": true, "seatMapId": "..." }`

**错误：** `FORBIDDEN`；`NOT_FOUND`；`CONFLICT`（仍被 hall / show 引用）

#### 5.1 座位图（购票：场次级）

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /shows/{showId}/seat-map` |
| 权限 | 公开可读；锁座仍须登录 |
| 说明 | 返回包围盒 + **稀疏** `seats[]`（仅有座格子）+ 本场 `status`；前端按 `graphRow/graphCol` 落位，展示 `seatName`；无座位的格留空即可呈现凸形 |

**入参 Path：** `showId`

**入参 Path JSON（示意）**

```json
{
  "showId": "s900"
}
```

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| showId | string | |
| rows | int | 包围盒行数 |
| cols | int | 包围盒列数 |
| screenLabel | string | 默认「银幕」 |
| seats | SeatVO[] | 稀疏列表；含本场 status；含 `seatName`/`graph*`/`rowNo`/`colNo` |
| legend | object | 状态文案 |
| price | number | 单价冗余 |

**legend 示例**

```json
{
  "available": "可选",  // 可选
  "locked": "锁定中",  // 锁定中
  "sold": "已售",  // 已售
  "unavailable": "不可选",  // 不可选
  "golden": "黄金区"  // 黄金区
}
```

**响应 200（节选）**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "showId": "s900",  // 场次 ID
    "rows": 12,  // 包围盒行数
    "cols": 16,  // 包围盒列数
    "screenLabel": "银幕",  // 银幕文案
    "price": 55.0,  // 单价（元）
    "legend": {  // 图例：状态文案映射
      "available": "可选",  // 可选
      "locked": "锁定中",  // 锁定中
      "sold": "已售",  // 已售
      "unavailable": "不可选",  // 不可选
      "golden": "黄金区"  // 黄金区
    },
    "seats": [  // 稀疏座位列表
      {
        "seatId": "sm1:6:7",  // 系统键；写入 Draft.seatIds / 锁座
        "seatName": "6排7座",  // 对号文案
        "rowNo": 6,
        "colNo": 7,
        "graphRow": 6,
        "graphCol": 7,
        "type": "normal",
        "zone": "golden",
        "status": "available",
        "couplePairId": null
      }
    ]
  },
  "traceId": "tr_seatmap_1"  // 链路追踪 ID，用于排障与日志关联
}

```

---

#### 5.2 锁座

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /locks` |
| 权限 | 登录 |
| 副作用 | `seat_status`→locked；写 `seat_lock`；Redis TTL；可选回写 Draft |

**入参 Body**

| 字段 | 类型 | 必填 | 默认 | 约束 | 说明 |
|------|------|------|------|------|------|
| showId | string | 是 | | | |
| seatIds | string[] | 是 | | 长度 1–4；非空 | 系统 seatId（非 seatName） |
| ttlSeconds | int | 否 | 900 | 60–900 | 演示默认 15min |
| sessionId | string | 否 | | | 传则回写 Draft |

**出参 data：** LockVO（`status=active`）

**请求**

```http
POST /api/v1/locks
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{
  "showId": "s900",  // 场次 ID
  "seatIds": [  // 系统 seatId 列表
    "sm1:6:7",
    "sm1:6:8"
  ],
  "ttlSeconds": 900,  // 锁座 TTL（秒），默认 900
  "sessionId": "sess_1"  // 购票/Agent 会话 ID
}
```

**响应 200**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "lockId": "lk_01HZX",  // 锁座凭证 ID
    "showId": "s900",  // 场次 ID
    "seatIds": [  // 系统 seatId 列表
      "sm1:6:7",
      "sm1:6:8"
    ],
    "userId": "u1",  // 用户 ID
    "expireAt": "2026-07-28T15:05:00+08:00",  // 截止时间（锁座/待支付）
    "ttlSeconds": 900,  // 锁座 TTL（秒）
    "status": "active"  // 资源状态（随接口变化）
  },
  "traceId": "tr_lock_1"  // 链路追踪 ID，用于排障与日志关联
}

```

**错误对照**

| errorCode | Body.code | 场景 |
|-----------|-----------|------|
| UNAUTHORIZED | -1 | 未登录 |
| VALIDATION_ERROR | -1 | seatIds 空/超限/重复 |
| NOT_FOUND | -1 | show 不存在 |
| SEAT_TAKEN | -1 | 含已锁/已售 |
| COUPLE_RULE | -1 | 情侣座单边 |
| SEAT_INVALID | -1 | 座位不属于该场 |

**SEAT_TAKEN 响应示例**

```json
{
  "code": -1,
  "message": "seats already taken: sm1:6:7",
  "data": {
    "errorCode": "SEAT_TAKEN",
    "conflictSeatIds": [  // 冲突座位列表
      "sm1:6:7"
    ],
    "showId": "s900"  // 场次 ID
  },
  "traceId": "tr_lock_fail"  // 链路追踪 ID，用于排障与日志关联
}

```

---

#### 5.3 查询锁

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /locks/{lockId}` |
| 权限 | 本人 |

**出参 data：** LockVO  
**错误：** `FORBIDDEN` / `NOT_FOUND`

**入参 Path JSON（示意）**

```json
{
  "lockId": "lk_01HZX"
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "lockId": "lk_01HZX",
    "showId": "s900",
    "seatIds": ["sm1:6:7", "sm1:6:8"],
    "userId": "u1",
    "expireAt": "2026-07-28T15:05:00+08:00",
    "ttlSeconds": 900,
    "status": "active"
  },
  "traceId": "tr_lock_get_1"
}
```

---

#### 5.4 解锁

| 项 | 内容 |
|----|------|
| 方法/路径 | `DELETE /locks/{lockId}` |
| 权限 | 登录 + 锁归属本人 |

**入参 Path：** `lockId`  
**入参 Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | string | 否 | 成功后清 Draft 锁字段 |

**入参 JSON（示意）**

```json
{
  "lockId": "lk_01HZX",
  "sessionId": "sess_1"
}
```

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| lockId | string | |
| released | boolean | true |

**响应 200**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "lockId": "lk_01HZX",  // 锁座凭证 ID
    "released": true  // 是否已释放
  },
  "traceId": "tr_unlock_1"  // 链路追踪 ID，用于排障与日志关联
}

```

**错误：** `FORBIDDEN`；`NOT_FOUND`；`LOCK_EXPIRED`（建议幂等：已过期也返回 `released: true`）

---

### 6. 订单与支付

#### 6.1 创建订单

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /orders` |
| 权限 | 登录 |
| 说明 | 服务端从 lock 快照影片/场次/座位/金额；客户端不可改价 |

**入参 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| lockId | string | 是 | 必须 active 且属本人 |
| sessionId | string | 否 | 回写 `draft.orderId` |

**出参 data：** OrderVO（`status=pending_pay`）

**请求**

```json
{
  "lockId": "lk_01HZX",  // 锁座凭证 ID
  "sessionId": "sess_1"  // 购票/Agent 会话 ID
}
```

**响应 200**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "orderId": "o_1001",  // 订单 ID
    "userId": "u1",  // 用户 ID
    "showId": "s900",  // 场次 ID
    "movieTitle": "流浪地球 3",  // 影片名快照
    "cinemaName": "万达影城（五角场店）",  // 影院名快照
    "hallName": "1号厅",  // 影厅名称
    "startTime": "2026-07-28T14:10:00+08:00",  // 开场时间 ISO-8601
    "seatIds": [  // 系统 seatId 列表
      "sm1:6:7",
      "sm1:6:8"
    ],
    "unitPrice": 55.0,  // 单价快照
    "amount": 110.0,  // 订单金额
    "status": "pending_pay",  // 资源状态（随接口变化）
    "ticketCode": null,  // 取票码；出票后填充
    "qrPayload": null,  // 取票 QR 签名载荷；出票后填充
    "lockId": "lk_01HZX",  // 锁座凭证 ID
    "expireAt": "2026-07-28T15:05:00+08:00",  // 截止时间（锁座/待支付）
    "createdAt": "2026-07-28T14:50:00+08:00",  // 创建时间
    "payAt": null  // 支付时间；未支付为 null
  },
  "traceId": "tr_order_1"  // 链路追踪 ID，用于排障与日志关联
}

```

**错误：** `LOCK_EXPIRED` / `FORBIDDEN` / `CONFLICT`（锁已关联其他单）/ `NOT_FOUND`

---

#### 6.2 模拟支付（二维码 · 唯一出票入口）

MVP 模拟支付采用 **支付 QR + 手机 H5 确认 + 取票 QR** 三段式（领域规则见 §3.2.6；事务见 §6.3）。

| 步骤 | 接口 | 说明 |
|------|------|------|
| 1 | `GET /orders/{orderId}/pay-qrcode` | PC/Agent 展示支付 QR |
| 2 | `GET /orders/{orderId}/pay-session` | 手机扫码后拉摘要（验 `payToken`） |
| 3 | `POST /orders/{orderId}/pay` | 用户显式确认 → 出票 + 取票 QR |
| 4 | `GET /tickets/verify` | 可选：核验取票 QR |

**硬约束：** **禁止**注册为 Agent Tool；`payToken` 不能自动代付，须 H5/PC **点击**触发 `POST /pay`。

---

##### 6.2.1 获取支付二维码

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /orders/{orderId}/pay-qrcode` |
| 权限 | 登录 + 本人 + `status=pending_pay` |
| 副作用 | 签发 `payToken`；写 Redis `pay:token:{jti}` |

**入参 Path：** `orderId`

**出参 data：** PayQrVO

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": "o_1001",
    "amount": 110.0,
    "expireAt": "2026-07-28T15:05:00+08:00",
    "payUrl": "https://ticket.example.com/m/pay/o_1001?t=eyJhbGciOiJIUzI1NiJ9...",
    "pollIntervalMs": 2000
  },
  "traceId": "tr_pay_qr_1"
}
```

**错误：** `NOT_FOUND` / `FORBIDDEN` / `CONFLICT`（非 `pending_pay`）/ `LOCK_EXPIRED`

> 前端将 `payUrl` 编码为 QR 图；PC 按 `pollIntervalMs` 轮询 `GET /orders/{orderId}` 直至 `status=issued`。

---

##### 6.2.2 手机支付会话（扫码落地）

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /orders/{orderId}/pay-session` |
| 权限 | **公开**；Query `t` = 有效 `payToken`（无需 JWT） |
| 说明 | 供 H5 `/m/pay/:orderId` 展示订单摘要 |

**入参 Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| t | string | 是 | `payToken` |

**出参 data：** PaySessionVO

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": "o_1001",
    "amount": 110.0,
    "expireAt": "2026-07-28T15:05:00+08:00",
    "movieTitle": "流浪地球 3",
    "cinemaName": "万达影城（五角场店）",
    "hallName": "1号厅",
    "startTime": "2026-07-28T14:10:00+08:00",
    "seatIds": ["sm1:6:7", "sm1:6:8"],
    "status": "pending_pay"
  },
  "traceId": "tr_pay_session_1"
}
```

**错误：** `PAY_TOKEN_INVALID` / `PAY_TOKEN_EXPIRED` / `NOT_FOUND` / `LOCK_EXPIRED` / `CONFLICT`（已出票则返回 issued 摘要或引导出票页）

---

##### 6.2.3 确认支付（出票）

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /orders/{orderId}/pay` |
| 权限 | 本人 JWT **或** Header `X-Pay-Token`（与 orderId 匹配） |
| 硬约束 | 仅用户显式点击触发 |

**入参 Path：** `orderId`  
**入参 Header（二选一）**

| Header | 说明 |
|--------|------|
| `Authorization: Bearer …` | PC 直接支付 |
| `X-Pay-Token: {payToken}` | 手机 H5 扫码支付 |

**入参 Body**

| 字段 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| mockResult | string | 否 | `success` | `success` \| `fail`（P1 演练） |
| channel | string | 否 | `desktop_button` | `desktop_button` \| `mobile_qr` |
| sessionId | string | 否 | | 传则回写 Draft→`TicketIssued` |

**出参 data：** OrderVO（`status=issued`，含 `ticketCode`、`qrPayload`、`payAt`、`payChannel`）

**副作用：** seats `locked→sold`；lock `consumed`；DEL `pay:token:{jti}`；生成签名 `qrPayload`

**请求（手机 H5）**

```http
POST /api/v1/orders/o_1001/pay
X-Pay-Token: eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json
Idempotency-Key: idem_pay_o_1001

{
  "mockResult": "success",
  "channel": "mobile_qr",
  "sessionId": "sess_1"
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": "o_1001",
    "userId": "u1",
    "showId": "s900",
    "movieTitle": "流浪地球 3",
    "cinemaName": "万达影城（五角场店）",
    "hallName": "1号厅",
    "startTime": "2026-07-28T14:10:00+08:00",
    "seatIds": ["sm1:6:7", "sm1:6:8"],
    "unitPrice": 55.0,
    "amount": 110.0,
    "status": "issued",
    "ticketCode": "TKT-20260728-1001",
    "qrPayload": "MIAOYU|v1|o_1001|u1|1722159090|TKT-20260728-1001|a1b2c3d4e5f67890",
    "lockId": "lk_01HZX",
    "expireAt": null,
    "payChannel": "mobile_qr",
    "createdAt": "2026-07-28T14:50:00+08:00",
    "payAt": "2026-07-28T14:51:30+08:00"
  },
  "traceId": "tr_pay_1"
}
```

**错误：** `LOCK_EXPIRED` / `PAY_TOKEN_INVALID` / `PAY_TOKEN_EXPIRED` / `CONFLICT`（非 `pending_pay`）/ `FORBIDDEN`；P1 `mockResult=fail` 时可用 `CONFLICT` + message

---

##### 6.2.4 取票核验

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /tickets/verify` |
| 权限 | 公开（演示/运营验票）；生产可加 staff 鉴权 |
| 说明 | 解析出票 `qrPayload`，验签后返回订单摘要 |

**入参 Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| payload | string | 是 | URL 编码的完整 `qrPayload` |

**出参 data：** TicketVerifyVO

**响应 200（有效）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "valid": true,
    "orderId": "o_1001",
    "ticketCode": "TKT-20260728-1001",
    "movieTitle": "流浪地球 3",
    "cinemaName": "万达影城（五角场店）",
    "hallName": "1号厅",
    "startTime": "2026-07-28T14:10:00+08:00",
    "seatIds": ["sm1:6:7", "sm1:6:8"],
    "payAt": "2026-07-28T14:51:30+08:00",
    "userId": "u1"
  },
  "traceId": "tr_verify_1"
}
```

**响应 200（无效票）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "valid": false,
    "reason": "TICKET_INVALID"
  },
  "traceId": "tr_verify_fail"
}
```

**错误：** `VALIDATION_ERROR`（payload 缺失）

---

#### 6.3 取消订单

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /orders/{orderId}/cancel` |
| 权限 | 本人 |

**入参 Path JSON（示意）**

```json
{
  "orderId": "o_1001"
}
```

**入参 Body（可选）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| reason | string | 否 | 如 `user_cancel` |

**入参 JSON**

```json
{
  "reason": "user_cancel"  // 取消原因，如 user_cancel
}
```

**出参 data：** OrderVO（`status=cancelled`）  
**副作用：** 释放仍 active 的锁座

**出参 JSON**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "orderId": "o_1001",  // 订单 ID
    "userId": "u1",  // 用户 ID
    "showId": "s900",  // 场次 ID
    "movieTitle": "流浪地球 3",  // 影片名快照
    "cinemaName": "万达影城（五角场店）",  // 影院名快照
    "hallName": "1号厅",  // 影厅名称
    "startTime": "2026-07-28T14:10:00+08:00",  // 开场时间 ISO-8601
    "seatIds": [  // 系统 seatId 列表
      "sm1:6:7",
      "sm1:6:8"
    ],
    "unitPrice": 55.0,  // 单价快照
    "amount": 110.0,  // 订单金额
    "status": "cancelled",  // 资源状态（随接口变化）
    "ticketCode": null,  // 取票码；出票后填充
    "qrPayload": null,  // 取票 QR 签名载荷；出票后填充
    "lockId": "lk_01HZX",  // 锁座凭证 ID
    "expireAt": null,  // 截止时间（锁座/待支付）
    "createdAt": "2026-07-28T14:50:00+08:00",  // 创建时间
    "payAt": null  // 支付时间；未支付为 null
  },
  "traceId": "tr_cancel_1"  // 链路追踪 ID，用于排障与日志关联
}

```

---

#### 6.4 订单列表

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /orders` |
| 权限 | 登录本人（工作人员只读协助见 §10.10 `GET /admin/orders`） |

**入参 Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | string | 否 | `pending_pay` / `issued` / `cancelled` |
| page | int | 否 | |
| size | int | 否 | |

**出参 data：** `{ items: OrderVO[], page, size, total }`

**入参 Query JSON（示意）**

```json
{
  "status": "issued",
  "page": 1,
  "size": 10
}
```

**响应 200（节选）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "orderId": "o_1001",
        "userId": "u1",
        "showId": "s900",
        "movieTitle": "流浪地球 3",
        "cinemaName": "万达影城（五角场店）",
        "hallName": "1号厅",
        "startTime": "2026-07-28T14:10:00+08:00",
        "seatIds": ["sm1:6:7", "sm1:6:8"],
        "unitPrice": 55.0,
        "amount": 110.0,
        "status": "issued",
        "ticketCode": "TKT-20260728-1001",
        "qrPayload": "MIAOYU|v1|o_1001|u1|1722159090|TKT-20260728-1001|a1b2c3d4e5f67890",
        "lockId": "lk_01HZX",
        "expireAt": null,
        "createdAt": "2026-07-28T14:50:00+08:00",
        "payAt": "2026-07-28T14:51:30+08:00"
      }
    ],
    "page": 1,
    "size": 10,
    "total": 1
  },
  "traceId": "tr_orders_list_1"
}
```

---

#### 6.5 订单详情

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /orders/{orderId}` |
| 权限 | 本人 |
| 出参 data | OrderVO |
| 错误 | `FORBIDDEN` / `NOT_FOUND` |

**入参 Path JSON（示意）**

```json
{
  "orderId": "o_1001"
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "orderId": "o_1001",
    "userId": "u1",
    "showId": "s900",
    "movieTitle": "流浪地球 3",
    "cinemaName": "万达影城（五角场店）",
    "hallName": "1号厅",
    "startTime": "2026-07-28T14:10:00+08:00",
    "seatIds": ["sm1:6:7", "sm1:6:8"],
    "unitPrice": 55.0,
    "amount": 110.0,
    "status": "issued",
    "ticketCode": "TKT-20260728-1001",
    "qrPayload": "MIAOYU|v1|o_1001|u1|1722159090|TKT-20260728-1001|a1b2c3d4e5f67890",
    "lockId": "lk_01HZX",
    "expireAt": null,
    "createdAt": "2026-07-28T14:50:00+08:00",
    "payAt": "2026-07-28T14:51:30+08:00"
  },
  "traceId": "tr_order_get_1"
}
```

---

### 7. BookingDraft

#### 7.1 创建 Session

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /booking-drafts` |
| 权限 | 公开 |

**入参 Body**

| 字段 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| source | string | 否 | `manual` | `manual`/`agent`/`hybrid` |
| movieId | string | 否 | — | 预填；有则 state 可到 `SelectCinema` |

**入参 JSON**

```json
{
  "source": "manual",
  "movieId": "m100"
}
```

**出参 data：** BookingDraftVO（新 `sessionId`，`version=0`）

**响应 200**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "sessionId": "sess_01HZX",  // 购票/Agent 会话 ID
    "userId": null,  // 用户 ID
    "source": "manual",  // 来源：manual / agent / hybrid
    "state": "Idle",  // BookingState 当前步
    "intent": null,  // 意图：buy_ticket / browse / modify / cancel / chitchat
    "movieId": null,  // 影片 ID
    "count": 1,  // 票数（1–4）
    "seatIds": [],  // 系统 seatId 列表
    "lockId": null,  // 锁座凭证 ID
    "orderId": null,  // 订单 ID
    "listContext": null,  // 当前列表上下文，供指代「第 N 个」
    "version": 0,  // Draft 乐观锁版本
    "updatedAt": "2026-07-28T14:00:00+08:00"  // 最后更新时间
  },
  "traceId": "tr_draft_create"  // 链路追踪 ID，用于排障与日志关联
}

```

---

#### 7.2 获取 Draft（hydrate）

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /booking-drafts/{sessionId}` |
| 权限 | 公开；含敏感锁/单字段时仅本人 |

**入参 Path：** `sessionId`  
**行为：** 不存在可懒创建 Idle Draft  
**出参 data：** BookingDraftVO

**入参 Path JSON（示意）**

```json
{
  "sessionId": "sess_01HZX"
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "sess_01HZX",
    "userId": "u1",
    "source": "hybrid",
    "state": "SelectShow",
    "intent": "buy_ticket",
    "movieId": "m100",
    "cinemaId": "c12",
    "showId": null,
    "count": 2,
    "seatIds": [],
    "preferRow": "middle",
    "preferSide": "center",
    "lockId": null,
    "orderId": null,
    "expireAt": null,
    "listContext": { "type": "shows", "ids": ["s900", "s901"] },
    "version": 5,
    "updatedAt": "2026-07-28T14:30:00+08:00"
  },
  "traceId": "tr_draft_get_1"
}
```

---

#### 7.3 更新 Draft

| 项 | 内容 |
|----|------|
| 方法/路径 | `PUT /booking-drafts/{sessionId}` |
| 权限 | 已绑定 userId 则须本人；匿名可写未锁座字段 |

**入参 Path：** `sessionId`  
**入参 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| version | long | 是 | 客户端持有版本 |
| patch | object | 是 | 部分字段；服务端依赖清空 |

**patch 允许字段：**  
`source`, `state`, `intent`, `movieId`, `filmTitle`, `genre`, `date`, `timeWindow`, `lat`, `lng`, `cinemaId`, `showId`, `count`, `seatIds`, `preferRow`, `preferSide`, `together`, `budgetMax`, `listContext`

**禁止客户端写：** `lockId` / `orderId` / `expireAt`（仅中台写）

**出参 data：** 最新 BookingDraftVO（`version+1`）

**请求**

```json
{
  "version": 2,  // Draft 乐观锁版本；更新时必传客户端持有版本
  "patch": {  // 部分字段补丁；服务端按依赖清空
    "movieId": "m100",  // 影片 ID
    "state": "SelectCinema"  // BookingState 当前步
  }
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "sess_01HZX",
    "userId": "u1",
    "source": "manual",
    "state": "SelectCinema",
    "intent": "buy_ticket",
    "movieId": "m100",
    "cinemaId": null,
    "showId": null,
    "count": 2,
    "seatIds": [],
    "lockId": null,
    "orderId": null,
    "version": 3,
    "updatedAt": "2026-07-28T14:32:00+08:00"
  },
  "traceId": "tr_draft_patch_1"
}
```

**DRAFT_CONFLICT（version 不一致）**

```json
{
  "code": -1,
  "message": "draft version mismatch",
  "data": {
    "errorCode": "DRAFT_CONFLICT",
    "serverDraft": {  // 服务端当前 Draft（冲突时回传）
      "sessionId": "sess_01HZX",  // 购票/Agent 会话 ID
      "version": 5,  // Draft 乐观锁版本
      "state": "SelectShow",  // BookingState 当前步
      "movieId": "m100",  // 影片 ID
      "cinemaId": "c12"  // 影院 ID
    }
  },
  "traceId": "tr_draft_conflict"  // 链路追踪 ID，用于排障与日志关联
}

```

---

### 8. Agent

> **部署说明（v4.4）：** `POST /agent/turns` 由 **ticket-agent（Python LangChain）** 实现并对外暴露（或经网关转发）。**Java 中台不实现 Turn 逻辑**；本章定义 **跨服务契约**，供前端与 Python 实现对齐。中台提供 Draft/Message 持久化（§7.7、§8.2、§8.3）及 Tool REST（§2–§6）。

#### 8.1 一轮对话 Turn（核心 · Python Agent 服务）

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /agent/turns` |
| **部署** | **ticket-agent（Python）**；Base URL 可与中台相同（网关分流） |
| 权限 | 浏览可匿名；执行 lock/createOrder 前须登录 |
| 硬约束 | LangChain Tool 白名单**无** `payMock`；支付走中台 §6.2 |

**入参 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| sessionId | string | 否 | 空则服务端创建 |
| message | string | 条件 | 与 `cardAction` 至少填一个 |
| cardAction | object | 条件 | 见下 |
| clientDraftVersion | long | 否 | 乐观锁，冲突 `DRAFT_CONFLICT` |
| debug | boolean | 否 | `true` 返回 `toolTraces` |

**cardAction**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cardId | string | 是 | |
| actionId | string | 是 | |
| itemId | string | 否 | 列表项 |
| draftPatch | object | 否 | 覆盖/补充 |

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| sessionId | string | |
| replyText | string | 助手回复 |
| draft | BookingDraftVO | 最新草稿 |
| cards | AgentCardVO[] | 本轮卡片，可空 |
| progress | object | 见下 |
| needLogin | boolean | 下一步需登录时 true |
| events | string[] | 埋点事件名 |
| toolTraces | object[] | 仅 debug |

**progress**

| 字段 | 类型 | 说明 |
|------|------|------|
| steps | string[] | `["选片","影院","场次","选座","支付"]` |
| currentIndex | int | 0–4；出票后 5 表示满格 |
| state | string | BookingState |

**toolTraces[]**

| 字段 | 类型 | 说明 |
|------|------|------|
| tool | string | |
| input | object | |
| outputSummary | object | |
| latencyMs | int | |
| success | boolean | |

**卡片 payload 细则**

| type | payload 字段 |
|------|----------------|
| movie_list | `movies: MovieVO[]` |
| cinema_list | `cinemas: CinemaVO[]` |
| show_list | `shows: ShowVO[]`, `date: string` |
| seat_plans | `plans: SeatPlanVO[]`, `compromise?` |
| order_confirm | `order: OrderVO` |
| pay_mock | `orderId`, `amount`, `expireAt`, `payUrl`, `pollIntervalMs`, `movieTitle`, `seatIds` |
| ticket_issued | `order: OrderVO` |
| ask | `slot`, `prompt`, `suggestions: string[]` |
| error | `code`, `message`, `alternatives?: SeatPlanVO[]` |

##### 示例 A — 模糊意图

**请求**

```json
{
  "sessionId": "sess_1",  // 购票/Agent 会话 ID
  "message": "周末想看个喜剧"  // 用户文本消息；与 cardAction 至少填一个
}
```

**响应 200**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "sessionId": "sess_1",  // 购票/Agent 会话 ID
    "replyText": "这几部喜剧口碑不错，点一张卡片就行。",  // 助手回复话术
    "draft": {  // 最新 BookingDraft
      "sessionId": "sess_1",  // 购票/Agent 会话 ID
      "userId": null,  // 用户 ID
      "source": "agent",  // 来源：manual / agent / hybrid
      "state": "SelectMovie",  // BookingState 当前步
      "intent": "buy_ticket",  // 意图枚举
      "genre": "喜剧",  // 类型筛选槽
      "date": "2026-08-01",  // 业务日 YYYY-MM-DD
      "count": 1,  // 票数（1–4）
      "seatIds": [],  // 系统 seatId 列表
      "listContext": {  // 当前列表上下文，供指代「第 N 个」
        "type": "movies",  // 卡片或列表类型
        "ids": [  // 列表项 ID 数组
          "m201",
          "m202",
          "m203"
        ],
        "labels": [  // 列表项展示文案
          "年会不能停！",
          "热辣滚烫",
          "喜剧片C"
        ]
      },
      "version": 2,  // Draft 乐观锁版本
      "updatedAt": "2026-07-28T14:10:00+08:00"  // 最后更新时间
    },
    "cards": [  // 本轮动态卡片
      {
        "cardId": "c_mov_1",  // 卡片 ID
        "type": "movie_list",  // 卡片或列表类型
        "title": "喜剧推荐",  // 卡片标题
        "payload": {  // 卡片业务载荷（随 type 变化）
          "movies": [  // 影片列表
            {
              "movieId": "m201",  // 影片 ID
              "title": "年会不能停！",  // 片名或卡片标题
              "posterUrl": "https://cdn.example/m201.jpg",  // 海报 URL
              "genres": [  // 类型标签列表
                "喜剧"
              ],
              "rating": 8.2,  // 评分；无评分可为 null
              "durationMin": 118,  // 片长（分钟）
              "releaseDate": "2026-01-10",  // 上映日 YYYY-MM-DD
              "status": "hot_showing",  // 影片状态：hot_showing / coming_soon / off
              "description": "打工人逆袭……",  // 简介
              "cast": "大鹏 / 白客",  // 主演文案
              "wantSeeCount": 5600  // 想看人数
            }
          ]
        },
        "actions": [  // 卡片可点动作
          {
            "actionId": "select",  // 动作 ID，如 select / confirm / go_pay
            "label": "选这部",  // 按钮文案
            "itemId": "m201",  // 列表项 ID
            "draftPatch": {  // 建议合并进 Draft 的字段补丁
              "movieId": "m201"  // 影片 ID
            }
          }
        ]
      }
    ],
    "progress": {  // 购票五步进度
      "steps": [  // 步骤名列表
        "选片",
        "影院",
        "场次",
        "选座",
        "支付"
      ],
      "currentIndex": 0,  // 当前步骤下标（0–4；出票后可为 5）
      "state": "SelectMovie"  // BookingState 当前步
    },
    "needLogin": false,  // 下一步是否需要登录
    "events": [  // 本轮埋点事件名
      "intent_parsed",
      "card_show"
    ]
  },
  "traceId": "tr_turn_1"  // 链路追踪 ID，用于排障与日志关联
}

```

##### 示例 B — 点卡推进

**请求**

```json
{
  "sessionId": "sess_1",  // 购票/Agent 会话 ID
  "clientDraftVersion": 2,  // 客户端 Draft 版本；冲突返回 DRAFT_CONFLICT
  "cardAction": {  // 点卡动作；与 message 至少填一个
    "cardId": "c_mov_1",  // 卡片 ID
    "actionId": "select",  // 动作 ID，如 select / confirm / go_pay / payment_done
    "itemId": "m201",  // 列表项 ID
    "draftPatch": {  // 建议合并进 Draft 的字段补丁
      "movieId": "m201"  // 影片 ID
    }
  }
}
```

**响应要点：** `draft.state=SelectCinema`，`cards` 含 `cinema_list`，`events` 含 `slot_updated`。

##### 示例 C — 支付完成后刷新对话态

**请求**

```json
{
  "sessionId": "sess_1",  // 购票/Agent 会话 ID
  "cardAction": {  // 点卡动作；与 message 至少填一个
    "cardId": "pay_done",  // 卡片 ID
    "actionId": "payment_done",  // 动作 ID，如 select / confirm / go_pay / payment_done
    "draftPatch": {  // 建议合并进 Draft 的字段补丁
      "orderId": "o_1001"  // 订单 ID
    }
  }
}
```

**行为：** 校验订单已 `issued` → 推 `ticket_issued` 卡，`draft.state=TicketIssued`。

##### Agent 可调用 Tool 白名单（LangChain Tool → 中台 HTTP）

| Tool | 对应中台能力 | 副作用 | 是否 Agent Tool |
|------|--------------|--------|-----------------|
| searchMovies | GET /movies | 无 | ✓ |
| recommendMovies | GET /reco/personal | 无 | ✓ |
| getMovie | GET /movies/{id} | 无 | ✓ |
| searchCinemas | GET /cinemas | 无 | ✓ |
| listShows | GET /shows | 无 | ✓ |
| getSeatMap | GET seat-map | 无 | ✓ |
| recommendSeats | POST /reco/seats | 无 | ✓ |
| lockSeats | POST /locks | 写 | ✓ |
| unlockSeats | DELETE /locks/{id} | 写 | ✓ |
| createOrder | POST /orders | 写 | ✓ |
| getOrder | GET /orders/{id} | 无 | ✓ |
| payMock | POST /orders/{id}/pay | 写 | **✗ 禁止** |

---

#### 8.2 Agent 历史消息（**ticket-agent 实现**，读自有库）

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /agent/sessions/{sessionId}/messages`（Agent 服务；亦可仅 Turn 内使用） |
| 权限 | 会话所有者（匿名 session 持有即可） |
| 存储 | Agent SQLAlchemy `agent_message` |

**入参 Query**

| 字段 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| limit | int | 否 | 20 | 最大 50 |
| beforeId | string | 否 | — | 游标翻页 |

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| items | object[] | 见下 |

**items[]**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | messageId |
| role | string | `user` / `assistant` / `system` |
| text | string | |
| cards | AgentCardVO[] \| null | |
| createdAt | string | |

**入参 Query JSON（示意）**

```json
{
  "sessionId": "sess_1",
  "limit": 20,
  "beforeId": "msg_009"
}
```

**响应 200（节选）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "msg_010",
        "role": "user",
        "text": "帮我买两张流浪地球3的票",
        "cards": null,
        "createdAt": "2026-07-28T14:00:01+08:00"
      },
      {
        "id": "msg_011",
        "role": "assistant",
        "text": "好的，请先选择影院：",
        "cards": [
          {
            "cardId": "card_cinema_1",
            "type": "cinema_list",
            "title": "附近影院",
            "payload": {
              "cinemas": [
                {
                  "cinemaId": "c12",
                  "name": "万达影城（五角场店）",
                  "address": "淞沪路 77 号",
                  "distanceMeters": 1200,
                  "minPrice": 45.0
                }
              ]
            },
            "actions": [
              {
                "actionId": "select",
                "label": "选这家",
                "draftPatch": { "cinemaId": "c12" },
                "itemId": "c12"
              }
            ]
          }
        ],
        "createdAt": "2026-07-28T14:00:02+08:00"
      }
    ]
  },
  "traceId": "tr_agent_msgs_1"
}
```

---

#### 8.3 追加对话消息（**Agent 自有库**，Turn 内写入）

| 项 | 内容 |
|----|------|
| 实现 | Turn 结束时 SQLAlchemy `INSERT agent_message`（user + assistant） |
| 对外 API | 可选暴露于 **ticket-agent**；**中台不再提供** |
| 权限 | **废除** `X-Internal-Api-Key`；若对外则会话方/登录用户 |
| 说明 | 主要由 ProcessAgentTurn 内部调用；不对浏览器公开写库后门 |

**入参 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| messages | object[] | 是 | 1–2 条（本轮 user + assistant） |

**messages[] 元素**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| role | string | 是 | `user` \| `assistant` \| `system` |
| content | string | 是 | 文本 |
| cardsJson | object | 否 | assistant 轮的 cards 快照 |
| eventsJson | object | 否 | 埋点事件 |

**请求示例**

```json
{
  "messages": [
    { "role": "user", "content": "周末想看个喜剧" },
    {
      "role": "assistant",
      "content": "这几部喜剧口碑不错，点一张卡片就行。",
      "cardsJson": [{ "cardId": "card_m1", "type": "movie_list", "title": "…", "payload": {} }]
    }
  ]
}
```

**出参 data：** `{ "saved": 2 }`

---

### 9. 用户画像（P1）

#### 9.1 获取画像

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /me/profile` |
| 权限 | 登录 |

**出参 data**

| 字段 | 类型 | 说明 |
|------|------|------|
| preferGenres | string[] | |
| preferRow | string \| null | |
| preferSide | string \| null | |
| wantSeeMovieIds | string[] | 可与 want-see 表同步 |

**入参 JSON（示意）**

```json
{
  // 无 Body；依赖 Header Authorization
}
```

**出参 JSON**

```json
{
  "code": 200
  "message": "success",  // 人类可读说明
  "data": {  // 业务载荷；错误时可含 details
    "preferGenres": [  // 偏好类型
      "喜剧",
      "科幻"
    ],
    "preferRow": "middle",  // 偏好排区：front / middle / back
    "preferSide": "center",  // 偏好左右：center / aisle / edge
    "wantSeeMovieIds": [  // 想看影片 ID 列表
      "m100",
      "m201"
    ]
  },
  "traceId": "tr_profile_1"  // 链路追踪 ID，用于排障与日志关联
}

```

---

#### 9.2 更新画像

| 项 | 内容 |
|----|------|
| 方法/路径 | `PUT /me/profile` |
| 权限 | 登录 |

**入参 JSON**

```json
{
  "preferGenres": [  // 偏好类型
    "喜剧"
  ],
  "preferRow": "middle",  // 偏好排区：front / middle / back
  "preferSide": "center"  // 偏好左右：center / aisle / edge
}
```

**出参 JSON：** 同获取画像（更新后）

---

### 10. 运营管理（Admin / Staff）

> 详见下方 **§10.0**（v4.8 staff 影院隔离）。路径兜底 staff|admin；新建影院 / 账号 / 推荐权重仅 admin；staff 写操作须通过 `assertCinemaScope`。

#### 10.0 角色与权限矩阵

> **权限总则（v4.8）：** 本章及 §5.0 建图接口不对 `role=user` 开放。须登录后携带 JWT。  
> - 路径兜底：`/admin/**`、`/seat-maps/**`、`POST /halls` → `hasAnyRole('staff','admin')`  
> - **影院创建** `POST /admin/cinemas`、**账号管理** §10.12、**推荐权重** §10.11 → **仅 `@Admin`**  
> - **staff 数据范围：** 仅 `user_account.cinema_id` 对应影院的厅 / 座位图 / 场次 / 订单协助；**可**维护全平台影片目录；**不可**新建影院  
> - 未登录 → `401`；角色不符或越权影院 → `403` / `CINEMA_SCOPE_DENIED`

| 角色 | JWT `role` | `cinemaId` Claim | 数据范围 | 可访问 |
|------|------------|------------------|----------|--------|
| **工作人员** | `staff` | **必有**（= `user_account.cinema_id`） | **仅本影院**运营数据 + 全平台影片目录 | §10.1–10.2（影片）；§10.4（仅本影院）；§10.5–10.10；§5.0（本影院图/厅） |
| **系统管理员** | `admin` | null | 全平台 | staff 能力 + 新建影院 + §10.11 + §10.12 |

| 能力 | staff | admin |
|------|:-----:|:-----:|
| 新建影院 `POST /admin/cinemas` | — | ✓ |
| 更新影院（本影院 / 任意） | ✓ 本影院 | ✓ |
| 影片目录 CRUD | ✓ | ✓ |
| 本影院影厅 / 座位图 / 排片 | ✓ | ✓ |
| 他院影厅 / 座位图 / 排片 | — | ✓ |
| 订单协助查询 | ✓ 本影院场次 | ✓ 全量 |
| 推荐权重 / Banner（P1） | — | ✓ |
| 用户账号 CRUD、改 role、绑影院 | — | ✓ |

**Service 层影院范围校验（所有运营写/读协助接口必须实现）：**

```text
function assertCinemaScope(resourceCinemaId):
  if role == admin: return
  if role == staff and resourceCinemaId == jwt.cinemaId: return
  raise CINEMA_SCOPE_DENIED  # 或 FORBIDDEN
```

| 资源 | staff 如何取 resourceCinemaId |
|------|------------------------------|
| cinema | path `cinemaId` |
| hall | `hall.cinema_id` |
| seat_map | `seat_map.cinema_id` |
| show | `show.cinema_id`（创建时 Body.cinemaId 亦须等于本影院） |
| admin orders | join `show_schedule.cinema_id` |

**与 C 端区分：** C 端 `GET /movies` 等只读仍公开；写操作走 `/admin/*` 或 §5.0。

**已有运营接口（见 §5.0）：**

| 方法 | 路径 | 节 | staff 范围 |
|------|------|-----|------------|
| POST | `/seat-maps` | 5.0.1 | Body/`cinemaId` 强制本影院 |
| GET | `/seat-maps` | 5.0.2 | 仅列出本影院图（admin 可按 cinemaId 筛） |
| GET | `/seat-maps/{id}` | 5.0.3 | 本影院 |
| PUT | `/seat-maps/{id}` | 5.0.5 | 本影院 |
| DELETE | `/seat-maps/{id}` | 5.0.6 | 本影院 |
| POST | `/halls` | 5.0.4 | Body.cinemaId 须本影院 |

---

#### 10.1 新建影片

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /admin/movies` |
| 权限 | **staff / admin**（影片为**全平台目录**，不做影院隔离） |

**入参 Body（MovieCreateDTO）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| movieId | string | 否 | 不传则服务端生成 |
| title | string | 是 | |
| posterUrl | string | 是 | |
| genres | string[] | 是 | 至少 1 项 |
| rating | number | 否 | |
| durationMin | int | 是 | >0 |
| releaseDate | string | 是 | `YYYY-MM-DD` |
| status | string | 否 | 默认 `coming_soon`；`hot_showing`/`coming_soon`/`off` |
| description | string | 是 | |
| cast | string | 否 | |

**出参 data：** MovieVO

**入参 JSON**

```json
{
  "title": "新片示例",
  "posterUrl": "https://cdn.example/new.jpg",
  "genres": ["喜剧"],
  "durationMin": 120,
  "releaseDate": "2026-08-01",
  "status": "coming_soon",
  "description": "简介……",
  "cast": "演员 A / 演员 B"
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "movieId": "m_new_1",
    "title": "新片示例",
    "posterUrl": "https://cdn.example/new.jpg",
    "genres": ["喜剧"],
    "rating": null,
    "durationMin": 120,
    "releaseDate": "2026-08-01",
    "status": "coming_soon",
    "description": "简介……",
    "cast": "演员 A / 演员 B",
    "wantSeeCount": 0
  },
  "traceId": "tr_admin_movie_create"
}
```

---

#### 10.2 更新影片

| 项 | 内容 |
|----|------|
| 方法/路径 | `PUT /admin/movies/{movieId}` |
| 权限 | **staff / admin**（全平台目录） |
| 说明 | 全量或部分字段更新；`status=off` 等价下架，不物理删除 |

**入参 Path：** `movieId`  
**入参 Body：** 同创建（除 `movieId` 外均可选 PATCH 语义由服务端实现）

**出参 data：** MovieVO

**响应 200（节选 — 上架热映）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "movieId": "m_new_1",
    "title": "新片示例",
    "status": "hot_showing",
    "wantSeeCount": 0
  },
  "traceId": "tr_admin_movie_update"
}
```

---

#### 10.3 新建影院

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /admin/cinemas` |
| 权限 | **仅系统管理员（admin）**；staff → `403 FORBIDDEN` |

**入参 Body（CinemaCreateDTO）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cinemaId | string | 否 | |
| cityId | string | 否 | 默认 `city_sh` |
| name | string | 是 | |
| address | string | 是 | |
| lat | number | 是 | WGS84 |
| lng | number | 是 | |

**出参 data：** CinemaVO + 可选 `cityId`

**入参 JSON**

```json
{
  "name": "示例影城（新店）",
  "address": "示例路 100 号",
  "lat": 31.2304,
  "lng": 121.4737
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "cinemaId": "c_new_1",
    "name": "示例影城（新店）",
    "address": "示例路 100 号",
    "distanceMeters": null,
    "minPrice": null
  },
  "traceId": "tr_admin_cinema_create"
}
```

---

#### 10.4 更新影院

| 项 | 内容 |
|----|------|
| 方法/路径 | `PUT /admin/cinemas/{cinemaId}` |
| 权限 | **admin** 任意；**staff 仅当** `cinemaId == JWT.cinemaId` |

**入参 Body：** `name` / `address` / `lat` / `lng` / `cityId`（均可选）

**出参 data：** CinemaVO

---

#### 10.5 影厅列表

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /admin/halls` |
| 权限 | **staff / admin**；staff 的 Query.`cinemaId` **必须**等于本影院（可省略，服务端默认本影院） |

**入参 Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cinemaId | string | 条件 | admin **必填**；staff 可省略（默认 JWT.cinemaId），若传则必须等于本影院 |
| page | int | 否 | 默认 1 |
| size | int | 否 | 默认 20 |

**出参 data：** `{ items: HallAdminVO[], page, size, total }`

**HallAdminVO**

| 字段 | 类型 | 说明 |
|------|------|------|
| hallId | string | |
| cinemaId | string | |
| name | string | |
| seatMapId | string | |
| showCount | int | 已排片场次数 |

**响应 200（节选）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "hallId": "h1",
        "cinemaId": "c12",
        "name": "1号厅",
        "seatMapId": "sm_rect_1",
        "showCount": 12
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1
  },
  "traceId": "tr_admin_halls"
}
```

> 新建影厅仍用 §5.0.4 `POST /halls`（**staff / admin**）。

---

#### 10.6 更新影厅

| 项 | 内容 |
|----|------|
| 方法/路径 | `PUT /admin/halls/{hallId}` |
| 权限 | **staff / admin**；staff 须 `hall.cinema_id` 为本影院 |
| 说明 | 仅可改 `name`；**不可改绑** `seatMapId`（有场次时改绑会导致库存错位，须新建厅 + 新场次） |

**入参 Body**

| 字段 | 类型 | 必填 |
|------|------|------|
| name | string | 是 |

**出参 data：** `{ hallId, cinemaId, name, seatMapId }`

---

#### 10.7 排片（创建场次）

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /admin/shows` |
| 权限 | **staff / admin**；staff 的 Body.`cinemaId` **必须**为本影院，且与 hall 一致 |
| 说明 | 创建 `show_schedule` 并为该图下每个座位 **INSERT seat_status**（初始态取自 `seat.default_status`） |

**副作用：** 该 `seat_map` 标记 `mutable=false`；写入 `seat_status` 行数 = 该图座位数

> **v4.6：** 创建/更新请传 `zonePrices: [{ zone, price }, ...]`，须覆盖该厅座位图全部 `DISTINCT zone`。单一 `price` 仅作兼容冗余（最低区价）。

**入参 Body（ShowCreateDTO）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| showId | string | 否 | |
| movieId | string | 是 | |
| cinemaId | string | 是 | 须与 hall 所属影院一致 |
| hallId | string | 是 | |
| startTime | string | 是 | ISO-8601 |
| endTime | string | 是 | 须晚于 startTime |
| zonePrices | `{ zone: string, price: number }[]` | 是 | 覆盖座位图全部区；price>0 |
| price | number | 否 | 忽略；服务端回填 min(zonePrices) |

**入参 JSON**

```json
{
  "movieId": "m100",
  "cinemaId": "c12",
  "hallId": "h1",
  "startTime": "2026-07-29T19:30:00+08:00",
  "endTime": "2026-07-29T22:25:00+08:00",
  "zonePrices": [
    { "zone": "A", "price": 88.0 },
    { "zone": "B", "price": 68.0 },
    { "zone": "C", "price": 45.0 }
  ]
}
```

**响应 200**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "showId": "s_new_900",
    "movieId": "m100",
    "cinemaId": "c12",
    "hallId": "h1",
    "hallName": "1号厅",
    "startTime": "2026-07-29T19:30:00+08:00",
    "endTime": "2026-07-29T22:25:00+08:00",
    "price": 45.0,
    "zonePrices": [
      { "zone": "A", "price": 88.0 },
      { "zone": "B", "price": 68.0 },
      { "zone": "C", "price": 45.0 }
    ],
    "seatRemain": 120,
    "seatRemainLevel": "ample"
  },
  "traceId": "tr_admin_show_create"
}
```

**错误：** `VALIDATION_ERROR`（缺区/多余区/price≤0）；`NOT_FOUND`（movie/cinema/hall）；`CONFLICT`（同厅时间重叠）

---

#### 10.8 更新场次

| 项 | 内容 |
|----|------|
| 方法/路径 | `PUT /admin/shows/{showId}` |
| 权限 | **staff / admin**；staff 仅本影院场次 |
| 说明 | 仅 `status=on_sale` 且无锁座/未支付订单时可改 `startTime`/`endTime`/`zonePrices` |

**入参 Body：** 上述字段可选；若传 `zonePrices` 则整表替换且仍须覆盖座位图全部区

**出参 data：** ShowVO

**错误：** `CONFLICT`（已有锁座或订单）；`VALIDATION_ERROR`

---

#### 10.9 取消场次

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /admin/shows/{showId}/cancel` |
| 权限 | **staff / admin**；staff 仅本影院场次 |
| 说明 | `show.status → cancelled`；释放未出票锁座；已出票订单不受影响（演示可禁止取消） |

**入参：** 无 Body

**出参 data：** `{ showId, status: "cancelled" }`

**错误：** `CONFLICT`（存在已出票订单且策略禁止）

---

#### 10.10 订单协助查询

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /admin/orders` |
| 权限 | **staff / admin**（只读） |
| 说明 | 按用户/状态/日期筛选；**staff 仅本影院场次订单**；**不可**代客支付或取消 |

**入参 Query**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | string | 否 | 协助查某用户 |
| status | string | 否 | `pending_pay`/`issued`/`cancelled` |
| dateFrom | string | 否 | `YYYY-MM-DD` |
| dateTo | string | 否 | |
| page | int | 否 | |
| size | int | 否 | |

**出参 data：** `{ items: OrderVO[], page, size, total }`（OrderVO 含 `userId`）

**响应 200（节选）**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "orderId": "o_1001",
        "userId": "u1",
        "showId": "s900",
        "status": "pending_pay",
        "amount": 110.0,
        "seatIds": ["sm_rect_1:6:7", "sm_rect_1:6:8"],
        "expireAt": "2026-07-28T14:15:00+08:00"
      }
    ],
    "page": 1,
    "size": 20,
    "total": 1
  },
  "traceId": "tr_admin_orders"
}
```

---

#### 10.11 推荐权重配置（P1）

| 项 | 内容 |
|----|------|
| 方法/路径 | `PUT /admin/reco/weekly-hot` |
| 权限 | **仅系统管理员（admin）**（全平台/城市级配置） |
| 说明 | 覆盖周热门榜单权重或固定顺序；落库或 Redis `reco:weekly:{cityId}` |

**入参 Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| cityId | string | 否 | 默认 `city_sh` |
| movieIds | string[] | 是 | 有序列表 |

**出参 data：** `{ cityId, movieIds, updatedAt }`

---

#### 10.12 用户账号管理（仅系统管理员）

> **本节全部接口 role 必须为 `admin`**；staff 调用返回 `403 FORBIDDEN`。

##### 10.12.1 用户列表

| 项 | 内容 |
|----|------|
| 方法/路径 | `GET /admin/users` |
| 权限 | **仅系统管理员（admin）** |

**入参 Query：** `role`（可选）、`status`（0 禁用 / 1 正常）、`page`、`size`

**出参 data：** `{ items: AdminUserVO[], page, size, total }`

**AdminUserVO**

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | string | |
| nickname | string | |
| phone | string \| null | 脱敏 |
| role | string | `user`/`staff`/`admin` |
| cinemaId | string \| null | staff 必有；user/admin 为 null |
| status | int | 0 禁用 / 1 正常 |
| createdAt | string | |

##### 10.12.2 创建账号

| 项 | 内容 |
|----|------|
| 方法/路径 | `POST /admin/users` |
| 权限 | **仅系统管理员（admin）** |

**入参 Body（AdminUserCreateDTO）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| nickname | string | 是 | 唯一 |
| phone | string | 否 | 11 位，唯一 |
| password | string | 是 | 8–64 |
| role | string | 是 | `user`/`staff`/`admin` |
| cinemaId | string | 条件 | **`role=staff` 时必填**且影院须存在；user/admin 时必须省略或 null |

**出参 data：** AdminUserVO（不含 password）

**入参 JSON**

```json
{
  "nickname": "运营小李",
  "phone": "13900000002",
  "password": "ChangeMe123",
  "role": "staff",
  "cinemaId": "c12"
}
```

##### 10.12.3 更新账号

| 项 | 内容 |
|----|------|
| 方法/路径 | `PUT /admin/users/{userId}` |
| 权限 | **仅系统管理员（admin）** |

**入参 Body：** `nickname` / `phone` / `role` / `cinemaId` / `status` / `password`（均可选；改密须满足长度约束）

**出参 data：** AdminUserVO

**规则：**

- 禁止将**当前登录 admin** 的 `role` 降为 non-admin 或 `status=0`（防自锁）
- 至少保留一名 `role=admin` 且 `status=1`
- 改为 `staff` 时必须提供有效 `cinemaId`；改为 `user`/`admin` 时清空 `cinemaId`；保持 `staff` 时可改绑影院

---

### 11. 接口总表（索引）

| 方法 | 路径 | 权限 | 模块 | 节 |
|------|------|------|------|-----|
| POST | /auth/login | 公开 | 用户管理 | 1.1 |
| — | 静默续期 | Filter | 用户管理 | 1.2（无公开 API） |
| POST | /auth/logout | 登录 | 用户管理 | 1.3 |
| GET | /auth/me | 登录 | 用户管理 | 1.4 |
| GET | /movies | 公开 | 影片管理 | 2.1 |
| GET | /movies/{id} | 公开 | 影片管理 | 2.2 |
| POST | /movies/{id}/want-see | 登录 | 影片管理 | 2.3 |
| DELETE | /movies/{id}/want-see | 登录 | 影片管理 | 2.3 |
| GET | /me/want-see | 登录 | 影片管理 | 2.3 |
| GET | /cinemas | 公开 | 影院管理 | 3.1 |
| GET | /cinemas/{id} | 公开 | 影院管理 P1 | 3.2 |
| GET | /shows | 公开 | 场次管理 | 3.3 |
| GET | /shows/{id} | 公开 | 场次管理 | 3.4 |
| GET | /reco/weekly-hot | 公开 | 热门推荐系统 | 4.1 |
| GET | /reco/personal | 可选登录 | 热门推荐系统 | 4.2 |
| POST | /reco/seats | 登录 | 热门推荐系统 | 4.3 |
| POST | /seat-maps | **staff(本影院)/admin** | 座位管理 | 5.0.1 |
| GET | /seat-maps | **staff(本影院)/admin** | 座位管理 | 5.0.2 |
| GET | /seat-maps/{id} | **staff(本影院)/admin** | 座位管理 | 5.0.3 |
| PUT | /seat-maps/{id} | **staff(本影院)/admin** | 座位管理 | 5.0.5 |
| DELETE | /seat-maps/{id} | **staff(本影院)/admin** | 座位管理 | 5.0.6 |
| POST | /halls | **staff(本影院)/admin** | 影院管理 | 5.0.4 |
| GET | /shows/{id}/seat-map | 公开 | 座位管理 | 5.1 |
| POST | /locks | 登录 | 座位管理 | 5.2 |
| GET | /locks/{id} | 本人 | 座位管理 | 5.3 |
| DELETE | /locks/{id} | 本人 | 座位管理 | 5.4 |
| POST | /orders | 登录 | 订单管理 | 6.1 |
| GET | /orders/{id}/pay-qrcode | 本人 | 支付管理 | 6.2.1 |
| GET | /orders/{id}/pay-session | 公开+token | 支付管理 | 6.2.2 |
| POST | /orders/{id}/pay | 本人/token | 支付管理 | 6.2.3 |
| GET | /tickets/verify | 公开 | 支付管理 | 6.2.4 |
| POST | /orders/{id}/cancel | 本人 | 订单管理 | 6.3 |
| GET | /orders | 本人 | 订单管理 | 6.4 |
| GET | /orders/{id} | 本人 | 订单管理 | 6.5 |
| POST | /booking-drafts | 公开 | Agent/Draft | 7.1 |
| GET | /booking-drafts/{sid} | 公开/本人 | Agent/Draft | 7.2 |
| PUT | /booking-drafts/{sid} | 公开/本人 | Agent/Draft | 7.3 |
| POST | /agent/turns | 匿名→登录 | **Agent 服务(Python)** | 8.1 |
| GET | /agent/sessions/{sid}/messages | 会话方 | **Agent（Python）** | 8.2 |
| POST | /agent/sessions/{sid}/messages | Turn 内/可选对外 | **Agent 自有库** | 8.3 |
| GET | /me/profile | 登录 | 用户管理 P1 | 9.1 |
| PUT | /me/profile | 登录 | 用户管理 P1 | 9.2 |
| POST | /admin/movies | **staff/admin** | 运营管理 | 10.1 |
| PUT | /admin/movies/{id} | **staff/admin** | 运营管理 | 10.2 |
| POST | /admin/cinemas | **admin** | 运营管理 | 10.3 |
| PUT | /admin/cinemas/{id} | **staff(本影院)/admin** | 运营管理 | 10.4 |
| GET | /admin/halls | **staff(本影院)/admin** | 运营管理 | 10.5 |
| PUT | /admin/halls/{id} | **staff(本影院)/admin** | 运营管理 | 10.6 |
| POST | /admin/shows | **staff(本影院)/admin** | 运营管理 | 10.7 |
| PUT | /admin/shows/{id} | **staff(本影院)/admin** | 运营管理 | 10.8 |
| POST | /admin/shows/{id}/cancel | **staff(本影院)/admin** | 运营管理 | 10.9 |
| GET | /admin/orders | **staff(本影院)/admin** | 运营管理 | 10.10 |
| PUT | /admin/reco/weekly-hot | **admin** | 运营管理 P1 | 10.11 |
| GET | /admin/users | **admin** | 运营管理 | 10.12.1 |
| POST | /admin/users | **admin** | 运营管理 | 10.12.2 |
| PUT | /admin/users/{id} | **admin** | 运营管理 | 10.12.3 |

---

### 12. 普通购票最小调用链（对照时序）

```text
POST /auth/login
GET  /movies?status=hot_showing
GET  /movies/{movieId}
GET  /cinemas?movieId&lat&lng
GET  /shows?cinemaId&movieId&date
GET  /shows/{showId}/seat-map
POST /reco/seats                    # 可选智能选座
POST /locks
POST /orders
GET  /orders/{orderId}/pay-qrcode   # PC 展示支付 QR
GET  /orders/{orderId}/pay-session  # 手机 H5 扫码摘要（Query t=payToken）
POST /orders/{orderId}/pay          # 显式确认出票（Bearer 或 X-Pay-Token）
GET  /tickets/verify                # 可选：核验取票 QR
GET  /orders/{orderId}              # PC 轮询支付结果
```

Agent 路径以多次 `POST /agent/turns` 编排上述能力；支付须用户手机扫码或 PC 显式确认，仍走 `POST /orders/{id}/pay`（**禁止** Agent Tool）。

**运营建图最小调用链（对照前端系分 §9.7）：**

```text
POST /auth/login                    # staff / admin（role 校验）
GET  /seat-maps                     # 列表页
POST /seat-maps                     # 画布保存稀疏图
PUT  /seat-maps/{seatMapId}         # 改图（mutable=true）
GET  /seat-maps/{seatMapId}         # 回显；mutable=false 时前端只读
POST /halls                         # 新建厅并绑图
POST /admin/shows                   # 排片 → 展开 seat_status
```

**运营管理完整链路（§10）：**

```text
POST /auth/login                    # staff / admin
POST /admin/movies                  # 建片
POST /admin/cinemas                 # 建院
POST /seat-maps + POST /halls       # 建图绑厅
POST /admin/shows                   # 排片
GET  /admin/orders                  # 协助查单（staff/admin）
GET  /admin/users                   # 账号管理（admin only）
```

---

## 8. 流程 / UML / 时序图

渲染：Mermaid。本章含用例图、状态机、类图、组件图与时序图。

### 1. 用例图（UML Use Case）

```mermaid
flowchart LR
  U((用户))
  S((工作人员))
  A((系统管理员))

  subgraph Catalog["目录与推荐"]
    UC1[浏览热映/待映]
    UC2[查看影片详情]
    UC3[每周热门]
    UC4[个人推荐]
    UC5[想看收藏]
  end

  subgraph Manual["普通购票"]
    UC6[选影院/场次]
    UC7[手选座位]
    UC8[智能选座]
    UC9[锁座]
    UC10[确认订单]
    UC11[模拟支付出票]
    UC12[查看我的订单]
  end

  subgraph AgentFlow["Agent 购票"]
    UC13[对话购票 Turn]
    UC14[点选动态卡片]
    UC15[确认支付按钮]
  end

  subgraph Staff["运营"]
    UC16[维护影片影院场次]
    UC17[配置推荐权重]
    UC18[协助查单]
    UC19[座位图画布建图]
    UC20[绑定影厅]
  end

  U --> UC1 & UC2 & UC3 & UC4 & UC5
  U --> UC6 & UC7 & UC8 & UC9 & UC10 & UC11 & UC12
  U --> UC13 & UC14 & UC15
  S --> UC16 & UC17 & UC18 & UC19 & UC20
  A --> UC16 & UC19 & UC20

  UC15 -.->|调用同一 pay API| UC11
  UC13 -.->|Tools 调用中台| UC9
  UC13 -.->|Tools| UC8
  UC19 -.->|POST /seat-maps| UC20
```

**关键约束（图外文字）：**

- `模拟支付出票` 必须由用户显式触发；Agent 用例不得包含「静默支付」。
- 普通购票与 Agent 购票共享锁座/订单用例实现（同一 Application Use Case）。
- 运营建图：前端画布（系分 §9.7）点选增删 → `POST /seat-maps` 稀疏落库；已排片图 `mutable=false`。

---

### 2. 业务总流程

#### 2.1 普通购票流程

```mermaid
flowchart TD
  A[首页热映/热门/个人推荐] --> B[影片详情]
  B --> C[选影院]
  C --> D[选场次]
  D --> E[座位图手选 / 智能选座]
  E --> F{POST /locks}
  F -->|SEAT_TAKEN| E
  F -->|成功| G[订单确认]
  G -->|去支付| H[模拟支付页]
  G -->|取消| R[DELETE /locks]
  R --> D
  H -->|GET pay-qrcode| Q[展示支付 QR]
  Q -->|手机扫码| M[H5 /m/pay/:orderId]
  M -->|POST pay 确认| I[出票成功]
  Q -->|PC 轮询 GET order| I
  H -->|TTL 到期| R
```

#### 2.2 Agent 购票流程

```mermaid
flowchart TD
  A[打开 Agent / 快捷 Chip] --> B[POST /agent/turns]
  B --> C{意图与槽位}
  C -->|缺槽| D[追问卡 ask / 列表卡]
  D --> B
  C -->|可推进| E[调用 Tools]
  E --> F[推动态卡片]
  F -->|用户点卡| B
  F -->|确认座位方案| G[lockSeats Tool]
  G -->|失败| H[error + 备选 seat_plans]
  H --> B
  G -->|成功| I[createOrder → order_confirm 卡]
  I -->|去支付| J[pay_mock 卡 + payUrl/QR]
  J -->|手机扫码 POST pay| K[REST 出票]
  J -->|PC 轮询| K
  K --> L[payment_done turn → ticket_issued]
```

#### 2.3 Agent Turn 内部活动图

```mermaid
flowchart TD
  S[接收 Turn 请求] --> L[加载 Session / Draft / Memory]
  L --> CA{有 cardAction?}
  CA -->|是| M1[确定性合并 draftPatch]
  CA -->|否| M0[跳过]
  M1 --> NLP
  M0 --> NLP{有 message?}
  NLP -->|是| EX[意图分类 + 槽位提取]
  NLP -->|否| PL
  EX --> MG[Merge SlotPatch + 依赖清空]
  MG --> PL[Planner 生成 PlanAction]
  PL --> AU{需要登录?}
  AU -->|是且未登录| NL[返回 needLogin + 引导文案]
  AU -->|否| TE[ToolExecutor]
  TE --> CC[CardComposer + 话术]
  CC --> PS[持久化 Draft / Message / 埋点]
  PS --> RS[返回 AgentTurnResponse]
  NL --> RS
```

---

### 3. 状态机（UML State）

#### 3.1 BookingDraft / BookingState

与 §4.7 相同。

```mermaid
stateDiagram-v2
  direction TB

  [*] --> Idle
  Idle --> PickFilmCinema: 购票意图

  state PickFilmCinema {
    direction LR
    [*] --> SelectMovie: 片→院
    [*] --> SelectCinema: 院→片
    SelectMovie --> SelectCinema: 缺 cinema
    SelectCinema --> SelectMovie: 缺 movie
  }

  PickFilmCinema --> SelectShow: movieId ∧ cinemaId
  SelectShow --> SelectSeat: showId
  SelectSeat --> ConfirmOrder: lockSeats
  ConfirmOrder --> PayMock: 去支付
  PayMock --> TicketIssued: pay 成功
  TicketIssued --> Idle: 结束

  SelectShow --> PickFilmCinema: 改片/院
  SelectSeat --> SelectShow: 改场次
  ConfirmOrder --> SelectSeat: 取消
  PayMock --> SelectSeat: 超时/取消
```

> `PickFilmCinema` 为文档分组，非 `BookingState` 枚举值；运行时 `state` 仍为 `SelectMovie` / `SelectCinema` 之一（`firstIncompleteStep`）。`SelectSeat` 完备需 lock 成功；支付 API 不在 Agent Tool 白名单。

#### 3.2 座位状态

```mermaid
stateDiagram-v2
  [*] --> available
  available --> locked: lockSeats
  locked --> available: unlock / TTL 到期
  locked --> sold: mockPay 成功
  available --> unavailable: 维护配置
  unavailable --> [*]
  sold --> [*]
```

#### 3.3 订单状态

```mermaid
stateDiagram-v2
  [*] --> pending_pay: createOrder
  pending_pay --> issued: pay
  pending_pay --> cancelled: user_cancel / TTL
  issued --> [*]
  cancelled --> [*]
```

#### 3.4 锁 seat_lock 状态

```mermaid
stateDiagram-v2
  [*] --> active: lockSeats
  active --> released: unlockSeats
  active --> expired: TTL Job
  active --> consumed: mockPay
  released --> [*]
  expired --> [*]
  consumed --> [*]
```

---

### 4. 组件图 / 部署图（UML）

#### 4.1 逻辑组件（Hexagonal）

```mermaid
flowchart TB
  subgraph AgentSvc["ticket-agent Python"]
    Turn[ProcessAgentTurn]
    LC[LangChain]
    Tools[LC Tools httpx]
  end
  subgraph Adapter["ticket-api Java adapter"]
    Web[Web Controllers]
    RedisA[Redis]
    DBA[PostgreSQL Repositories]
  end
  subgraph Application["ticket-api Java application"]
    CatalogUC[Catalog UseCases]
    InvUC[Inventory UseCases]
    OrdUC[Order UseCases]
    RecUC[Recommend UseCases]
    DraftUC[Draft Messages UseCases]
  end
  subgraph Domain["domain"]
    Booking[BookingDraft / State]
    InvDom[SeatLock Aggregate]
    OrdDom[Order Aggregate]
    Scorer[SeatScorer / HotScore]
  end
  Turn --> LC
  Turn --> Tools
  Tools -->|REST| Web
  LC -.-> ExtLLM[外部 LLM]
  Web --> CatalogUC
  Web --> InvUC
  Web --> OrdUC
  Web --> RecUC
  Web --> DraftUC
  CatalogUC --> DBA
  InvUC --> InvDom
  InvUC --> DBA
  InvUC --> RedisA
  OrdUC --> OrdDom
  OrdUC --> DBA
  DraftUC --> DBA
  RecUC --> Scorer
  Turn -.-> Booking
```

#### 4.2 部署视图

```mermaid
flowchart LR
  Browser[浏览器] --> GW[API Gateway]
  GW -->|/api/v1/agent/*| Agent[ticket-agent Python]
  GW -->|其余 /api/v1/*| App[ticket-api Java]
  Agent -->|httpx Tools| App
  App --> PG[(PostgreSQL)]
  App --> Redis[(Redis)]
  Agent -.-> LLM[LLM API]
```

---

### 5. 核心类图（UML Class）

```mermaid
classDiagram
  class BookingDraft {
    +String sessionId
    +String userId
    +Source source
    +BookingState state
    +String movieId
    +String cinemaId
    +String showId
    +int count
    +List~String~ seatIds
    +String lockId
    +String orderId
    +long version
    +boolean isStepComplete()
    +void applyPatch(patch)
    +void clearDependents(fromField)
  }

  class ProcessAgentTurn {
    +AgentTurnResponse execute(cmd)
  }
  class IntentClassifier {
    +Intent classify(text, draft)
  }
  class SlotExtractor {
    +SlotPatch extract(text, draft)
  }
  class Planner {
    +PlanAction plan(draft)
  }
  class ToolExecutor {
    +ToolResult run(name, args)
  }
  class CardComposer {
    +List~Card~ compose(plan, toolResults)
  }
  class LockSeatsUseCase {
    +LockResult lock(showId, seatIds, userId, ttl)
  }
  class CreateOrderUseCase {
    +Order create(lockId, userId)
  }
  class MockPayUseCase {
    +Order pay(orderId, userId, channel, payToken?)
  }
  class GetPayQrUseCase {
    +PayQrVO createPayQr(orderId, userId)
  }
  class GetPaySessionUseCase {
    +PaySessionVO loadSession(orderId, payToken)
  }
  class TicketQrSigner {
    +String ticketCode(orderId, payAt)
    +String qrPayload(order, payAt)
    +boolean verify(payload)
  }
  class SeatScorer {
    +List~SeatPlan~ topN(map, pref, n)
  }
  class HotScoreCalculator {
    +double score(stats, weight)
  }

  class SeatLock {
    +String lockId
    +String showId
    +String userId
    +List~String~ seatIds
    +Instant expireAt
    +LockStatus status
  }
  class OrderTicket {
    +String orderId
    +OrderStatus status
    +BigDecimal amount
    +String ticketCode
    +String qrPayload
    +String payChannel
  }

  ProcessAgentTurn --> IntentClassifier
  ProcessAgentTurn --> SlotExtractor
  ProcessAgentTurn --> Planner
  ProcessAgentTurn --> ToolExecutor
  ProcessAgentTurn --> CardComposer
  ProcessAgentTurn --> BookingDraft
  ToolExecutor --> LockSeatsUseCase
  ToolExecutor --> CreateOrderUseCase
  ToolExecutor --> SeatScorer
  LockSeatsUseCase --> SeatLock
  CreateOrderUseCase --> OrderTicket
  MockPayUseCase --> OrderTicket
  MockPayUseCase --> TicketQrSigner
  GetPayQrUseCase --> TicketQrSigner
  Note: "MockPay/GetPayQr 不注册进 ToolExecutor"
```

#### 5.1 Planner 决策类（补充）

```mermaid
classDiagram
  class PlanAction {
    <<enumeration>>
    ASK_SLOT
    SHOW_CARDS
    CALL_TOOL
    SKIP_TO
    DONE
    CANCEL_FLOW
  }
  class Planner {
    +PlanAction plan(BookingDraft draft)
    -Step firstIncompleteStep(draft)
    -boolean canAutoResolve(step)
  }
  Planner --> PlanAction
  Planner --> BookingDraft
```

---

### 6. 时序图（Sequence）

#### 6.1 普通流程：选座 → 锁座 → 下单 → 支付

```mermaid
sequenceDiagram
  actor U as 用户
  participant FE as 前端
  participant API as Inventory/Order API
  participant DB as PostgreSQL
  participant RD as Redis

  U->>FE: 确认选座
  FE->>API: POST /locks {showId, seatIds}
  API->>DB: BEGIN
  API->>DB: SELECT seat_status FOR UPDATE
  alt 座位全部 available
    API->>DB: INSERT seat_lock
    API->>DB: UPDATE seat_status locked
    API->>RD: SET lock:ttl:{id} EX 900
    API->>DB: COMMIT
    API-->>FE: 200 LockVO
    FE->>API: POST /orders {lockId}
    API->>DB: INSERT order_ticket pending_pay
    API-->>FE: 200 OrderVO
    FE->>API: GET /orders/{id}/pay-qrcode
    API-->>FE: PayQrVO(payUrl)
    FE->>FE: 展示支付 QR；轮询 GET /orders/{id}
    Note over FE: 用户手机扫码 H5 确认
    FE->>API: POST /orders/{id}/pay（或手机 X-Pay-Token）
    API->>DB: seats sold, lock consumed, qrPayload
    API->>RD: DEL lock:ttl
    API->>RD: DEL pay:token
    API-->>FE: 200 OrderVO ticketCode + qrPayload
    FE-->>U: 出票页（取票 QR）
  else 冲突
    API->>DB: ROLLBACK
    API-->>FE: code=-1 errorCode=SEAT_TAKEN
    FE-->>U: 刷新座位图
  end
```

#### 6.2 Agent：模糊意图 → 影片卡

```mermaid
sequenceDiagram
  actor U as 用户
  participant FE as Agent壳
  participant AT as POST /agent/turns
  participant NLP as NLP
  participant PL as Planner
  participant TL as Tools
  participant REC as RecommendUC
  participant DB as PostgreSQL

  U->>FE: 「周末想看个喜剧」
  FE->>AT: {sessionId, message}
  AT->>DB: 加载 session + draft + memory
  AT->>NLP: classify + extract
  NLP-->>AT: intent=buy_ticket, genre=喜剧, date≈weekend
  AT->>AT: merge Draft state=SelectMovie
  AT->>PL: plan(draft)
  PL-->>AT: SHOW_CARDS + CALL searchMovies
  AT->>TL: searchMovies(genre=喜剧)
  TL->>REC: GET /movies?genre=喜剧
  REC-->>TL: items[]
  TL-->>AT: result
  AT->>AT: CardComposer movie_list，写 listContext
  AT->>DB: 持久化 draft + agent_message
  AT-->>FE: replyText + draft + cards
  FE-->>U: 展示影片卡
```

#### 6.3 Agent：点卡推进 → 锁座 → 确认单

```mermaid
sequenceDiagram
  actor U as 用户
  participant FE as Agent壳
  participant AT as AgentTurn
  participant TL as Tools
  participant INV as LockSeatsUC
  participant ORD as CreateOrderUC

  U->>FE: 点座位方案「确认」
  FE->>AT: cardAction confirm + seatIds + clientDraftVersion
  AT->>AT: Draft 写入 seatIds
  AT->>TL: lockSeats(showId, seatIds)
  TL->>INV: lock(...)
  alt 成功
    INV-->>TL: lockId, expireAt
    TL->>ORD: createOrder(lockId)
    ORD-->>TL: Order pending_pay
    AT-->>FE: order_confirm 卡，draft ConfirmOrder
  else SEAT_TAKEN
    TL->>TL: recommendSeats 再算
    AT-->>FE: error 卡 + 备选 seat_plans
  end
```

#### 6.4 Agent 支付（禁止静默 · 二维码）

```mermaid
sequenceDiagram
  actor U as 用户
  participant PC as Agent壳/PC
  participant AT as AgentTurn
  participant API as Pay API
  participant M as 手机 H5
  participant DB as PostgreSQL

  Note over AT: Tool 白名单无 payMock
  AT-->>PC: cards: pay_mock（payUrl + 金额 + 倒计时）
  PC->>API: GET /orders/{id}/pay-qrcode
  API-->>PC: payUrl → 渲染支付 QR
  U->>M: 手机扫码
  M->>API: GET /orders/{id}/pay-session?t=…
  API-->>M: PaySessionVO
  U->>M: 点击「确认付款」
  M->>API: POST /orders/{id}/pay<br/>X-Pay-Token, channel=mobile_qr
  API->>DB: 出票 + qrPayload
  API-->>M: OrderVO issued
  PC->>API: GET /orders/{id}（轮询）
  API-->>PC: issued
  PC->>AT: cardAction payment_done {orderId}
  AT->>DB: draft→TicketIssued
  AT-->>PC: ticket_issued 卡（含 qrPayload）
  PC-->>U: 出票展示
```

#### 6.5 信息完备跳步（P1）

```mermaid
sequenceDiagram
  actor U as 用户
  participant FE as Agent壳
  participant AT as AgentTurn
  participant NLP as NLP
  participant PL as Planner
  participant TL as Tools

  U->>FE: 「两张明天下午最近《流浪地球 3》」
  FE->>AT: message
  AT->>NLP: 多槽抽取
  NLP-->>AT: count=2,date,timeWindow,filmTitle
  AT->>TL: searchMovies(title) → movieId 唯一
  AT->>PL: plan → SKIP_TO 深步
  Note over PL: 不追问选片
  AT->>TL: searchCinemas(lat,lng,movieId)
  AT->>TL: listShows(cinemaId,movieId,date)
  Note over AT: 按 draft.timeWindow 过滤 startTime
  alt 场次唯一或高置信
    AT->>TL: recommendSeats(showId,count=2,…)
    AT-->>FE: seat_plans 卡
  else 多场次
    AT-->>FE: show_list 卡
  end
```

#### 6.6 模式切换 hydrate

```mermaid
sequenceDiagram
  actor U as 用户
  participant FE as 前端
  participant DR as GET /booking-drafts/{sid}
  participant SH as GET /shows

  U->>FE: Agent 已选 movie+cinema，关闭抽屉进场次页
  FE->>DR: hydrate sessionId
  DR-->>FE: draft(state=SelectShow, movieId, cinemaId)
  FE->>SH: cinemaId + movieId + date
  SH-->>FE: shows[]
  FE-->>U: 场次列表（无需重选片/院）
```

#### 6.7 并发抢座（双模式）

```mermaid
sequenceDiagram
  participant A as 用户A 普通流程
  participant B as 用户B Agent
  participant API as POST /locks
  participant DB as PostgreSQL

  par A 锁座
    A->>API: seatIds=[sm1:6:7,sm1:6:8]
    API->>DB: FOR UPDATE
  and B 锁座
    B->>API: seatIds=[sm1:6:7,sm1:6:8]
    API->>DB: FOR UPDATE 等待
  end
  DB-->>API: A 事务先提交 locked
  API-->>A: 200 OK
  DB-->>API: B 读到已 locked
  API-->>B: code=-1 errorCode=SEAT_TAKEN
```

#### 6.8 锁座 TTL 释放

```mermaid
sequenceDiagram
  participant RD as Redis TTL
  participant JOB as ExpireJob
  participant DB as PostgreSQL
  participant SES as agent_session

  RD->>JOB: key expired lock:ttl:lk_01
  Note over JOB: 或定时扫描 expire_at < now
  JOB->>DB: BEGIN
  JOB->>DB: seat_status → available
  JOB->>DB: seat_lock → expired
  JOB->>DB: pending_pay order → cancelled
  JOB->>SES: 清 draft lock/order/seats，state=SelectSeat
  JOB->>DB: COMMIT
```

#### 6.9 Draft 乐观锁冲突

```mermaid
sequenceDiagram
  participant FE1 as 页签A
  participant FE2 as Agent壳
  participant API as PUT /booking-drafts/{sid}

  FE1->>API: version=3 patch movieId
  API-->>FE1: version=4
  FE2->>API: version=3 patch cinemaId
  API-->>FE2: code=-1 errorCode=DRAFT_CONFLICT + serverDraft(v4)
  FE2->>FE2: 拉最新后重合并
```

#### 6.10 推荐座位 → 落锁失败兜底

```mermaid
sequenceDiagram
  actor U as 用户
  participant FE as Agent壳
  participant AT as AgentTurn
  participant TL as Tools

  U->>FE: 确认 plan sp_1
  FE->>AT: confirm seatIds
  AT->>TL: lockSeats
  TL-->>AT: SEAT_TAKEN
  AT->>TL: recommendSeats 排除冲突座
  AT-->>FE: type=error + alternatives seat_plans
  FE-->>U: 提示已被抢 + 备选方案
```

---

### 7. 包结构与依赖（对照类图）

```text
com.miaoyu.ticket
├── domain/          # 无框架依赖
├── application/     # 用例；依赖 domain Port
├── adapter/
│   ├── web/         # REST → 用例
│   ├── persistence/
│   ├── redis/
│   ├── llm/
│   └── tool/        # AgentToolRegistry → 用例 Port
└── bootstrap/
```

依赖方向：**adapter → application → domain**。删除 Agent Core 后，Web Controllers 仍可调用 Catalog/Inventory/Order → 满足「先有店再有店员」。

---

### 8. 图与文档交叉索引

| 场景 | 时序节 | API | 表/事务 |
|------|--------|-----|---------|
| 手动闭环 | 6.1 | 本文 §7.5–7.6 | 本文 §6.6.1–6.3 |
| 模糊意图 | 6.2 | 本文 §7.8.1 | agent_session/message |
| 点卡锁座 | 6.3 | 本文 §7.8.1 + §7.5.2 | seat_lock |
| 禁止静默支付 | 6.4 | 本文 §7.6.2（二维码） | order_ticket + pay:token |
| 跳步 P1 | 6.5 | 本文 §7.8.1 | Draft 字段 |
| hydrate | 6.6 | 本文 §7.7.2 | agent_session |
| 抢座 | 6.7 | 本文 §7.5.2 | FOR UPDATE |
| TTL | 6.8 | — | 本文 §6.6.4 |
| Draft 冲突 | 6.9 | 本文 §7.7.3 | version CAS |

---

## 9. Agent 与中台的模块边界（双服务）

```text
┌─────────────────────────────────────────────────────────────┐
│ ticket-agent（Python + LangChain）独立服务                     │
│  Turn · NLP · RAG · Planner · CardComposer · LC Tools       │
│  Tools = httpx → ticket-api REST                             │
│  禁静默支付 · 禁直连 DB · 禁 pay Tool                          │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTP（用户 JWT 透传 + M2M）
                             ▼
┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
│ 影院管理 │ 影片管理 │ 场次管理 │ 座位管理 │ 订单管理 │ 支付管理 │
│ Cinema  │ Movie   │ Show    │ Seat    │ Order   │ Payment │
└──────────┴──────────┴──────────┴──────────┴─────┬────┴────▲─────┘
┌──────────┬──────────────┬───────────────────────┘         │
│ 用户管理 │ 热门推荐系统 │  Draft / agent_session / message │
│ User    │ Recommend    │  （PostgreSQL，仅 Java 写）        │
└──────────┴──────────────┘              支付：二维码 REST；Agent 禁止调 pay
```

| 边界 | 允许 | 禁止 |
|------|------|------|
| Agent 服务 → 中台 | HTTP 调 §7 REST；Draft PUT/GET | 直连 PostgreSQL / Redis 库存 |
| Agent → 支付 | 推 `pay_mock` 卡（含 payUrl） | 调用 pay API / 注册 pay Tool |
| 中台 Java | 全部 Use Case + Draft 持久化 | 嵌入 LangChain / 实现 Turn |
| 推荐系统 | 纯算法打分 | LLM 生成榜单 |
| Web Controller | 业务 REST + Draft + Messages | 第二套库存逻辑 |

**删除测试：** 停掉 ticket-agent 后，普通购票与中台 REST 仍可用 → Agent 是增强模块（B-G7）。

---

## 10. 错误处理

与 §7.0.1 一致：失败响应均由 **`@RestControllerAdvice`** 产出，`code=-1`；客户端优先读 `data.errorCode`，校验场景读 `data.fieldErrors`。

| Body.code | data.errorCode | 前端/Agent 行为 |
|-----------|----------------|-----------------|
| 200 | — | 正常渲染 |
| -1 | VALIDATION_ERROR | 提示；展示 fieldErrors |
| -1 | UNAUTHORIZED | 引导登录回跳 |
| -1 | FORBIDDEN | 提示 |
| -1 | NOT_FOUND | 提示 |
| -1 | SEAT_TAKEN | 刷新座位 + 备选卡 |
| -1 | LOCK_EXPIRED | 回选座 |
| -1 | DRAFT_CONFLICT | 拉最新 draft |
| -1 | SOLD_OUT | 换场卡 |
| -1 | SILENT_PAY_BLOCKED | 拒绝；引导显式支付 |
| -1 | INTERNAL_ERROR | 降级文案 |

---

## 11. 非功能与可观测

| 项 | 要求 |
|----|------|
| 中台查询 P95 | < 300ms |
| Agent 一轮 P95 | < 3s（LLM 超时 1.5s 则规则降级） |
| 子 Agent 只读超时 | 800–1200ms；写 1500ms（见 §5.10） |
| 锁座 | 事务/原子；压测两用户同座；幂等键 |
| 支付 | 仅 REST；二维码须显式确认；启动断言无 pay Tool（§5.12） |
| 日志 | sessionId / traceId / toolName / latency / idempotencyKey |
| 埋点 | intent_parsed, slot_updated, plan_skip, card_show, seat_lock, pay_mock_success, silent_pay_blocked |

---

## 12. 测试计划（后端）

| 类型 | 用例 |
|------|------|
| 单测 | SeatScorer 打分；hotScore；Planner 跳步/回退 |
| 单测 | SlotExtractor 多槽句；指代「第 2 个」 |
| 集成 | lockSeats 并发仅一成功；TTL 释放 |
| 集成 | ticket-agent Turn → httpx lockSeats → 中台；Draft PUT CAS |
| 集成 | Agent Tool 白名单无 pay；Python 启动自检 |
| 契约 | Card JSON schema 快照 |

---

## 13. 里程碑映射（2 周压缩）

> **总工期：2 周 / 10 个工作日。** 原 M1–M6 合并为两段冲刺；P1/增强项默认砍掉，见 §14.2「可砍清单」。

| 阶段 | 时间 | 后端交付 | 联调依赖（前端） |
|------|------|----------|------------------|
| **S1 中台可购票** | D1–D5（第 1 周） | 鉴权 + 目录 + 座位图（种子/读写）+ 锁座 + 订单 + **模拟支付**（QR 优先，`desktop_button` 兜底）+ 基础 Draft | C 端五步页；选座联调；支付页可先桌面直付 |
| **S2 Agent + 收口** | D6–D10（第 2 周） | Draft CAS 收口；Messages（可简）；**ticket-agent 规则主路径**（Tools 回调中台、禁 pay）；缺陷与演示 | Agent Drawer 真联调；H5/QR；E2E 两条主演示 |

| 原里程碑 | 2 周处置 |
|----------|----------|
| M1 中台+QR+建图 | **必做**（S1）；建图可「种子凸形图 + 只读/简 Editor」 |
| M2 画布联调 | **缩水**：有凸形种子即可演示；完整 Editor 能加则加 |
| M3 推荐 | **可砍** → 静态周榜种子或隐藏 Rail |
| M4 Draft+Messages | **必做精简**：Draft CAS 必做；Messages 可内存/单表简实现 |
| M5 Agent | **必做精简**：规则/槽位推进为主；LLM 可选 |
| M6 跳步/RAG/抢座 | **可砍**；有余力再加「同座冲突」一句演示 |

> 日计划与席位见 **§14**。前端对齐见 `02` §13。

---

## 14. 分工与时间安排（2 周）

> 全组 5 人；`负责人` 自填。R1–R3 后端、R4–R5 前端。**每日站会 ≤15min**；契约变更当日同步全员。

### 14.0 排期参数（自填）

| 参数 | 填写 |
|------|------|
| 启动日（D1） | ________年____月____日 |
| 演示日（D10） | ________年____月____日（建议启动后第 10 个工作日） |
| 站会 | 每日 ____:____ |
| 仓库 / 分支 | ________ |

**两周日历：**

| 日 | 主题 | 当日硬门槛（全员） |
|----|------|-------------------|
| **D1** | 开干：环境 + 契约冻结 | docker PG+Redis 起得来；错误码/`seatId`/包络对齐；分支策略确认 |
| **D2** | 目录 + 座位图可读 | 登录 + 影片/影院/场次；`GET seat-map` 有数据 |
| **D3** | 锁座/下单骨架 | `POST /locks` → `POST /orders` Postman 通 |
| **D4** | 支付 + C 端主路径 | pay（桌面或 QR）出票；前端能走到确认页 |
| **D5** | **S1 验收** | **不经 Agent** 完成：选座→锁→单→付→出票 |
| **D6** | Draft + Agent 骨架 | Draft GET/PUT；`/agent/turns` 能回 movie_list |
| **D7** | Agent 推进到锁座 | 点卡选片/院/场/座方案 → lock+order；禁 pay Tool |
| **D8** | 双入口联调 | 普通 + Agent 都能到支付卡/支付页；H5 或桌面付 |
| **D9** | E2E + 缺陷 | 演示脚本跑通；列已知问题 |
| **D10** | **演示彩排** | 定稿环境；P0 清零 |

### 14.1 五人席位总览（姓名自填）

| 席位 | 角色简称 | 负责人（自填） | 主技术 | 2 周主责（收紧） |
|------|----------|----------------|--------|------------------|
| **R1** | 中台·目录与运营 | ________ | Java | 骨架/包络、鉴权、影片/影院/场次、种子数据；Admin **仅查单+必要 CRUD** |
| **R2** | 中台·库存与支付 | ________ | Java | 座位图 API/种子、锁座、订单、支付、Draft CAS |
| **R3** | Agent 服务 | ________ | Python | 规则 Turn + Tools 调中台；卡片；**无 pay**；D1 起并行写骨架 |
| **R4** | 前端·C 端购票 | ________ | React | Client、Draft、购票五步、支付/H5、登录 |
| **R5** | 前端·Agent+管理端 | ________ | React | Drawer+CardRenderer；SeatMap 共享组件；管理端最小可用 |

### 14.2 必做 / 可砍（2 周纪律）

| 优先级 | 后端项 | 说明 |
|--------|--------|------|
| **P0 必做** | 鉴权、目录查询、seat-map、locks、orders、pay、Draft CAS、Agent 主路径（选片→…→order_confirm/pay_mock） | 演示两条链路依赖 |
| **P0 必做** | Tools 白名单无 pay；并发锁座至少一测 | 原则不可破 |
| **P1 有余力** | pay-qrcode + pay-session 完整 QR；weekly-hot；reco/seats；Admin 排片；Messages 持久化 | D8 前未完则降级 |
| **P2 默认砍** | RAG FAQ、跳步/指代、个人推荐算法、用户管理完善、压测、厅重叠校验细化 | 文档保留设计，演示不承诺 |

**支付降级策略：** D4 先通 `channel=desktop_button`；D7–D8 再补 QR+H5。二者至少一个必须上演示。

### 14.3 后端工作包（按日）

#### R1 — 目录与运营

| 包 | 日 | DoD |
|----|-----|-----|
| 工程+Flyway+包络 | D1 | 健康检查；统一 `code/data` |
| 鉴权 login/me/logout | D1–D2 | Token + 静默续期可后置，login 必通 |
| 影片/影院/场次+种子 | D2–D3 | 前端能列片选院选场 |
| Admin 查单 / 最小 CRUD | D4–D5 | 至少 `GET /admin/orders`；改排片有余力再做 |
| 联调与缺陷 | D8–D10 | 陪跑演示数据 |

#### R2 — 库存与支付

| 包 | 日 | DoD |
|----|-----|-----|
| 凸形座位图种子 + GET seat-map | D1–D2 | 稀疏图可渲染 |
| seat-maps 写接口（可简） | D2–D3 | 供 Editor 或仅种子 |
| locks + orders | D3–D4 | TTL、幂等、取消释放 |
| pay（桌面→QR） | D4–D5 | 出票 + ticketCode |
| Draft CAS | D5–D6 | `{version,patch}`；禁客户端写 lock/order |
| 缺陷/并发 | D8–D9 | 同座仅一人成功 |

#### R3 — Agent（与中台并行，勿等 W2）

| 包 | 日 | DoD |
|----|-----|-----|
| FastAPI 骨架 + 配置 | D1–D2 | `/health` |
| Tools httpx 对接已就绪 API | D3–D5 | 随中台接口日更 |
| Turn 规则路径 + cards/progress | D6–D7 | 点卡推进；启动断言无 pay |
| 联调 needLogin / 冲突提示 | D8–D9 | Drawer E2E |
| 演示话术收口 | D10 | 固定 Demo 话术清单 |

### 14.4 后端甘特（10 日）

```text
席位  D1     D2        D3         D4         D5✓S1    D6        D7         D8        D9      D10
R1   骨架   鉴权+目录  场次种子    Admin最小  验收协助  缺陷      缺陷       数据陪跑  E2E     演示
R2   ER图   seat-map  locks      订单+支付  Draft起  Draft完   联调       并发/缺陷 E2E     演示
R3   骨架   契约+Tool Tool接真API 跟支付契约 跟Draft   Turn主路径 锁座下单卡 双入口    缺陷    演示
                                         ↑ D5：普通购票闭环必须绿
```

### 14.5 依赖与风险（压缩版）

| 依赖 | 最晚 | 降级 |
|------|------|------|
| seatId / SeatVO | D2 | 前端写死一张图 |
| locks/orders | D4 | 前端 Mock 跳确认（仅自测） |
| pay | D5 | 仅 desktop_button |
| Draft | D6 | Agent 内存 draft，演示后补 CAS |
| `/agent/turns` | D7 | Drawer 用录制 JSON |

| 风险 | 缓解 |
|------|------|
| 两周做不完全量系分 | 严格按 §14.2 砍 P2；演示只承诺 P0 |
| Agent 等中台 | R3 D1 开工；D3 起每半日对接新 API |
| 范围回潮 | 新增需求默认进 P2，D10 前不插队 |

### 14.6 与前端对齐

| 阶段 | 后端 | 前端（02 §13） |
|------|------|----------------|
| S1 D1–D5 | 中台可购票 + 支付 | C 端五步 + SeatMap + 支付页 |
| S2 D6–D10 | Agent 主路径 + 收口 | Drawer 联调 + H5/QR + E2E 演示 |
