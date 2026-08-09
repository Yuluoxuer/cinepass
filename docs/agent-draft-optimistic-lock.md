# 草稿乐观锁（clientDraftVersion）实施计划

> 目标：解决「手动购票页面」与「Agent（agent/agent2/agent3）」各自修改 bookingdraft 时相互覆盖的冲突，用版本号做冲突检测与合并。

## 一、背景与问题

- 手动购票页面（`/booking/*`）直接 PUT 中台 `/booking-drafts/{sessionId}`；
- Agent 侧草稿来源不同：
  - 旧 graph `/booking/turns`：读写中台草稿；
  - agent2 `/agent/turns`：读写本地 `booking_drafts` 表（每轮前后与中台双向同步，已修复）；
  - agent3 `/agent3/turns`：统一读写中台草稿（方案 A）；
- 前端 `useBookingStore.draft.version` 和请求里的 `clientDraftVersion` 已存在，但**后端未使用**，无法判断"谁更新、谁该赢"；
- 现有合并用固定优先级（点卡 > 页面快照 > 中台 > 本地），无法区分"页面本地未同步的新改动"和"Agent 已锁座/下单的成果"。

## 二、目标

1. 用 `draft.version` 做乐观锁，检测冲突；
2. 无冲突时正常合并；有冲突时按「谁新谁赢」解决，且不破坏 Agent 已锁座/下单成果；
3. 修改决策字段时级联清理依赖字段（如改影片 → 清影院/场次/座位），保持草稿一致性；
4. 三套 Agent 端点行为一致。

## 三、现状盘点

| 位置 | 现状 |
|------|------|
| 前端 `types/index.ts:426` | 已有 `clientDraftVersion?: number` |
| 前端 `stores/booking.ts` | 本地 `draft.version`；`patchLocal` 乐观更新时 `merged.version = draft.version`；PUT 带 `version`；读 `serverDraft.version` |
| 中台 `BookingDraftController` | `POST /booking-drafts`、`GET/PUT /booking-drafts/{sessionId}`，draft 含 `version` |
| agent2 本地 `booking_drafts` 表 | `draft JSONB`，无独立 version 列（version 需存进 JSON） |
| `fapi/api/agent.py` / `booking.py` / `agent3/api.py` | 均未消费 `clientDraftVersion` |

## 四、设计

### 4.1 version 语义
- `draft.version`：草稿变更序号，**每次有效写入 +1**（页面 PUT、Agent 每轮写回、锁座/下单回填）；
- 前端在 `applyAgentDraft` 后以服务端返回的 `version` 为准；本地 `patchLocal` 乐观更新时先保留当前 `version`，待服务端确认后再用新 `version`；
- 旧数据无 `version` → 视作 `0`，首次写时初始化。

### 4.2 冲突检测（后端统一算法）
每轮对话/写操作前：

```
cv = 请求携带的 clientDraftVersion（无则 null）
sv = 服务端草稿 draft.version
server_draft = 服务端草稿字段
client_draft = 请求 clientDraft 字段（页面快照）

分支：
1. cv == null          → 兼容旧客户端：退化为「固定优先级合并」（现有逻辑）
2. cv == sv            → 无冲突：正常合并（决策字段页面优先，Agent 锁座/下单成果保留）
3. cv <  sv            → 服务端较新（Agent/其他端改过）：
                            以 server_draft 为准，忽略 client_draft；返回 server_draft，前端覆盖本地
4. cv >  sv            → 客户端较新（页面本地改动未同步）：
                            决策字段以 client_draft 为准，与 server_draft 合并；
                            但 lockId/orderId/expireAt 以 server_draft 为准（保护 Agent 成果）；
                            合并后 version = max(cv, sv) + 1
```

### 4.3 依赖字段级联清理
合并后必须按依赖关系清理，避免"改了影院还留着旧场次/座位"：

