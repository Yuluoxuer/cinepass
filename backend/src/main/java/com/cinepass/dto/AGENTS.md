<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# dto

## Purpose
数据传输对象包（当前为占位目录）。用于接口入参/出参。

## For AI Agents

### Working In This Directory
- DTO 用于 Controller 层与前端交互
- 使用 Lombok `@Data`
- 参数校验注解：`@NotBlank`/`@NotNull`/`@Min`/`@Max`/`@Pattern`

### Common Patterns
```java
@Data
public class UserCreateDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20)
    private String password;
}
```

<!-- MANUAL: -->
