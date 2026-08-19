<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# constant

## Purpose
常量定义包。Redis 缓存键常量、数据权限范围类型枚举。

## Key Files

| File | Description |
|------|-------------|
| `CacheKeys.java` | Redis 缓存 Key 常量（`LOGIN_FAIL_COUNT`、`REFRESH_TOKEN`、`USER_PERMS` 等），提供格式化工具方法 |
| `ScopeType.java` | 数据权限范围类型枚举（`ALL`/`DEPT`/`DEPT_AND_SUB`/`SELF`） |

## For AI Agents

### Working In This Directory
- 常量类用 `public static final`，工具方法用 `public static`
- Redis Key 统一在 `CacheKeys` 管理，禁止硬编码字符串

### Common Patterns
```java
String key = CacheKeys.userPermsKey(userId);  // "user:perms:123"
redisUtil.set(key, perms, 1800);
```

<!-- MANUAL: -->
