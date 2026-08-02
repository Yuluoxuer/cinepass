# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Mini HR 管理系统（minni-hr）后端，基于 Spring Boot 2.7.18 + MyBatis 2.3.2 + Spring Security + Redis + RocketMQ 构建的 REST API 服务。

**硬性约束：Java 8**，禁止使用 Java 9+ 特性（`var`、模块系统等）。

## 常用命令

```bash
# 编译并启动（开发环境，默认 dev profile）
mvn clean spring-boot:run

# 打包（跳过测试）
mvn clean package -DskipTests

# 运行全部测试（自动使用 test profile + H2 内存数据库）
mvn test

# 运行指定测试类
mvn test -Dtest=MinihrApplicationTests

# 运行指定测试类中的单个方法
mvn test -Dtest=EmployeeServiceTest#saveEmployeeShouldGenerateEmployeeNoAndContract

# 指定 profile 启动
mvn spring-boot:run -Dspring-boot.run.profiles=test
java -jar target/minni-hr-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod
```

## 架构概览

### 分层结构

```
Controller  →  Service（接口）  →  ServiceImpl  →  Mapper（接口）  →  XML（resources/mapper/）
     ↓              ↓
   DTO/VO         Entity
```

- **Controller**：接收请求，参数校验（`@Valid`），调用 Service，返回 `Result<T>` 或 `PageResult<T>`
- **Service**：接口 + 实现类模式（`service/` → 接口，`service/impl/` → `@Service` 实现），业务逻辑 + 事务管理
- **Mapper**：MyBatis 接口，简单 CRUD 用注解，复杂查询写在 `resources/mapper/*.xml` 中
- **Entity**：数据库表映射，Lombok `@Data`，实现 `Serializable`
- **DTO**：接口入参/出参载体，与前端交互，可加 `@NotBlank`/`@NotNull` 校验注解
- **VO**：跨表组装的视图对象，返回给前端

### API 模块划分（20 个 Controller）

| 模块 | 路径前缀 | 说明 | @PreAuthorize |
|---|---|---|---|
| 认证 | `/api/auth` | 登录、刷新 Token、登出、修改密码、当前用户信息 | ❌ 无需 |
| 账号管理 | `/api/users` | 账号 CRUD、状态切换、密码重置 | ✅ all ADMIN |
| 员工档案 | `/api/employees` | 员工 CRUD、导入导出、薪资档案、字段权限 | ✅ 完成 |
| 合同管理 | `/api/employees/{id}/contracts` | 合同历史、续签、变更、删除 | ✅ 完成 |
| 部门管理 | `/api/departments` | 部门 CRUD、树形结构、统计、合并 | ✅ 完成 |
| 职位管理 | `/api/positions` | 职位 CRUD | ✅ 完成 |
| 考勤管理 | `/api/attendance` | 考勤组 CRUD | ❌ |
| 菜单管理 | `/api/menus` | 当前用户菜单树 | ❌ |
| 权限管理 | `/api/permissions` | 权限列表 | ✅ ADMIN |
| 角色管理 | `/api/roles` | 角色 CRUD | ✅ all ADMIN |
| 个人中心 | `/api/profile` | BFF 聚合接口：考勤日历、工资条、安全日志等 | ❌ |
| M04 入职 | `/api/onboarding` | 入职申请全流程（草稿→提交→审批→入职→放弃） | ❌ |
| M04 转正 | `/api/probation-reviews` | 转正评估（待发起→创建→提交→终审） | ❌ |
| M04 调岗 | `/api/transfers` | 调岗申请（创建→撤回→确认调岗） | ❌ |
| M04 离职 | `/api/resignations` | 离职申请（创建→撤回→确认离职） | ❌ |
| 薪资批次 | `/api/v1/salary/payroll-batches` | 批次 CRUD、计算、预览、调整、提交、确认发放 | ❌ |
| 工资条 | `/api/v1/salary/pay-slips` | 工资条列表、核验、详情、趋势 | ❌ |
| 员工薪资 | `/api/v1/salary/employee-salaries` | 薪资档案 CRUD、调整记录 | ❌ |
| 薪资账套 | `/api/v1/salary/templates` | 账套模板 CRUD | ❌ |
| 审计日志 | `/api/audit` | 操作日志分页查询 | ✅ ADMIN |

**@PreAuthorize 覆盖**：8/20 个 Controller（Department、Position、Employee、Contract、Role、User、Permission、Audit），
未覆盖的模块认证只依赖 JwtAuthFilter 的"已登录即放行"。

---

## 模块完善程度总览

### 员工档案模块（Employee）— 基本完整

