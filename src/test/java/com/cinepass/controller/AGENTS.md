<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# test/controller

## Purpose
Controller 层测试（MockMvc 集成测试）。

## For AI Agents

### Working In This Directory
- 使用 `@SpringBootTest` + `@AutoConfigureMockMvc(addFilters = false)` + `@MockBean` Service
- 关注参数校验和响应结构
- 禁用 Security Filter 以简化测试

### Common Patterns
```java
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class UserControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private UserService userService;
}
```

<!-- MANUAL: -->
