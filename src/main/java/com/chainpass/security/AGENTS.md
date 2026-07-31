<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# security

## Purpose
Spring Security + JWT 认证鉴权包。包含 JWT 过滤器、JWT 工具类、安全上下文及 Spring Security 配置。

## Key Files

| File | Description |
|------|-------------|
| `JwtAuthFilter.java` | JWT 认证过滤器（`OncePerRequestFilter`），提取/验证 Token |
| `JwtUtil.java` | JWT 工具类（Access Token 30分钟，Refresh Token 7天，HS256） |
| `SecurityContext.java` | 安全上下文（`@Component("securityContext")`，ThreadLocal + Bean） |
| `SecurityConfig.java` | Spring Security 配置（公开路径：`/api/auth/login`、Swagger） |

## For AI Agents

### Working In This Directory
- JWT 密钥通过 `@Value("${jwt.secret}")` 注入
- 新增公开路径需同时修改：`JwtAuthFilter.PUBLIC_PATHS` + `SecurityConfig.permitAll()`
- `SecurityContext` 静态方法供 Java 调用，Bean 供 SpEL（`@PreAuthorize`）

### Common Patterns
```java
@PreAuthorize("hasRole('ADMIN')")
public Result<List<User>> listUsers() { ... }

Long userId = SecurityContext.getCurrentUserId();
```

## Dependencies

### Internal
- `common.Result`/`common.ResultCode`

### External
- Spring Security, JWT (jjwt 0.11.5), Hutool

<!-- MANUAL: -->