**已实现：** CRUD、工号自动生成（行锁+唯一索引兜底）、身份证 AES-GCM 加密、按角色分级脱敏（HR/ADMIN 明文，MANAGER 脱敏，本人查看自己脱敏）、
变更日志 AOP 自动记录（`EmployeeChangeLogAspect` + `@EmployeeChangeTrack`）、高级分页搜索（多条件 JOIN）、
Excel 批量导入（逐行校验+错误明细）、异步导出（`@Async` + Redis 状态跟踪 + Hutool 流式写出）、
软删除+引用校验、唯一性校验、合同关联查询、薪资档案关联查询。

**剩余缺口（5 个，主要是跨模块对接）：**

| 位置 | 描述 |
|---|---|
| `EmployeeSalaryServiceImpl` | bankAccount 未做 AES 加密，明文落库（仅 API 层调用 maskBankAccount 脱敏） |
| `EmployeeSalaryController:161 — toVO()` | employeeName / templateName 为空/null（跨服务对接未完成） |
| `PayrollBatchController:266/280` | employeeName / employeeNo 为空（跨服务对接未完成） |
| `PaySlipController:83` | 工资条核验不验证密码/短信（对接认证服务待做） |
| `PaySlipController:195` | 工资条详情 VO 中员工姓名/工号/部门为空 |

### 合同管理模块（Contract）— ✅ 已完成

Contract 现已拥有完整的四层架构：`ContractController` + `ContractService` + `ContractServiceImpl` + `ContractMapper` + `ContractMapper.xml`。
支持合同历史列表、查询现行合同、续签、修改变更、删除，Controller 全部方法已加 `@PreAuthorize`。

### 组织架构模块（Department + Position）— ✅ 已完成

`DepartmentController` 的 `@PreAuthorize` 已全部添加，原 TODO 已解决。

### M04 入转调离（Onboarding / ProbationReview / Transfer / Resignation）— 状态机骨架

四模块的状态流转（草稿→提交→审批→确认）骨架已完整实现，通过硬编码简化了审批逻辑。
所有 M05（审批流服务）对接点为 TODO，当前通过直接触发状态变更来绕过。

---

## 安全认证架构

### JWT 认证流程

```
请求 → JwtAuthFilter（OncePerRequestFilter）
         ├── 公开路径？→ 直接放行
         └── 需要认证 → 提取 Authorization: Bearer <token>
              ├── Token 无效/缺失 → 返回 401 JSON
              └── Token 有效 → 解析出 userId/username/roles
                   ├── 设置自定义 SecurityContext（ThreadLocal）
                   │    ⚠ permissions 当前未注入（硬编码 null）
                   │    ⚠ employeeId/departmentId 当前未注入
                   ├── SecurityContext 注册为 Spring Bean "securityContext"
                   │    → 供 SpEL 表达式：@securityContext.currentEmployeeId
                   └── 设置 Spring Security Authentication（ROLE_ 前缀）
                        → filterChain 继续
                        → finally: 清理两个上下文
```

- **JwtAuthFilter**：`OncePerRequestFilter`，在 `UsernamePasswordAuthenticationFilter` 之前执行
- **JwtUtil**：jjwt 0.11.5，HS256 签名，Access Token 30 分钟，Refresh Token 7 天
- **SecurityContext**（`com.cinepass.security.SecurityContext`）：`@Component("securityContext")`，ThreadLocal + Bean 双模式。
  静态方法供 Java 代码调用；Bean 引用供 SpEL 表达式（`@PreAuthorize("... @securityContext.currentEmployeeId == #id")`）。
  提供 `getCurrentUserId()`、`getCurrentUsername()`、`getCurrentEmployeeId()`、`getCurrentDepartmentId()`、`getCurrentRoles()`、`hasPermission(permission)`
- **角色映射**：JWT 中的角色 `"admin"` → Spring Security 的 `ROLE_admin` → `@PreAuthorize("hasRole('admin')")` 生效
- **JWT 密钥**：通过配置属性 `jwt.secret` 注入

### SecurityConfig

仅保留一个 `com.cinepass.security.SecurityConfig`（组件式，Spring Security 5.7+ 推荐写法）。
旧版 `com.cinepass.config.SecurityConfig` 已删除。

### Spring Security 公开路径

`/api/auth/login`、`/api/auth/refresh`、Swagger/Knife4j 静态资源。

**新增公开路径需同时修改两处**：`JwtAuthFilter.PUBLIC_PATHS` + `SecurityConfig` 的 `permitAll()`。

### `@PreAuthorize` 权限控制 — 8 个 Controller 已覆盖

权限粒度：
- `hasRole('ADMIN')` — 仅管理员（审计日志、用户管理、角色管理、权限列表）
- `hasAnyRole('ADMIN','HR')` — HR 及以上（新增/修改/删除员工、部门、职位、合同）
- `hasAnyRole('ADMIN','HR','MANAGER')` — 管理者及以上（查看员工列表、详情、部门树）
- `hasAnyRole('ADMIN','HR','MANAGER') or @securityContext.currentEmployeeId == #id` — 本人也能查看（员工详情、合同、薪资档案）
- `isAuthenticated()` — 任何已登录用户均可访问（部门树只读、字段权限配置）

