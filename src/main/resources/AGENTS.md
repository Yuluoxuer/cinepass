<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# main/resources

## Purpose
Spring Boot 应用资源目录。包含多环境配置文件、MyBatis Mapper XML 映射、日志配置及数据库迁移脚本。

## Key Files

| File | Description |
|------|-------------|
| `application.yml` | 公共配置，设置默认 active profile 为 `dev` |
| `application-dev.yml` | 开发环境配置（MySQL 远程 + Redis localhost:6379），不在版本库中 |
| `application-test.yml` | 测试环境配置（H2 内存库，排除 Redis/RocketMQ），在版本库中 |
| `application-prod.yml` | 生产环境配置（MySQL 本地 + 环境变量注入），不在版本库中 |
| `logback-spring.xml` | Logback 日志配置（按 Spring profile 区分日志级别，生产输出到文件） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `mapper/` | MyBatis XML 映射文件（见 `mapper/AGENTS.md`） |
| `db/migration/` | Flyway/原始数据库迁移 SQL 脚本 |

## For AI Agents

### Working In This Directory
- 敏感配置（密码、密钥）通过环境变量注入，禁止明文提交；`application-dev.yml` 和 `application-prod.yml` 在 `.gitignore` 中
- 简单 CRUD SQL 用 MyBatis 注解直接写在 Mapper 接口方法上；复杂多表 JOIN / 动态 SQL 写在 `mapper/*.xml` 中
- 新增 Mapper XML：文件名与 Mapper 接口同名，`namespace` 设为完整接口类名

### Common Patterns
```bash
# 指定 profile 启动
mvn spring-boot:run -Dspring-boot.run.profiles=test
```

<!-- MANUAL: -->
