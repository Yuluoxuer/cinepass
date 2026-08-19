<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-16 | Updated: 2026-07-16 -->

# util

## Purpose
项目自封装工具类目录。存放 Hutool 无法满足需求时的自定义工具方法，如 JWT 工具、Redis Key 管理、AES 加密封装等。工具类不包含业务逻辑，保持无状态或仅依赖配置。

## Key Files

| File | Description |
|------|-------------|
| `RedisUtil.java` | RedisTemplate 封装（String/Hash/List/Set/ZSet、分布式锁）；`@ConditionalOnBean(RedisTemplate)` |
| `RedisKeyUtil.java` | Redis Key 静态拼装（如工资条 captcha / viewToken） |

## For AI Agents

### Working In This Directory
- **优先使用 Hutool 5.8.25**：`cn.hutool.core.*`、`cn.hutool.crypto.*`、`cn.hutool.poi.*` 均可直接用；Hutool 不满足时才在此封装
- 无状态工具用静态方法；需注入 Spring 配置（如 `@Value`）时注册为 `@Component`
- 每个工具类专注单一职责

### Common Patterns
```java
// JWT 工具（需 @Value，注册为 Bean）
@Component
public class JwtUtil {
    @Value("${jwt.secret}")
    private String secret;

    public String generateToken(Long userId, String username) { ... }
    public Claims parseToken(String token) { ... }
    public boolean isTokenExpired(String token) { ... }
}

// Redis Key 工具（纯静态）
public class RedisKeyUtil {
    public static String userTokenKey(Long userId) {
        return "token:user:" + userId;
    }
}
```

### Testing Requirements
- 加密/解密、Token 生成/解析等安全相关方法必须有单元测试

## Dependencies

### Internal
- `service/impl/` — 主要使用方

### External
- Hutool 5.8.25（`cn.hutool.crypto.*` 等）
- JWT (jjwt 0.11.5)

<!-- MANUAL: Any manually added notes below this line are preserved on regeneration -->
