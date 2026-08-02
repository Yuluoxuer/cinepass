<!-- Parent: ../../../AGENTS.md -->
<!-- Generated: 2026-07-16 | Updated: 2026-07-30 -->

# src/test/java/com/minihr

## Purpose
Spring Boot 测试代码包。测试使用 `test` profile，自动加载 H2 内存数据库（MySQL 兼容模式），无需本地 MySQL 或 Redis，可在 CI 环境中直接运行。

## Key Files

| File | Description |
|------|-------------|
| `MinniHrApplicationTests.java` | 应用启动集成测试，验证 Spring 上下文能否正常加载 |
| `MockInfrastructureConfig.java` | `@TestConfiguration`，提供 `RedisTemplate`、`RedisUtil`、`CacheManager` 等基础设施的 Mock Bean |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `config/` | 测试专用 Spring 配置类 |
| `controller/` | Controller 层集成/MockMvc 测试 |
| `service/` | Service 层单元/集成测试（含 `impl/`） |
| `util/` | 工具类单元测试（`SensitiveDataUtilTest` 等） |

## For AI Agents

### Working In This Directory
- 测试类命名：`XxxServiceTest`（单元）或 `XxxControllerTest`（集成），与被测类同名加 `Test` 后缀
- 集成测试使用 `@SpringBootTest`，纯单元测试使用 `@ExtendWith(MockitoExtension.class)`
- 测试默认激活 `test` profile，H2 内存数据库，Redis 已排除

### Testing Requirements
```bash
mvn test                                     # 运行全部测试
mvn test -Dtest=MinniHrApplicationTests      # 运行指定测试类
```

### Common Patterns
- 用 `@MockBean` 替换 Redis、RocketMQ 等外部服务
- 断言优先使用 AssertJ（`assertThat`）
- 测试数据通过 H2 内存库隔离，测试结束后自动清理

## Dependencies

### Internal
- `src/main/java/com/minihr/` — 被测生产代码
- `src/main/resources/application-test.yml` — 测试专用配置（H2 + 排除 Redis）

### External
- Spring Boot Test — JUnit 5 + Mockito + AssertJ
- H2 (test scope) — 内存数据库

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
