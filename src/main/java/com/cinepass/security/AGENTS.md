<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# security

## Purpose
Spring Security + JWT 认证鉴权包。包含 JWT 过滤器、JWT 工具类、安全上下文及 Spring Security 配置。

## Key Files

| File | Description |
|------|-------------|
| `JwtAuthFilter.java` | JWT 认证过滤器；无 Token 放行，非法 Token → 401 |
| `JwtUtil.java` | Access Claims：`sub/role/sid/jti`，TTL 默认 3600s |
| `SecurityContext.java` | 安全上下文（`@Component("securityContext")`） |
| `SecurityConfig.java` | 路径级规则 + `@EnableGlobalMethodSecurity` |
| `Roles.java` / `Admin` / `Staff` / `LoginUser` | 角色常量 `user/staff/admin` 与方法级元注解 |

## For AI Agents

### Working In This Directory
- 登录签发：`jwtUtil.generateAccessToken(userId, username, Roles.STAFF, sid)`
- 新增公开路径：只改 `SecurityConfig.permitAll()`
- Controller 必须加 `@Staff` / `@Admin` / `@LoginRequired`

### Common Patterns
```java
@Staff
@PostMapping("/admin/movies")
public Result<?> create() { ... }

@Admin
@GetMapping("/admin/users")
public Result<?> users() { ... }

@LoginRequired
@PostMapping("/locks")
public Result<?> lock() { ... }
```

## Dependencies

### Internal
- `common.Result`/`common.ResultCode`

### External
- Spring Security, JWT (jjwt 0.11.5), Hutool

<!-- MANUAL: -->
