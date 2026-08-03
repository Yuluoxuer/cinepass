<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# service

## Purpose
业务逻辑层：本包放 **接口**，实现类放 `impl/`。

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `impl/` | Service 实现类（`@Service`，命名 `XxxServiceImpl`） |

## For AI Agents

### Working In This Directory
- 接口：`XxxService.java`（本包）；实现：`impl/XxxServiceImpl.java`
- 方法命名：`getXxx`/`listXxx`/`saveXxx`/`updateXxx`/`deleteXxx`
- 事务用 `@Transactional`（标在实现类方法上）
- 业务异常抛 `BusinessException`
- Controller 依赖接口类型注入，不依赖 `impl` 包

### Common Patterns
```java
public interface UserService {
    User getById(Long id);
    List<User> listByDept(Long deptId);
    void save(User user);
}
```

<!-- MANUAL: -->
