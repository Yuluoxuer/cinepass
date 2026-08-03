# 代码审查修复清单

> 分支：`feature/leijieming` · 审查日期：2026-08-03

---

## 🔴 严重（必须在合并前修复）

- [ ] **#1 密码修改接口无需登录**
  - 文件：`AuthController.java:48`
  - 问题：`POST /api/v1/auth/password/change` 在 `SecurityConfig` 中为 `permitAll`，且无频率限制，攻击者可暴力破解任意账号密码。
  - 修复：在 `changePassword` 方法上加 `@LoginUser`，移除匿名 account 分支；或保留匿名路径但加 IP 限速 + 旧密码错误计数。

- [ ] **#2 DataInitializer 种子数据**
  - 文件：`DataInitializer.java:38`
  - 问题：staff 种子账号插入时 `cinemaId` 为 null，违反 schema CHECK 约束 `chk_staff_cinema`，导致首次启动崩溃。
  - 修复：删除 `DataInitializer`（或清空 `run()` 方法体），不使用种子数据。

---

## 🟠 高（影响功能正确性）

- [ ] **#3 staff → user 降级被永久阻断**
  - 文件：`AdminUserService.java:158`
  - 问题：将 staff 降级为 user 时，若请求体未显式传 `cinemaId=""`，`resolveCinemaIdForRole` 用旧 cinemaId 触发 400 报错。
  - 修复：在 `resolveCinemaIdForRole` 中，当 `role != staff` 时直接返回 `null`，不再校验传入的 cinemaId（自动清除）。

- [ ] **#4 末位管理员保护非原子，可被并发绕过**
  - 文件：`AdminUserService.java:113`
  - 问题：两个并发请求各自读到 `countActiveAdmins()=1`，均通过校验并提交，导致零管理员。
  - 修复：在 `userAccountMapper.countActiveAdmins` 查询中加 `SELECT ... FOR UPDATE`（行锁），或在 `update` 方法加应用层锁。

---

## 🟡 中（安全 / 性能）

- [ ] **#5 logout 黑名单 TTL 使用配置值而非 token 实际剩余时间**
  - 文件：`AuthService.java:118`
  - 问题：`denyJti(jti, jwtUtil.getExpiresInSeconds())` 使用全量 TTL；配置变更后已退出的 token 可能在黑名单过期后重新被接受。
  - 修复：在 `JwtUtil` 添加 `getRemainingSeconds(token)` 方法（从 `exp` claim 计算剩余秒数），`logout` 时传入实际剩余时间。

- [ ] **#6 WantSeeService.add() 存在 TOCTOU 竞态**
  - 文件：`WantSeeService.java:41`
  - 问题：`exists()` + `insertIgnore()` 分两步执行，并发时双请求均读到 `exists=false`，可能导致 `want_see_count` 被重复递增。
  - 修复：移除 `exists()` 预检，直接调用 `insertIgnore()`，仅当返回受影响行数 `> 0` 时才调用 `incrWantSeeCount()`。

- [ ] **#7 schema.sql 缺少 want_see 分页索引**
  - 文件：`schema.sql`
  - 问题：`want_see` 表无 `(user_id, created_at)` 复合索引，分页查询需全表排序。
  - 修复：添加索引：
    ```sql
    CREATE INDEX idx_want_see_user_time ON want_see(user_id, created_at DESC);
    ```

---

## 🔵 低（维护性）

- [ ] **#8 PhoneMask 与 SensitiveDataUtil.maskPhone() 逻辑重复**
  - 文件：`PhoneMask.java:16`
  - 问题：两处相同的手机脱敏逻辑，规则变更时容易漏改其中一处。
  - 修复：删除 `PhoneMask`，`AdminUserService` 和 `AuthService` 改用 `SensitiveDataUtil.maskPhone()`。

- [ ] **#9 UserAccount.updateCinemaId 是传输标志位混入领域实体**
  - 文件：`UserAccount.java`
  - 问题：布尔标志仅用于控制 MyBatis 动态 SQL，若实体被缓存后再次传入 `update()`，可能静默覆盖数据库 `cinema_id`。
  - 修复：提取专用 `updateCinemaId(userId, cinemaId)` Mapper 方法，去掉实体上的标志位。

---

## 优先级建议

| 优先级 | 编号 | 说明 |
|--------|------|------|
| 本次 PR 合并前 | #1 #2 #3 #4 | 安全漏洞 + 启动崩溃 + 功能阻断 |
| 下个迭代 | #5 #6 #7 | 安全加固 + 并发修复 + 性能优化 |
| 有空再清理 | #8 #9 | 代码维护性改善 |