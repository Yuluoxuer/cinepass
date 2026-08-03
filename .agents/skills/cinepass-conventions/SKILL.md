---
name: cinepass-conventions
description: >-
  CinePass 票务中台（cinenpass_leijieming_backend）开发规范：包目录放置、SQL 只能写在
  MyBatis XML、角色权限注解 @Admin/@Staff/@LoginUser 用法、业务注释编写规范。
  在本仓库新增/修改业务代码、Controller、Mapper、权限配置、补注释前必须加载并遵循。
---

# CinePass 后端开发规范

Java 8 + Spring Boot 2.7 + MyBatis。包名 `com.cinepass`。编码前先读本 skill。

## 硬性约束（违反即错）

1. **SQL 只能写在 XML**：禁止在 Mapper 接口上使用 `@Select` / `@Insert` / `@Update` / `@Delete` 或任何注解 SQL。接口只声明方法；SQL 全部放 `src/main/resources/mapper/*.xml`。
2. **Java 8 only**：禁止 `var`、模块系统等 Java 9+ 语法。
3. **Controller 必须加权限注解**：`@Admin` / `@Staff` / `@LoginUser` 三选一（或类级 + 方法级组合）；禁止裸接口依赖「碰巧已登录」。
4. **不擅自改 `pom.xml` 依赖/版本**；需新依赖先征得用户同意。
5. **新增/修改业务代码须按「注释规范」补注释**；禁止无类注释的公共 API，禁止英文散文式废话注释。

---

## 文件夹放什么

根包：`src/main/java/com/cinepass/`

| 包/目录 | 放什么 | 命名 |
|---------|--------|------|
| `controller/` | REST 入口：参数校验、调 Service、返回 `Result`/`PageResult` | `XxxController` |
| `service/` | 业务接口；实现类放 `service/impl/` | `XxxService` / `XxxServiceImpl` |
| `mapper/` | MyBatis **接口 only**，无 SQL、无业务 | `XxxMapper` |
| `model/` | 表行 DO，不暴露给 Controller | 与表对应，如 `Movie` |
| `dto/` | 接口入参 | `XxxDTO` / `XxxCreateDTO` |
| `vo/` | 返回前端的展示对象 | `XxxVO` |
| `security/` | JWT、Filter、角色常量、权限元注解 | 勿塞业务 |
| `common/` | `Result`、`ResultCode`、`BusinessException`、全局异常等 | 勿塞业务 |
| `config/` | Spring `@Configuration` | `XxxConfig` |
| `constant/` | 缓存 Key 等常量 | `CacheKeys` |
| `util/` | 无状态工具 | `XxxUtil` / `PhoneMask` |
| `aop/` | 审计等切面 | `@AuditLog` 等 |

资源：

| 路径 | 放什么 |
|------|--------|
| `src/main/resources/mapper/` | **全部** MyBatis SQL（`XxxMapper.xml`，`namespace` = 接口全名） |
| `src/main/resources/db/migration/` | 库表迁移脚本 |
| `src/main/resources/application*.yml` | 配置；敏感项走环境变量 |
| `src/test/java/com/cinepass/` | 测试（`@ActiveProfiles("test")` + H2） |

分层：

```
Controller → Service → Mapper 接口 → mapper/*.xml → DB
```

- Controller **禁止**直接调 Mapper。
- Controller **禁止** try-catch 拼 JSON；抛 `BusinessException`，由 `GlobalExceptionHandler` 处理。
- 对外只返回 DTO/VO，不返回 `model`。

### 新模块检查清单

1. `model/` → 2. `mapper/` 接口 + `resources/mapper/*.xml` → 3. `dto/`/`vo/` → 4. `service/` → 5. `controller/`（加权限注解）→ 6. 测试

---

## SQL 规范（仅 XML）

**禁止：**

```java
// ❌ 不允许
@Select("SELECT * FROM movie WHERE movie_id = #{id}")
Movie findById(String id);
```

**正确：**

