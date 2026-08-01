<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# serializer

## Purpose
Jackson 序列化器包。字段权限控制、脱敏序列化（当前为桩实现）。

## Key Files

| File | Description |
|------|-------------|
| `FieldPermissionSerializer.java` | 字段权限检查工具（模板桩，默认全部穿透） |
| `FieldPermissionAnnotationIntrospector.java` | Jackson 注解内省器桩 |
| `FieldPermissionPropertyFilter.java` | Jackson 属性过滤器桩 |

## For AI Agents

### Working In This Directory
- 当前为桩实现（`FieldRule.PASS_THROUGH`），未实际接入 Jackson
- 实际脱敏逻辑在 `util.SensitiveDataUtil`

<!-- MANUAL: -->
