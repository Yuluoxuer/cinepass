# RBAC 权限模块说明与 Controller 用法

> 对应系分：`docs/01-后端系分-票务中台与Agent.md` §10（角色 `user` / `staff` / `admin`）  
> 代码目录：`src/main/java/com/cinepass/security/`（包名 `com.minihr.security`）  
>
> 本文档供开发者阅读（非运行时被代码调用）。Glob 确认仓库内尚无同名 RBAC 指南。  
> 用户指令：「写一个md告诉我每个代码文件的作用，并告诉我我怎么在controller中使用」

---

## 1. 请求怎么走

```text
HTTP 请求
  → JwtAuthFilter          有 Bearer Token 则验签，写入 ROLE_* + SecurityContext
  → SecurityConfig 路径规则  公开 /admin/** /seat-maps 等路径级兜底
  → Controller + 方法注解   @Staff / @Admin / @LoginRequired（精细控制）
```
> v4.7：已废除 `X-Internal-Api-Key`；对话消息由 ticket-agent 自有库存储。

未登录访问需登录接口 → **401**  
已登录但角色不够 → **403**

---

## 2. 每个文件干什么

| 文件 | 作用 |
|------|------|
| **`Roles.java`** | 角色字符串常量：`user` / `staff` / `admin`。签发 Token、判断角色时统一用这里，避免写错字符串。 |
| **`Admin.java`** | 方法/类注解。仅 **admin** 可进。等价 `@PreAuthorize("hasRole('admin')")`。用于账号管理 `/admin/users`。 |
| **`Staff.java`** | 方法/类注解。**staff 或 admin** 可进。等价 `@PreAuthorize("hasAnyRole('staff','admin')")`。用于运营 CRUD、座位图。 |
| **`LoginRequired.java`** | 方法/类注解。任意**已登录**用户可进。等价 `@PreAuthorize("isAuthenticated()")`。用于锁座、下单等 C 端写接口。 |
| **`JwtUtil.java`** | 签发 / 解析 Access Token。Claims：`sub`(userId)、`role`、`sid`、`jti`、`exp`；默认 TTL 3600 秒。登录成功后调用。 |
| **`JwtAuthFilter.java`** | 每个请求取 `Authorization: Bearer …`。合法则注入 Spring Authentication（`ROLE_xxx`）和 `SecurityContext`；无 Token **不拦**（交给路径规则）；非法 Token → 401。 |
| **`SecurityContext.java`** | 当前请求用户信息（ThreadLocal）。在 Service/Controller 里取 `userId`、`role` 等；也可在 SpEL 里用 `@securityContext`。 |
| **`SecurityConfig.java`** | Spring Security 总配置：CORS、无 Session、401/403 JSON、**路径级**放行/角色规则、注册两个 Filter、开启方法级 `@PreAuthorize`。 |
| **`AGENTS.md`** | 给 AI/协作者看的包内约定（可忽略）。 |
| **`JwtUtilTest.java`**（test） | 校验 Token 能带上 `role` / `sid` / `jti`。 |

### 相关配置（`application.yml`）

```yaml
jwt:
  secret: ${JWT_SECRET:...}
  access-token-expire-seconds: 3600

```

### 路径级规则（已在 `SecurityConfig` 配好，一般不用改）

| 路径 | 要求 |
|------|------|
| `POST /api/v1/auth/login`、Swagger、公开 GET（影片/影院/场次/推荐等） | 放行 |
| `GET …/pay-session`、`pay-qrcode`、`tickets/verify` | 放行 |
| `/api/v1/admin/**` | staff 或 admin |
| `/api/v1/seat-maps/**`、`POST /api/v1/halls` | staff 或 admin |
| 其余 | 需登录 |

路径是**兜底**；账号管理等仍要在方法上加 `@Admin`，否则 staff 也能打到该路径。

---

## 3. 在 Controller 里怎么用

### 3.1 运营接口（staff / admin）

