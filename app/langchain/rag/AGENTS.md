<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-02 | Updated: 2026-08-02 -->

# rag（检索增强生成）

## Purpose
FAQ 和政策文档的向量检索模块。MVP 阶段 `retrieve_faq` 返回空列表，避免幻觉库存字段。P1 计划接入远端 Chroma 向量库。**影片/场次/座位等事实性数据必须走 Tools，不经本模块。**

## Key Files

| File | Description |
|------|-------------|
| `__init__.py` | 包入口（空） |
| `retriever.py` | `retrieve_faq(query, top_k=3)`：async，返回 `list[dict]`；MVP 返回空列表 |
| `document_loader.py` | 语料加载工具：加载 PDF/文本到向量库（供运维使用，不在请求路径上） |

## For AI Agents

### Working In This Directory
- **P1 接入 Chroma**：在 `retriever.py` 中初始化 `chromadb.HttpClient`，替换空列表返回
- **禁止**用 RAG 查询库存、价格、场次等实时数据 — 这些走 Tools
- `document_loader.py` 仅供离线语料更新使用，不在请求处理路径上
- 向量库连接参数从 `app/config.py` 读取（待添加 `chroma_host`、`chroma_port` 字段）

### Testing Requirements
- 单测 `retrieve_faq`：mock Chroma 客户端，验证 `top_k` 参数透传
- 验证空 query 不抛出异常

## Dependencies

### External
- `chromadb` — 向量库客户端（P1）
- `pypdf` — PDF 语料加载
- `langchain-community` — 文档加载工具

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
