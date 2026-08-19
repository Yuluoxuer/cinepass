# CinePass 影院购票系统

影院购票与选座系统，包含前端购票 UI、后端票务中台，以及基于 RAG 的智能问答 Agent。

## 模块

| 模块 | 目录 | 技术栈 | 说明 |
| --- | --- | --- | --- |
| 前端 | [`front/`](front) | React + Umi + Ant Design | 购票、选座、后台管理 |
| 后端 | [`backend/`](backend) | Java + Spring Boot (Maven) | 票务中台：影片、排片、座位、订单、价格 |
| Agent | [`agent/`](agent) | Python + FastAPI + LangChain/LangGraph + ChromaDB | RAG 智能问答 Agent |

## 目录结构

```
cinepass
├── front/     # 前端
├── backend/   # 后端
└── agent/     # 智能问答 Agent
```

## 说明

- 本项目由三个独立仓库合并而来，各子目录保留完整提交历史。
- 各模块的构建与运行方式见各自目录内的 README / 文档。
