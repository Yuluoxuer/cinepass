<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# test/service/impl

## Purpose
ServiceImpl 测试（单元测试 + Spring 集成测试）。

## For AI Agents

### Working In This Directory
- 纯单元测试：`@ExtendWith(MockitoExtension.class)` + `@Mock` Mapper + `@InjectMocks` ServiceImpl
- 集成测试：`@SpringBootTest` + `@ActiveProfiles("test")` + `@Transactional`

### Common Patterns
```java
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock
    private UserMapper userMapper;
    @InjectMocks
    private UserServiceImpl userService;
}
```

<!-- MANUAL: -->
