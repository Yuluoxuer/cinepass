<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# controller

## Purpose
REST API 控制器层（当前为占位目录，业务 Controller 待添加）。

## For AI Agents

### Working In This Directory
- Controller 类用 `@RestController` + `@RequestMapping`
- 所有接口返回 `Result<T>` 或 `PageResult<T>`
- 参数校验用 `@Valid` + DTO 的 `@NotBlank`/`@NotNull`
- **必须加 `@PreAuthorize`**（参考 CLAUDE.md 的权限粒度）
- 禁止在 Controller 手动 try-catch 拼 JSON

### Common Patterns
```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public Result<List<User>> list() { ... }
}
```

<!-- MANUAL: -->
