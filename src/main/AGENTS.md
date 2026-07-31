<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# main

## Purpose
生产环境代码根目录。包含所有 Java 源码（`java/`）和应用配置与资源（`resources/`）。

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `java/com/minihr/` | 应用根包，所有业务代码（见 `java/com/minihr/AGENTS.md`） |
| `resources/` | 配置文件、MyBatis XML、日志配置（见 `resources/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 所有业务 Java 代码都在 `java/com/minihr/` 下的对应子包中
- 资源文件（`.yml`、`.xml`、`.sql`）放在 `resources/` 下
- 不要在 `java/` 目录直接放资源文件，也不要在 `resources/` 放 Java 源码

<!-- MANUAL: -->
