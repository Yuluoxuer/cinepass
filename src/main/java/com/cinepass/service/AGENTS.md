<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# service

## Purpose
业务逻辑层接口包（当前为占位目录）。

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `impl/` | Service 实现类（`@Service`） |

## For AI Agents

### Working In This Directory
- Service 接口定义在此包，实现类放 `impl/`
- 方法命名：`getXxx`/`listXxx`/`saveXxx`/`updateXxx`/`deleteXxx`
- 事务用 `@Transactional`
- 业务异常抛 `BusinessException`

### Common Patterns
```java
public interface UserService {
    User getById(Long id);
    List<User> listByDept(Long deptId);
    void save(User user);
}
```

<!-- MANUAL: -->
