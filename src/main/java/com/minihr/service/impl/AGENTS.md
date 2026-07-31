<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# service/impl

## Purpose
Service 实现类包（当前为占位目录）。

## For AI Agents

### Working In This Directory
- 实现类命名：`XxxServiceImpl`
- 用 `@Service` 注解
- 实现对应的 Service 接口
- 事务用 `@Transactional`（默认 REQUIRED 传播）

### Common Patterns
```java
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    
    @Override
    @Transactional
    public void save(User user) {
        userMapper.insert(user);
    }
}
```

<!-- MANUAL: -->
