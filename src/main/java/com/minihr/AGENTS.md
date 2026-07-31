<!-- Parent: ../../../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# main/java/com/minihr

## Purpose
应用根包。包含 Spring Boot 应用入口（`MinniHrApplication.java`）和所有业务功能模块（安全、配置、AOP、工具类、业务逻辑层等）。

## Key Files

| File | Description |
|------|-------------|
| `MinniHrApplication.java` | Spring Boot 启动类（`@SpringBootApplication`，Tomcat 内嵌 8080 端口） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `aop/` | AOP 切面（审计日志 `@AuditLog`，见 `aop/AGENTS.md`） |
| `cache/` | 缓存相关实现（见 `cache/AGENTS.md`） |
| `common/` | 公共基础设施（`Result`/`ResultCode`/`BusinessException`，见 `common/AGENTS.md`） |
| `config/` | Spring 配置类（Jackson/MyBatis/WebMvc/Redis，见 `config/AGENTS.md`） |
| `constant/` | 常量定义（`CacheKeys`/`ScopeType`，见 `constant/AGENTS.md`） |
| `controller/` | REST API 控制器（见 `controller/AGENTS.md`） |
| `dto/` | 数据传输对象（见 `dto/AGENTS.md`） |
| `entity/` | JPA/MyBatis 实体类（见 `entity/AGENTS.md`） |
| `interceptor/` | MyBatis 拦截器（见 `interceptor/AGENTS.md`） |
| `mapper/` | MyBatis Mapper 接口（见 `mapper/AGENTS.md`） |
| `mq/` | RocketMQ 相关（**已全局禁用**，见 `mq/AGENTS.md`） |
| `security/` | Spring Security + JWT（见 `security/AGENTS.md`） |
| `serializer/` | Jackson 序列化器（见 `serializer/AGENTS.md`） |
| `service/` | 业务逻辑层接口 + 实现（见 `service/AGENTS.md`） |
| `util/` | 自定义工具类（见 `util/AGENTS.md`） |
| `vo/` | 视图对象（见 `vo/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 分层架构：Controller → Service → ServiceImpl → Mapper → XML
- 包命名遵循功能职责
- 启动类不要添加业务逻辑

### Common Patterns
- 所有 Entity/DTO/VO 使用 Lombok `@Data`
- Service 层接口 + 实现类模式
- Controller 统一返回 `Result<T>` 或 `PageResult<T>`

<!-- MANUAL: -->
