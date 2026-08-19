<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# aop

## Purpose
AOP 切面包。操作审计注解及切面实现。

## Key Files

| File | Description |
|------|-------------|
| `AuditLog.java` | 审计注解（`@Target(METHOD)`） |
| `AuditLogAspect.java` | 审计切面（`@Aspect`），记录执行时间/结果/异常 |

## For AI Agents

### Working In This Directory
- 切面用 `@Aspect` + `@Component`
- 审计日志当前仅打 SLF4J，接入 Mapper 后可持久化

### Common Patterns
```java
@AuditLog(module = "用户管理", action = "新增用户")
public Result<User> createUser(@RequestBody User user) { ... }
```

<!-- MANUAL: -->
