<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-31 | Updated: 2026-07-31 -->

# cinenpass_leijieming_backend（票务中台）

## Purpose
Spring Boot 2.7.18 票务中台，包名 `com.cinepass`，提供电影、影院、场次、座位、订单、支付等核心购票业务 REST API。基于 Spring Security + JWT 认证，MyBatis ORM，Redis 缓存，RocketMQ（当前禁用）。同时也是 AI Agent 唯一合法的业务操作入口（Agent 通过 HTTP Tools 调用本服务，不直接操作数据库）。

> **注意**：测试代码包名为 `com.cinepass`（原模板遗留），主代码统一使用 `com.cinepass`。

## Key Files

| File | Description |
|------|-------------|
| `pom.xml` | Maven 配置（Java 8, Spring Boot 2.7.18, MyBatis, Redis, RocketMQ, Knife4j, JWT） |
| `schema.sql` | 完整数据库 DDL（电影/影院/场次/座位/订单/用户等业务表） |
| `CLAUDE.md` | AI 专用架构约定：安全架构、MyBatis 拦截器、AOP、测试模式完整说明 |
| `.agents/skills/cinepass-conventions/SKILL.md` | **跨 Agent 开发规范**（包放置 / SQL 仅 XML / 权限注解 / 注释）；编码前必读 |
| `README.md` | 中文项目文档与快速启动 |
| `README.en.md` | 英文项目文档 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/main/java/com/cinepass/` | 所有业务源代码（见 `src/main/java/com/cinepass/AGENTS.md`） |
| `src/main/resources/` | 配置（application*.yml）、MyBatis Mapper XML、Flyway 迁移脚本（见 `src/main/resources/AGENTS.md`） |
| `src/test/` | JUnit 5 单元与集成测试（包名 `com.cinepass`） |
| `doc/` | 附加设计文档 |
| `docs/` | 接口文档、部署说明 |
| `logs/` | 运行时日志（不提交） |

## For AI Agents

### Required skill（所有 Agent）
编码 / 改 Controller / Mapper / 权限 / 注释前，**先读并遵循**：

`.agents/skills/cinepass-conventions/SKILL.md`

（Cursor / Claude Code 另有 symlink：`.cursor/skills/`、`.claude/skills/` → 同一正文，勿复制多份。）

### Working In This Directory
- **Java 版本**：严格 Java 8，禁止 `var`、`instanceof` 模式匹配、模块系统等 Java 9+ 语法
- **包名**：新类放在 `com.cinepass.*` 对应子包
- **敏感信息**：DB_PASSWORD / REDIS_PASSWORD / JWT_SECRET 必须从环境变量读取，禁止硬编码提交
- **响应格式**：所有 Controller 返回 `Result<T>`，分页接口返回 `PageResult<T>`
- **异常处理**：Service 层抛 `BusinessException`，Controller 不 try-catch 拼 JSON
- **新增 Controller**：必须加 `@PreAuthorize` 注解 + Knife4j（`@Api`, `@ApiOperation`）
- **新增公开路径**：需同时修改 `JwtAuthFilter.PUBLIC_PATHS` + `SecurityConfig.permitAll()`
- **RocketMQ 已全局禁用**（`rocketmq.enable: false`），异步操作用 `@Async` + Redis 替代

### Testing Requirements
```bash
# 自动使用 H2 内存库，无需 MySQL/Redis
mvn test

# 单个测试类
mvn test -Dtest=MinniHrApplicationTests
```

### Common Patterns
- 分层：Controller → Service（接口）→ ServiceImpl → Mapper → XML
- 命名：Entity = 表名转大驼峰；Service 方法：`getXxx`/`listXxx`/`saveXxx`/`updateXxx`/`deleteXxx`
- Mapper XML 在 `src/main/resources/mapper/`；DB 迁移在 `db/migration/`（Flyway）
- API 文档（开发期）：`http://localhost:8080/doc.html`

## Dependencies

### External
- `spring-boot-starter-web` 2.7.18 — REST API 框架
- `mybatis-spring-boot-starter` 2.3.2 — ORM（PageHelper 1.4.7）
- `spring-boot-starter-data-redis` — 缓存 / Token 管理 / 分布式锁
- `jjwt` 0.11.5 — JWT 认证（HS256，Access 30min，Refresh 7d）
- `knife4j-openapi2-spring-boot-starter` 4.3.0 — API 文档
- `rocketmq-spring-boot-starter` 2.2.3 — MQ（已禁用）
- `hutool` 5.8.25 — 通用工具（AES 加密、Excel 等）
- `lombok` 1.18.38 — POJO 简化
- `h2`（test scope）— 测试用内存数据库

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
