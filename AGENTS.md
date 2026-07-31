<!-- Generated: 2026-07-16 | Updated: 2026-07-30 -->

# minni-hr

## Purpose
Mini HR Management System（微型人力资源管理系统）。基于 Spring Boot 2.7.18 + MyBatis + Spring Security + Redis 构建的后端 REST API 服务，提供员工信息管理、权限认证、薪资处理等 HR 核心业务能力。

## Key Files

| File | Description |
|------|-------------|
| `pom.xml` | Maven 项目配置，定义所有依赖版本与构建插件（强制 Java 8，Maven 3.6+） |
| `README.md` | 项目文档（中文），包含技术栈、目录结构与快速启动说明 |
| `README.en.md` | 项目文档（英文版） |
| `schema.sql` | 完整数据库 DDL 脚本，包含所有业务表建表语句 |
| `CLAUDE.md` | AI Agent 专用文档，记录架构约定、模块完善程度、安全架构细节 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/` | 全部源代码与资源文件（见 `src/AGENTS.md`） |
| `doc/` | 项目附加文档、设计文件 |
| `logs/` | 运行时日志输出目录（minni-hr.log，不要提交此目录内容） |

## For AI Agents

### Working In This Directory
- **Java 版本限制**：本项目严格要求 Java 8，禁止使用 Java 9+ 特性（`var`、`Stream.of(array)`、模块系统等）
- **数据库密码不得硬编码**：DB_USERNAME / DB_PASSWORD / REDIS_PASSWORD 等敏感信息必须通过环境变量注入，禁止写入配置文件后提交
- **响应格式统一**：所有 Controller 接口返回值必须用 `Result<T>` 包装，分页接口使用 `PageResult<T>`
- **异常处理统一**：Service 层抛 `BusinessException`，禁止在 Controller 手动 try-catch 拼 JSON
- **新增公开路径需同时修改两处**：`JwtAuthFilter.PUBLIC_PATHS` + `SecurityConfig` 的 `permitAll()`
- **新增 Controller 必须加 `@PreAuthorize`**，参考已有 Controller 的粒度模式

### Testing Requirements
```bash
# 测试使用 test profile，自动启用 H2 内存数据库，无需 MySQL/Redis
mvn test

# 单个测试类
mvn test -Dtest=MinniHrApplicationTests

# 单个测试方法
mvn test -Dtest=SomeTest#methodName
```

### Common Patterns
- 分层架构：Controller → Service（接口）→ ServiceImpl → Mapper → XML
- 命名：Entity 类名 = 表名转大驼峰（`sys_user` → `SysUser`）；Service 方法：`getXxx` / `listXxx` / `saveXxx` / `updateXxx` / `deleteXxx`
- 所有 Entity 实现 `Serializable`，使用 Lombok `@Data`
- DTO 负责接口参数/返回隔离，VO 负责跨表组装展示
- 新增业务错误码在 `ResultCode` 枚举的 `4xxx` 区段追加

## Dependencies

### External
- Spring Boot 2.7.18 — 基础框架
- Spring Security 5.7.x — 认证鉴权、JWT 过滤器、BCrypt 加密、`@PreAuthorize`
- MyBatis 2.3.2 — ORM（PageHelper 1.4.7 分页）
- MySQL Connector 8.0.33 — 数据库驱动
- Druid 1.2.20 — 连接池
- Redis (Lettuce + Commons Pool2) — 缓存 / Token 管理 / 分布式锁
- RocketMQ 2.2.3 — 消息队列（**已全局禁用**，`rocketmq.enable: false`）
- Knife4j 4.3.0 — API 文档（开发环境访问 http://localhost:8080/doc.html）
- JWT (jjwt 0.11.5) — Token 生成/解析/校验，HS256
- Hutool 5.8.25 — 通用工具（AES 加密、Excel 导入等）
- Lombok 1.18.38 — POJO 简化
- H2 (test scope) — 测试用内存数据库（MySQL 兼容模式）

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
