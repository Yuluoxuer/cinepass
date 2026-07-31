<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# config

## Purpose
Spring 配置类包。包含 Jackson、MyBatis、WebMvc、Redis 等基础设施配置。

## Key Files

| File | Description |
|------|-------------|
| `JacksonConfig.java` | Jackson 配置（Java 8 时间类型 → `yyyy-MM-dd HH:mm:ss`） |
| `MyBatisConfig.java` | MyBatis 配置占位 |
| `WebMvcConfig.java` | WebMvc 配置（Swagger 静态资源 + 时间参数 Formatter） |
| `RedisConfig.java` | Redis 配置（`@ConditionalOnBean`） |
| `AutoFillInterceptor.java` | MyBatis 审计字段自动填充拦截器 |
| `DataInitializer.java` | 数据初始化器 |
| `FieldPermissionBootstrap.java` | 字段权限初始化器 |

## For AI Agents

### Working In This Directory
- 配置类用 `@Configuration`，Bean 用 `@Bean`
- `@ConditionalOnBean`/`@ConditionalOnProperty` 实现条件装配
- MyBatis 拦截器注册在 `MyBatisConfig`

<!-- MANUAL: -->