### 数据权限（行级过滤）— ✅ 已实现

`DataPermissionInterceptor`（`com.cinepass.config.DataPermissionInterceptor`）在 MyBatis Executor.query 层面动态注入 WHERE 条件：

| 角色 | 过滤规则 |
|---|---|
| ADMIN / HR | 全量，不追加 |
| MANAGER | `AND e.department_id IN (本部门+全部子部门ID)` |
| 普通员工 | `AND e.id = currentEmployeeId` |

- 仅拦截 employee 表相关查询（通过 MappedStatement ID 匹配）
- 白名单表（sys_*、department、position 等）直接放行
- 部门子部门 ID 列表从 Redis 缓存 `dept:tree` 中获取
- 旧版 `DataScopeInterceptor`（`com.cinepass.interceptor.DataScopeInterceptor`）已废弃，pass-through 仅保留注册以兼容

### 字段脱敏 — 应用层按角色分级实现

当前脱敏在 Service 层硬编码（非 Jackson 序列化层），EmployeeServiceImpl 中：
- `hasAdminOrHrRole()` → 明文
- `hasManagerRole()` → `maskPhone()` 脱敏
- 本人查看自己 → 按角色处理（合同/薪资中 `@securityContext.currentEmployeeId == #id` 匹配即明文）

`FieldPermissionSerializer` 骨架存在但未装配（`init()` 未调用，Jackson 无 `BeanSerializerModifier` 注册），
当前由 `SensitiveDataUtil` 直接提供脱敏能力（`maskPhone`、`maskIdCard`、`maskBankAccount`、`maskEmail`）。

### 菜单权限 RBAC

**数据模型完整**，`SysUser→SysUserRole→SysRole→SysRoleMenu→SysMenu` 和 `SysUser→SysUserRole→SysRole→SysRolePermission→SysPermission` 两条链路均建表。
角色 CRUD 含权限批量替换+缓存清理、菜单树按角色递归组装（过滤 button 类型）、登录时加载权限并缓存 `user:perms:{userId}`。

---

## MyBatis 拦截器

| 拦截器 | 拦截点 | 状态 |
|---|---|---|
| `AutoFillInterceptor` | `Executor.update` | ✅ 自动填充 `@AutoFill` 字段 |
| `DataPermissionInterceptor` | `Executor.query` | ✅ 行级数据权限 SQL 改写（`com.cinepass.config`） |
| `DataScopeInterceptor` | `StatementHandler.prepare` | 🔧 已废弃，pass-through（功能已迁移到 DataPermissionInterceptor） |

由 `MyBatisConfig` 中 `@PostConstruct` 统一注册。

---

## AOP 切面

| 切面 | 注解 | 用途 |
|---|---|---|
| `AuditLogAspect` | `@AuditLog` | Controller 操作审计 → `sys_operation_log` 表（targetId 的 SpEL 解析为 TODO） |
| `EmployeeChangeLogAspect` | `@EmployeeChangeTrack` | 员工变更前后对比 → `employee_change_log` 表（自动查询旧/新实体，逐字段对比） |

---

## 核心公共模块（`common/`）

| 类 | 职责 |
|---|---|
| `Result<T>` | 统一响应体，`success()`/`fail()` 工厂方法 |
| `ResultCode` | 状态码枚举。HTTP 层 200/400/401/403/404/500；业务层 4xxx 区段 |
| `PageResult<T>` | 分页响应体，默认 pageNum=1/pageSize=20，`of()`/`empty()` |
| `BusinessException` | 业务异常，Service 层抛此异常 |
| `GlobalExceptionHandler` | `@RestControllerAdvice`，覆盖 12 种异常类型 |

---

## 多环境配置

| 文件 | 环境 | 数据库 | Redis | 说明 |
|---|---|---|---|---|
| `application.yml` | 公共 | — | — | 基础配置，默认 active=dev |
| `application-dev.yml` | 开发 | MySQL 远程 | localhost:6379 | 不在仓库中（.gitignore） |
| `application-test.yml` | 测试 | H2 内存库 | 已排除 | 在仓库中，无需外部依赖 |
| `application-prod.yml` | 生产 | MySQL 本地 | 环境变量注入 | 不在仓库中（.gitignore） |

---

## 已封装的工具类

- **`RedisUtil`**（`@Component`）：String/Hash/List/Set/ZSet 操作 + 分布式锁（`setIfAbsent` + `releaseLock` Lua 原子释放）
- **`RocketMQUtil`**（`@Component`）：RocketMQ 同步/异步/单向/顺序发送、延迟消息。**注意：RocketMQ 已全局禁用**（`rocketmq.enable: false` + 启动类排除自动配置）
- **`IdCardEncryptUtil`**：AES-GCM 加密/解密身份证号
- **`SensitiveDataUtil`**：手机号/身份证/邮箱/银行卡脱敏

