<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# test/config

## Purpose
测试专用 Spring 配置类。提供基础设施 Mock Bean、测试专用 Bean 覆盖。

## For AI Agents

### Working In This Directory
- `@TestConfiguration` 类提供测试专用 Bean 覆盖
- 配合 `@Import(TestXxxConfig.class)` 在具体测试类中按需引入

<!-- MANUAL: -->
