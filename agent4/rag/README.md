# agent4 RAG 知识库使用说明

agent4 的 RAG 模块（复制自 `agent/rag` 并适配），让购票助手能检索本地向量知识库（退票政策、改签规则、操作指南、影院位置/活动等）。

## 作用域（scope）

知识库分两种作用域，相互隔离：

| 作用域 | 内容举例 | 管理方 | Chroma collection | 磁盘目录 |
| --- | --- | --- | --- | --- |
| `system` | 购票流程、退改政策、平台规则 | admin（系统管理员） | `cinema_knowledge` | `knowledge/system/` |
| `cinema` | 某影院的位置、活动、服务 | 该影院的 staff（员工） | `cinema_knowledge_cinema_{cinemaId}` | `knowledge/cinema_{cinemaId}/` |

检索时：系统知识库**始终**被检索；若当前对话关联了影院（staff 的 JWT cinemaId，或 C 端用户会话中已选影院），该影院的独立知识库也会被一并检索；未关联影院（匿名 / 未选影院）时**回退检索所有影院知识库**（知识库面向 C 端全员开放）。来源标注 `[系统]` / `[影院:名称或ID]` 前缀（影院名称检索时按 cinemaId 调中台解析，带缓存，失败回退显示 ID），便于区分不同影院来源。

## 目录结构

```
agent4/rag/
├── config.py        # 共享配置（作用域 → collection/目录 映射、模型、切块参数、文件名校验）
├── ingest.py        # 知识库管理：灌入 / 删除 / 列表（CLI + 程序化 ingest_content）
├── retriever.py     # 检索：query → embedding → Chroma top-k → 拼装文本（可按作用域合并）
└── README.md        # 本说明

knowledge/                  # 源文档根目录（与 agent/ 共享）
├── refund_policy.md        # 旧顶层文档（CLI 灌入时兼容读取，等价系统作用域）
├── system/                 # 系统知识原文（admin 通过管理接口上传落盘）
└── cinema_{cinemaId}/      # 某影院知识原文（staff 通过管理接口上传落盘）
agent4/rag/chroma_db/   # 向量库持久化（运行时生成，勿提交 git）
agent4/rag/models/      # embedding 模型缓存（运行时生成，勿提交 git）
agent4/rag/chunks_preview.md  # 分块预览（运行时生成，勿提交 git）
```

> 说明：`knowledge/` 源文档与 `agent/` 共享；但向量库 `agent4/rag/chroma_db` 独立于 `agent/rag`，两套工具各自维护自己的向量库，需分别灌入。

## 加入知识库

### 方式一：管理端上传（推荐，带权限控制）

FastAPI 接口（`agent4/api/knowledge.py`，前缀 `/api/v1/agent4/knowledge`）：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/files` | 上传 Markdown（multipart，`file` 字段，≤1MB，UTF-8） |
| GET | `/files` | 列出当前作用域已有文档与分块数 |
| DELETE | `/files/{filename}` | 删除指定文档（向量块 + 磁盘原文） |

权限：**admin** 只能管理系统知识库；**staff** 只能管理自己影院的知识库（cinemaId 取自 JWT）；其它角色/匿名一律拒绝。错误以 `{code, message, data}` 信封返回。

admin 界面的「知识库」菜单页即封装了上述接口。

### 方式二：CLI 灌入（服务器运维用）

```bash
# 系统作用域（读取 knowledge/system/ 或兼容旧顶层 knowledge/）
python3 -m agent4.rag.ingest --file faq.md

# 删除 / 列表
python3 -m agent4.rag.ingest --delete faq.md
python3 -m agent4.rag.ingest --list
```

不带 `--file` 时默认灌入 `knowledge/refund_policy.md`。灌入会：
- 切块（markdown 标题策略，每块 ≤500 字，重叠 50 字）
- 计算向量并写入 Chroma（collection: `cinema_knowledge`）
- 导出分块预览到 `agent4/rag/chunks_preview.md`
- 跑检索自检（3 个测试 query 应全部 `[OK]`）

多文档可共存：多次 `--file` 灌不同文档，块会累积；重复灌同一文档则为幂等更新。

## 依赖与首次运行

依赖（已在 requirements.txt 声明）：

```bash
pip install fastembed==0.8.0 chromadb==1.1.1 langchain-text-splitters==1.1.2 python-multipart==0.0.9
```

首次灌入需联网下载 embedding 模型 `BAAI/bge-small-zh-v1.5`（约 180MB，缓存到 `agent4/rag/models`）。
若 HuggingFace 直连慢/失败，可用本地代理前缀：`proxy python3 -m agent4.rag.ingest`。

模型缓存完成后，代码会自动以离线模式加载（见 `config.ensure_offline_if_cached`），不会再联网抖动。

## agent 侧接入

`search_knowledge_base` 工具（`agent4/tools/AgentTools/rag_tools.py`）已接入**实时对话**：
- 挂在 chat 子 agent（`agent4/SubAgents/chat.py`）工具列表；
- 同时绑定到实时流程的 `chat_node`（`agent4/graph/nodes.py`），用户询问退票/改签/政策/操作指南/影院位置活动时自动检索作答。

工具检索范围 = 系统知识库 + 当前关联影院知识库（见上文「作用域」）。
