<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# test

## Purpose
测试代码根目录。包含 JUnit 5 测试类、Mock 配置 Bean，以及 H2 内存数据库的 Schema 文件。

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `java/com/minihr/` | 测试类根包（见 `java/com/minihr/AGENTS.md`） |
| `resources/` | H2 Schema（`schema.sql`）、M04 模块专用 Schema（`schema-h2.sql`） |

## For AI Agents

### Working In This Directory
- 测试 profile：`@ActiveProfiles("test")`，H2 内存库 + Mock Redis/RocketMQ，无需外部依赖
- `MockInfrastructureConfig`：提供基础设施 Mock Bean

### Testing Requirements
```bash
mvn test                          # 运行全部测试
mvn test -Dtest=ClassName         # 运行单个测试类
mvn test -Dtest=Class#method      # 运行单个测试方法
```

<!-- MANUAL: -->