| 变更字段 | 级联清空 |
|---------|---------|
| `movieId` | `cinemaId, cinemaName, showId, seatIds, lockId, orderId, expireAt` |
| `cinemaId` | `showId, seatIds, lockId, orderId, expireAt` |
| `showId` | `seatIds, lockId, orderId, expireAt` |
| `count` | `seatIds` |
| `seatIds` | `lockId, orderId, expireAt` |

> 与前端 `stores/booking.ts` 的 `rollbackDependent`/`withDerivedState`、旧 graph `modify_node` 的级联清空保持一致。

### 4.4 锁座/下单成果保护
无论哪种分支，只要 `server_draft` 已有 `lockId/orderId/expireAt`，合并结果必须保留这些字段（防止页面旧快照把已锁座/下单状态冲掉）。

## 五、改动点

### 5.1 后端：新增共享合并函数
- 新文件 `agent3/draft_merge.py`（或 `fapi/api/_draft_merge.py`），提供：
  ```python
  async def resolve_draft_merge(
      server_draft: dict,
      client_draft: dict | None,
      client_version: int | None,
      *,  # 依赖字段表
  ) -> dict:  # 返回合并后的 draft + 新 version
  ```
- 内部实现 4.2 分支 + 4.3 级联清理 + 4.4 成果保护。

### 5.2 各端点接入
| 端点 | 改动 |
|------|------|
| `fapi/api/agent.py` `agent_turns` | 用 `resolve_draft_merge` 替换现有固定优先级合并；对话后写回本地表时 `version = 新version`，再同步中台 |
| `fapi/api/booking.py` `booking_turn` | 同上（合并进中台草稿）；图初始状态喂合并后的 draft |
| `agent3/api.py` `_run_agent3` | 同上（已是中台草稿） |
| 中台 `BookingDraftController` | 确认 PUT 时已正确 `version+1` 与冲突校验；若无则补 |

### 5.3 前端
- `stores/agent.ts` `sendMessage`/`clickCardAction`：已带 `clientDraft`，**补上 `clientDraftVersion: draft.version`**；
- `stores/booking.ts` `applyAgentDraft`：若返回 `draft.version` 比本地小（服务端回退），以服务端为准并 `syncAgentProgress`；正常则用服务端 version；
- `patchLocal` 乐观分支：保持 version 不变，服务端确认后再更新（现有逻辑已接近）。

## 六、兼容与迁移
- 旧客户端不带 `clientDraftVersion` → 走分支 1（固定优先级），行为不回归；
- 旧数据无 `version` → 视作 0，首次写时初始化并回写；
- 中台与 agent2 本地表的 `version` 语义统一（都是变更序号），但**两套存储各自维护**，靠每轮双向同步拉齐。

## 七、测试用例
1. 页面改影院（cv == sv）→ 合并生效，锁座保留；
2. Agent 已锁座后页面旧快照（cv < sv）→ 服务端优先，锁座不回退；
3. 页面本地改片未同步（cv > sv）→ 客户端新影片生效，且级联清空旧影院/场次/座位；
4. 点卡明确选择 → 无论 version 都优先；
5. 无 version 的旧请求 → 走固定优先级兼容逻辑；
6. 依赖字段级联：改 `movieId` 清 `cinemaId/showId/seatIds`，改 `count` 清 `seatIds`。

## 八、风险与回滚
- **风险**：前端 `patchLocal` 的 version 语义若与后端不一致（如服务端确认后才 +1 vs 本地乐观就 +1），会导致误判冲突 → 实施时先统一语义；
- **风险**：中台 PUT 的 version 冲突校验若严格失败，页面可能 409 → 前端需处理 409（刷新服务端草稿）；
- **回滚**：`resolve_draft_merge` 分支 1 保底兼容旧行为，改动集中在后端一个函数，回滚只需还原端点调用。

## 九、建议实施顺序
1. 统一前后端 `version` 语义（先对齐 `stores/booking.ts`）；
2. 后端新增 `resolve_draft_merge` 并接入三个端点；
3. 前端补发 `clientDraftVersion`；
4. 中台确认/补充 PUT 的 version 校验；
5. 按第七节测试用例联调。
