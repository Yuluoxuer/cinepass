<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-07-30 | Updated: 2026-07-30 -->

# mq

## Purpose
RocketMQ 相关包（**已全局禁用**，`rocketmq.enable: false`）。

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `client/` | MQ 客户端封装 |
| `config/` | MQ 配置类 |
| `consumer/` | 消息消费者 |
| `controller/` | MQ 管理接口 |
| `dto/` | MQ 数据传输对象 |
| `entity/` | MQ 相关实体 |
| `exception/` | MQ 异常类 |
| `mapper/` | MQ Mapper |
| `schedule/` | MQ 定时任务 |
| `service/` | MQ 业务逻辑 |

## For AI Agents

### Working In This Directory
- **RocketMQ 已全局禁用**，启动类排除自动配置
- 异步功能用 `@Async` + Redis 替代
- 如需重新启用，设置 `rocketmq.enable: true` 并移除启动类的 `exclude`

<!-- MANUAL: -->
