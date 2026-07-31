<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# vo

## Purpose
视图对象包（当前为占位目录）。跨表组装的展示对象。

## For AI Agents

### Working In This Directory
- VO 用于 Controller 返回给前端
- 通常包含多表 JOIN 的数据
- 使用 Lombok `@Data`

### Common Patterns
```java
@Data
public class UserDetailVO {
    private Long userId;
    private String username;
    private String deptName;      // 来自 department 表
    private String positionName;  // 来自 position 表
}
```

<!-- MANUAL: -->
