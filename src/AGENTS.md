<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-16 | Updated: 2026-07-30 -->

# src

## Purpose
项目全部源代码与资源文件的顶级目录，遵循 Maven 标准目录布局（`main/` 存放生产代码，`test/` 存放测试代码）。

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `main/java/com/minihr/` | 生产 Java 源码（应用入口 + 各业务包，见 `main/java/com/minihr/AGENTS.md`） |
| `main/resources/` | 配置文件与 MyBatis XML 映射文件（见 `main/resources/AGENTS.md`） |
| `test/java/com/minihr/` | 测试代码（Spring Boot Test + H2，见 `test/java/com/minihr/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 不要在此目录直接放置 Java 文件或配置文件，代码按 Maven 规范放入对应子目录
- 生产代码与测试代码严格分离，生产依赖不得引用 test-scope 依赖

### Common Patterns
- Maven 标准布局：`src/main/java/`（Java 源码）、`src/main/resources/`（配置/XML）、`src/test/java/`（测试源码）

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