```java
import com.minihr.common.Result;
import com.minihr.security.Staff;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/movies")
public class AdminMovieController {

    @Staff   // staff 或 admin
    @PostMapping
    public Result<?> create(@RequestBody MovieCreateDTO dto) {
        // ...
        return Result.success();
    }

    @Staff
    @PutMapping("/{movieId}")
    public Result<?> update(@PathVariable String movieId, @RequestBody MovieUpdateDTO dto) {
        return Result.success();
    }
}
```

### 3.2 仅管理员（账号 CRUD）

```java
import com.minihr.security.Admin;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    @Admin   // 只有 admin
    @GetMapping
    public Result<?> list() {
        return Result.success();
    }

    @Admin
    @PostMapping
    public Result<?> create(@RequestBody AdminUserCreateDTO dto) {
        return Result.success();
    }
}
```

### 3.3 C 端需登录（锁座 / 下单）

```java
import com.minihr.security.LoginRequired;
import com.minihr.security.SecurityContext;

@RestController
@RequestMapping("/api/v1/locks")
public class LockController {

    @LoginRequired
    @PostMapping
    public Result<?> lockSeats(@RequestBody LockSeatsDTO dto) {
        Long userId = SecurityContext.getCurrentUserId();
        String role = SecurityContext.getCurrentRole();
        // 用 userId 做业务…
        return Result.success();
    }
}
```

### 3.4 公开接口（浏览影片等）

**不要**加 `@LoginRequired` / `@Staff` / `@Admin`。  
只要路径已在 `SecurityConfig` 里 `permitAll`（如 `GET /api/v1/movies/**`），匿名可访问。

### 3.5 类上统一加（少写重复）

```java
@Staff
@RestController
@RequestMapping("/api/v1/admin/cinemas")
public class AdminCinemaController {
    // 本类所有方法默认 staff/admin
}
```

### 3.6 不用元注解时的等价写法

```java
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorize("hasAnyRole('staff','admin')")
@PreAuthorize("hasRole('admin')")
@PreAuthorize("isAuthenticated()")
```

### 3.7 本人资源（可选）

```java
@PreAuthorize("isAuthenticated() and @securityContext.currentUserId == #userId")
@GetMapping("/api/v1/users/{userId}/orders")
public Result<?> myOrders(@PathVariable Long userId) { ... }
```

注意：SpEL 里用的是 Bean 名 `securityContext`，对应 getter 风格属性 `currentUserId`。

---

## 4. 登录时怎么把角色写进 Token

在登录 Service 里：

```java
import com.minihr.security.JwtUtil;
import com.minihr.security.Roles;
import java.util.UUID;

String sid = UUID.randomUUID().toString().replace("-", "");
// role 取库表：Roles.USER / Roles.STAFF / Roles.ADMIN
String accessToken = jwtUtil.generateAccessToken(
        user.getId(),
        user.getNickname(),
        Roles.STAFF,
        sid
);
// Refresh 写入 Redis：auth:refresh:{sid}（按系分，客户端不持有 refresh）
```

前端请求头：

```http
Authorization: Bearer <accessToken>
```


---

## 5. 选型速查

| 接口类型 | 用什么 |
|----------|--------|
| `GET /movies`、`/cinemas`、`/shows` 等浏览 | 不加注解（路径已公开） |
| 锁座、下单、我的订单 | `@LoginRequired` |
| `/admin/movies`、排片、座位图、协助查单 | `@Staff` |
| `/admin/users` 改角色/禁用账号 | `@Admin` |

---

## 6. 常见坑

1. **JWT 里的 role 必须小写**：`admin` / `staff` / `user`（与 `Roles` 常量一致）。Filter 会加上 `ROLE_` 前缀给 Spring。
2. **只配路径不够**：`/admin/users` 路径允许 staff，方法上必须再加 `@Admin`。
3. **新增公开 GET**：改 `SecurityConfig` 的 `permitAll`，不必再改 Filter。
4. **取用户用 `SecurityContext`**，不要自己再解析一遍 JWT。
