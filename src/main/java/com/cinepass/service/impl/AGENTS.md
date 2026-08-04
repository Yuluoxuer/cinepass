<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-08-03 -->

# service/impl

## Purpose
Service 实现类（`@Service`，命名 `XxxServiceImpl`）。

## For AI Agents

### Working In This Directory
- 实现对应 `service/` 下的接口；Controller 只依赖接口类型
- 事务用 `@Transactional`（标在实现类方法上）
- 业务异常抛 `BusinessException`

### 注释约定
- **类级**：`/** {@link XxxService} 实现。 */`，可附关键约束
- **方法级**：每个公开/私有方法一句话中文 Javadoc，说明做什么
- **行内 `//`**：补充「为什么」（幂等、竞态、权限收窄等）

### Common Patterns
```java
/**
 * {@link UserService} 实现。
 */
@Service
public class UserServiceImpl implements UserService {
    /** 保存用户 */
    @Override
    @Transactional
    public void save(User user) {
        userMapper.insert(user);
    }
}
```

<!-- MANUAL: -->