```java
// Mapper 接口：只声明
@Mapper
public interface MovieMapper {
    Movie findById(@Param("id") String id);
}
```

```xml
<!-- src/main/resources/mapper/MovieMapper.xml -->
<mapper namespace="com.cinepass.mapper.MovieMapper">
    <select id="findById" resultType="com.cinepass.model.Movie">
        SELECT * FROM movie WHERE movie_id = #{id}
    </select>
</mapper>
```

约定：

- XML 文件名与接口同名；`namespace` = 接口全限定名；`id` = 方法名。
- 多数据库差异用 `databaseId`（参考现有 `WantSeeMapper.xml`），不要在 Java 里拼方言。
- 简单与复杂 SQL **一律**进 XML，不因「太简单」改回注解。

---

## 权限怎么配

### 角色（`Roles`）

| 常量 | 值 | 含义 |
|------|-----|------|
| `Roles.USER` | `user` | 普通购票用户 |
| `Roles.STAFF` | `staff` | 运营/工作人员 |
| `Roles.ADMIN` | `admin` | 系统管理员 |

JWT claim 与 `hasRole` 均用小写；Filter 会加 `ROLE_` 前缀。业务里用 `Roles.XXX`，不要散落魔法字符串。

### 方法级注解（优先用这些，不要手写 `@PreAuthorize`）

定义在 `com.cinepass.security`：

| 注解 | 等价 SpEL | 谁能进 |
|------|-----------|--------|
| `@Admin` | `hasRole('admin')` | 仅管理员 |
| `@Staff` | `hasAnyRole('staff','admin')` | 员工或管理员 |
| `@LoginUser` | `isAuthenticated()` | 任意已登录用户 |

可标在**类**或**方法**上；方法级覆盖/收紧类级。

```java
import com.cinepass.security.Admin;
import com.cinepass.security.Staff;
import com.cinepass.security.LoginUser;

// 后台账号管理：整类仅 admin
@RestController
@RequestMapping("/api/v1/admin/users")
@Admin
public class AdminUserController { ... }

// 运营写接口：staff 或 admin
@Staff
@PostMapping("/api/v1/admin/movies")
public Result<?> createMovie(...) { ... }

// 登录用户即可（想看、个人中心等）
@LoginUser
@PostMapping("/{movieId}/want-see")
public Result<WantSeeVO> add(...) { ... }
```

选型：

- 改系统账号 / 角色 → `@Admin`
- 运营配置（影片、影厅、座位图等）→ `@Staff`
- 购票用户自己的数据 → `@LoginUser`
- **不要**再引入 `@LoginRequired`（已废弃，现用 `@LoginUser`）

### 路径级（`SecurityConfig`）

`SecurityConfig.securityFilterChain` 还有 URL 规则，与注解叠加（都要过）：

- 公开：`/api/v1/auth/login|register|password/change`、文档、部分 GET（影片/影院/场次等）
- `/api/v1/admin/**` → 至少 `staff` 或 `admin`（更严用 `@Admin`）
- 其余默认 `authenticated`

**新增公开路径**：只改 `SecurityConfig` 的 `permitAll()`（本仓库以 SecurityConfig 为准）。

### 当前用户

业务内取登录身份：`SecurityContext.getCurrentUserId()` / `getCurrentRole()` 等（由 `JwtAuthFilter` 注入，请求结束清理）。

---

## API 约定速查

- 路径前缀：业务 `/api/v1/...`；后台管理 `/api/v1/admin/...`
- 统一响应：`Result<T>`；分页可用 `com.cinepass.vo.PageResult<T>`
- 业务错误：`throw new BusinessException(ResultCode.xxx, "消息")`
- 密码：`PasswordEncoder`（BCrypt）；手机号出参用 `PhoneMask.mask`

---

## 注释规范

语言：**中文**。标识符、状态值、表名、系分章节号可保留英文（如 `lockId`、`pending_pay`、`§6.1`）。

### 目标与原则