## 配置类

- **`JacksonConfig`**：Java 8 时间类型序列化为 `yyyy-MM-dd HH:mm:ss`
- **`RedisConfig`**：Key=String，Value=`GenericJackson2JsonRedisSerializer`，`@ConditionalOnBean` 无 Redis 环境自动跳过
- **`WebMvcConfig`**：手动注册 Swagger/Knife4j 静态资源映射 + Java 8 时间类型 Formatter
  （`DateTimeFormatterRegistrar`，使 GET 请求 `LocalDate`/`LocalDateTime` 参数可正常绑定）
- **`MyBatisConfig`**：`@PostConstruct` 注册 `DataPermissionInterceptor` + `DataScopeInterceptor`

## 缓存策略

| 缓存 Key | 内容 | TTL | 说明 |
|---|---|---|---|
| `dept:tree` | 全量部门树 | 不设 TTL | 部门变更时主动延迟双删 |
| `user:perms:{userId}` | 用户权限码列表 | 30 min | 登录时加载，角色变更时 EVICT |
| `user:menu:{userId}` | 用户菜单树 | 30 min | 登录时加载 |
| `export:task:{taskId}` | 导出任务状态 | 1 h | `processing` / `done:{filepath}` |
| `lock:dept:tree` | 部门树重建锁 | 10 s | 防止并发重建 |
| `refresh:{userId}` | 刷新令牌 | 7 d | 登出时删除 |

Redis 缓存键统一定义在 `CacheKeys` 常量类中，带格式化工具方法。

---

## 测试

### 测试 Profile

`@ActiveProfiles("test")`，自动启用 H2 内存数据库（MySQL 兼容模式），排除 Redis/RocketMQ/Spring Security 自动配置。

### H2 测试 Schema

| 文件 | 用途 |
|---|---|
| `src/test/resources/schema.sql` | 主测试 Schema（`spring.sql.init.mode=always` 自动执行） |
| `src/test/resources/schema-h2.sql` | M04 模块 Schema，通过 `@Sql` 每个方法前 DROP + CREATE |

### 7 种测试模式

| 模式 | 注解栈 | 适用场景 |
|---|---|---|
| **A: 全栈集成** | `@SpringBootTest` + `@ActiveProfiles("test")` + `@Transactional` | Service + 真实 Mapper + H2 |
| **B: Controller MockMvc** | 模式 A + `@AutoConfigureMockMvc(addFilters = false)` + `@MockBean` Service | 接口层，专注参数校验 |
| **C: Controller 全栈** | 模式 B，但不 Mock Service | 接口端到端 |
| **D: 纯单元测试** | `@ExtendWith(MockitoExtension.class)` + `@Mock` Mapper + `@InjectMocks` Service | Service 逻辑单元 |
| **E: Mapper 集成** | `@SpringBootTest` + `@ActiveProfiles("test")` + `@Sql` | Mapper 层，需 `@TestConfiguration` Mock 依赖 |
| **F: 工具类单元** | 纯 JUnit 5，无 Spring | 工具类纯函数 |
| **G: 基础设施 Mock** | `@Configuration` + `@Profile("test")` | `MockInfrastructureConfig` 提供测试 Mock Bean |

---

## 关键设计约定

- **Controller 禁止 try-catch 拼 JSON**：异常统一由 `GlobalExceptionHandler` 处理
- **Entity 与 DTO 分离**：Entity 不暴露给 Controller
- **Service 方法命名**：`getXxx`/`listXxx`/`saveXxx`/`updateXxx`/`deleteXxx`
- **新增业务错误码**：在 `ResultCode` 枚举的 `4xxx` 区段追加
- **敏感信息**：数据库密码/Redis 密码通过环境变量注入，禁止写入配置文件
- **工具类**：优先 Hutool（5.8.25），不足时在 `util/` 下自行封装
- **RocketMQ 已全局禁用**，相关功能用 `@Async` + Redis 替代
- **新增 Controller 必须加 `@PreAuthorize`**，参考已有 Controller 的粒度模式
- **新增公开路径需同时修改 JwtAuthFilter + SecurityConfig 两处**
- **部门树缓存**：变更时用延迟双删策略（DEL → UPDATE → 延时 → DEL），避免并发读写脏数据

---

## API 文档

开发/测试环境启动后访问 `http://localhost:8080/doc.html`（Knife4j/Swagger 2）。

---

## 编辑器配置

`.editorconfig`：UTF-8、LF 换行、4 空格缩进（YAML 为 2）、行宽 150、末尾换行。
