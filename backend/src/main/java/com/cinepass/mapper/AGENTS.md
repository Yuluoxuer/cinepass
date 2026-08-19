<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# mapper

## Purpose
MyBatis Mapper 接口包（当前为占位目录）。

## For AI Agents

### Working In This Directory
- Mapper 接口用 `@Mapper` 注解
- 简单 CRUD 用注解（`@Select`/`@Insert`/`@Update`/`@Delete`）
- 复杂 SQL 写在 `src/main/resources/mapper/*.xml`
- XML 的 `namespace` = Mapper 接口完整类名

### Common Patterns
```java
@Mapper
public interface UserMapper {
    @Select("SELECT * FROM sys_user WHERE id = #{id}")
    User selectById(Long id);
    
    // 复杂查询在 UserMapper.xml 中定义
    List<User> selectByConditions(UserQuery query);
}
```

<!-- MANUAL: -->