- 注释写 **意图、约束、边界、不变量**，不复述代码字面含义。
- 优先让命名自解释；注释补命名说不清的业务规则。
- 禁止：`// 获取用户`、`i++ // 自增`、复制粘贴的过期说明、TODO 堆而不标责任人/条件。
- 修改行为时 **同步改注释**；发现注释与代码不符，优先改代码或删错注。

### 分层要求

| 层 | 必须有 | 写法 |
|----|--------|------|
| **类**（Controller / Service / Mapper / model / dto / vo / util / constant） | 类级 Javadoc | 一句话职责；可附路径列表或表名 `{@code order_ticket}` |
| **Service 接口** | 每个公开方法 Javadoc | 写清前置条件、幂等、副作用（如「首次加入计数 +1」） |
| **ServiceImpl** | 类级 `/** {@link XxxService} 实现。 */` | **不**重复接口方法 Javadoc；复杂分支用行内 `//` 说明为何 |
| **Controller** | 每个接口方法一句话 Javadoc | 与 AuthController 一致：`/** 登录，返回 Access Token 与角色信息 */` |
| **Mapper 接口** | 每个方法一句话 Javadoc | 写清语义（幂等 insert、FOR UPDATE、影响行数等），不写 SQL |
| **DTO / VO / model 字段** | 非显而易见字段须注释 | 枚举取值、JSON 结构、脱敏、可空、非表字段标记 |
| **常量类** | 类说明 + 非常量字段/方法说明 | 状态常量可用同行或上一行简注 |

### 格式约定

```java
/**
 * 用户订单接口（系分订单管理 · OrderAgent 对应 REST）。
 * <pre>
 * POST /api/v1/orders  创建
 * </pre>
 */
@RestController
public class OrderController {

    /** 由有效锁座创建订单；同 lockId 幂等 */
    @PostMapping
    @LoginUser
    public Result<OrderVO> create(...) { ... }
}
```

```java
/** 插入想看记录；已存在则忽略（幂等） */
int insertIgnore(@Param("userId") String userId, @Param("movieId") String movieId);
```

```java
/** [{seatId,zone,price,seatName?},...] JSON */
private String seatPriceSnapshot;

/**
 * MyBatis 部分更新标记：为 true 时写入 {@code cinema_id}（允许写 null 清空）。
 * 非表字段。
 */
private Boolean updateCinemaId;
```

```java
// ServiceImpl：只注释「为什么」，不注释「做了什么」
// 并发下同 lockId 可能双插；唯一索引冲突后按 lockId 回读已有单
try {
    orderTicketMapper.insert(order);
} catch (DuplicateKeyException ex) { ... }
```

### 行内注释（`//`）

仅用于：

1. 非直观算法 / 并发竞态 / 幂等兜底
2. 业务规则与系分不一致处的取舍说明
3. 排序、脱敏、权限收窄等易被误改的约束

一行说清即可；不要大段块注释复述流程（流程写在 Service 接口或类 Javadoc）。

### 禁止与例外

- **禁止**在 Impl 上用 `@Override` 方法再抄一遍接口 Javadoc（易腐烂）。
- **禁止**英文长段落；专有名词可中英混排。
- **不必**给 private 简单 getter/setter、Lombok 生成成员、显而易见的 `normalizePage` 写 Javadoc；若有特殊上限（如 size≤50）可一行注明。
- XML SQL：关键业务 SQL 可用 `<!-- 幂等：同 lockId -->` 短注释；禁止整文件装饰性分隔线。

### 新增代码检查清单（注释）

1. 新类有中文类 Javadoc  
2. Controller / Service 接口 / Mapper 公开方法有一句话说明  
3. DTO/VO/model 非常规字段有取值或格式说明  
4. Impl 里非直观分支有 `//` 原因注释  
5. 未引入与代码矛盾的旧注释  

---

## 测试

```bash
mvn test
mvn test -Dtest=ClassName
```

`@ActiveProfiles("test")`，H2，无需本机 MySQL/Redis（按现有 test 配置）。
