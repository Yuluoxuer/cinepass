# leijieming_backend — Spring Boot Admin Template

Reusable Spring Boot 2.7 skeleton with JWT security, MyBatis, Redis, and Knife4j — no business modules included.

## Stack

Spring Boot 2.7.18 · Java 8 · MyBatis · PageHelper · Druid · MySQL · Redis · JWT · Knife4j

## Package layout

| Package | Contents |
|---------|----------|
| `config` | Jackson, MyBatis, WebMvc, Redis, AutoFill, bootstrap stubs |
| `security` | JWT filter, SecurityConfig, SecurityContext |
| `common` / `util` | Result, exceptions, Redis helpers |
| `aop` | `@AuditLog` annotation + aspect stub |
| `serializer` | Field-permission JSON filter stubs |
| `constant` | CacheKeys, ScopeType |
| `controller`, `service`, `entity`, `mapper`, `dto`, `vo` | Empty — add your business code here |
| `mq/*` | Empty MQ sub-packages (`.gitkeep`) |

## Quick start

```bash
# Configure MySQL + Redis in application-dev.yml or env vars, then:
mvn spring-boot:run
```

API docs: http://localhost:8080/doc.html

## Tests

```bash
mvn test
```

Uses H2 in-memory DB and mocked Redis (`MockInfrastructureConfig` + `TestMockBeanConfig`).

## Schema

Add DDL to `schema.sql` (root) and `src/test/resources/schema.sql` for tests.
