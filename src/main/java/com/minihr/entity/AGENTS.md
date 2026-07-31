<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# entity

## Purpose
JPA/MyBatis 实体类包（当前为占位目录）。数据库表映射。

## For AI Agents

### Working In This Directory
- Entity 类名 = 表名转大驼峰（`sys_user` → `SysUser`）
- 使用 Lombok `@Data`
- 实现 `Serializable`
- 审计字段用 `@AutoFill` 注解

### Common Patterns
```java
@Data
public class SysUser implements Serializable {
    private Long id;
    private String username;
    
    @AutoFill(FillType.INSERT)
    private LocalDateTime createTime;
    
    @AutoFill(FillType.BOTH)
    private LocalDateTime updateTime;
}
```

<!-- MANUAL: -->
