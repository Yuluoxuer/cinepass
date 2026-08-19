<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-08-01 | Updated: 2026-08-01 -->

# model

## Purpose
MyBatis 表行 DO（普通 JavaBean）。**不是** JPA `@Entity`；工程已移除 `entity/` 包。

## For AI Agents

### Working In This Directory
- 类名与表对应（如 `user_account` → `UserAccount`），package `com.cinepass.model`
- 使用 Lombok `@Data` 即可；无 `@Entity` / `@Table`
- Mapper XML `resultType` 指向本包；对外 API 用 `dto` / `vo`，在 Service 转换
- `application.yml`：`mybatis.type-aliases-package=com.cinepass.model`

<!-- MANUAL: -->
