<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# common

## Purpose
公共基础设施包。统一响应体、业务异常、状态码、审计注解、全局异常处理。

## Key Files

| File | Description |
|------|-------------|
| `Result.java` | 统一响应体（`success()`/`fail()`） |
| `ResultCode.java` | 状态码枚举（HTTP + 业务 4xxx） |
| `PageResult.java` | 分页响应体 |
| `BusinessException.java` | 业务异常 |
| `GlobalExceptionHandler.java` | 全局异常处理器（`@RestControllerAdvice`） |
| `AutoFill.java` | 审计字段自动填充注解 |

## For AI Agents

### Working In This Directory
- **Controller 禁止 try-catch 拼 JSON**，异常统一由 `GlobalExceptionHandler` 处理
- 新增业务错误码在 `ResultCode` 的 `4xxx` 区段
- 所有接口返回 `Result<T>` 或 `PageResult<T>`

### Common Patterns
```java
return Result.success(data);
throw new BusinessException(ResultCode.CONFLICT, "用户已存在");
```

<!-- MANUAL: -->
