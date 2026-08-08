# 购票辅助 Agent 测试指南

## 一、环境准备（三要素）

| 依赖 | 状态 | 说明 |
|------|------|------|
| DeepSeek API Key | ✅ 已配置 | `agent/.env` 中 `DEEPSEEK_API_KEY`（注意：若 shell 环境变量是占位符会覆盖，config.py 已自动纠正） |
| PostgreSQL | ✅ 已配置 | `agent/.env` 中 POSTGRES_* 配置，`agent_db` 数据库需存在 |
| 票务中台后端 | ⚠️ 需启动 | `agent/.env` 中 `BACKEND_BASE_URL`（默认 http://localhost:8000），提供 /movies /cinemas /shows /locks /orders 等业务 API |

## 二、启动后端

票务中台提供电影、影院、场次、座位、订单等业务 API，需在 `localhost:8000` 运行。

```bash
# 请按你的票务中台实际启动方式运行，例如：
cd <票务中台项目目录>
uvicorn main:app --host 0.0.0.0 --port 8000 --reload
```

验证：`curl http://localhost:8000/health` 返回 200。

## 三、运行测试脚本

```bash
cd /home/rei/Code/FastAPIAgent
conda run -n langchain python -m agent.test_booking_flow
```

可选环境变量：
```bash
TOKEN=eyJxxx...  conda run -n langchain python -m agent.test_booking_flow   # 携带JWT测试登录操作
LAT=39.9042 LNG=116.4074  # 自定义位置坐标
```

## 四、测试场景与预期

### 场景1：普通问答 · 查热映电影
输入：`最近有什么热映的电影？`
预期：agent 调用 `searchMovies(status="hot_showing")`，列出热映影片，**不引导购票**。

### 场景2：一句话含多个购票要点
输入：`我想看《流浪地球2》，帮我找今天附近评分最高的电影院`
预期：agent 识别出 3 个信息点（电影、时间=今天、影院偏好=评分最高），
调用 `searchMovies` 和 `searchCinemas`，并用 `updateBookingDraft` 记录。

### 场景3：多轮补全 · 验证记忆
输入：`就选第一个影院吧，晚上7点左右的场次，2张票`
预期：agent 通过 `getBookingDraft` 读取已有草稿，补充影院/场次时间/票数，
不重复询问已有信息，引导完成选座和下单。

### 场景4：普通闲聊
输入：`你好呀，今天天气怎么样？`
预期：友好闲聊，**不强行引导购票**。

## 五、直接验证 BookingDraft（不依赖后端）

```bash
conda run -n langchain python -c "
import asyncio
from agent.tools.booking_draft import ensure_table, load_draft, save_draft, use_session_id
async def main():
    await ensure_table()
    with use_session_id('demo'):
        d = await load_draft()
        d['filmTitle'] = '流浪地球2'; d['count'] = '2'
        await save_draft(d)
        d = await load_draft(); d['timeWindow'] = 'evening'
        await save_draft(d)
        print('草稿:', await load_draft())
asyncio.run(main())
"
```

预期输出：`草稿: {'filmTitle': '流浪地球2', 'count': '2', 'timeWindow': 'evening'}`

## 六、数据库表

| 表 | 用途 |
|----|------|
| checkpoints / checkpoint_blobs / checkpoint_writes | LangGraph PostgresSaver 对话记忆 |
| booking_drafts | 购票草稿（session_id 主键，JSONB draft） |

查看：`psql -U postgres -d agent_db -c "\dt"`
