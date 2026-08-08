"""购票辅助Agent 能力测试脚本

用法：
    cd /home/rei/Code/FastAPIAgent
    conda run -n langchain python -m agent.test_booking_flow

覆盖场景：
  1. 普通问答：查热映电影（走 searchMovies 工具）
  2. 一句话含多个购票要点：电影 + 时间 + 影院偏好
  3. 多轮补全：逐步补充缺失信息，验证 bookingdraft 记忆
  4. 普通闲聊：不购票时不被强行引导

可选环境变量：
  TOKEN=xxx    携带 JWT 测试登录相关操作
"""
import asyncio
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from agent.agent import get_agent

TOKEN = os.getenv("TOKEN", "")
# 默认用北京天安门坐标
LAT = os.getenv("LAT", "39.9042")
LNG = os.getenv("LNG", "116.4074")

SID = "test_booking_flow_001"


async def chat(agent, sid, msg, tag="", **ctx):
    print(f"\n{'='*60}")
    print(f"[{tag}] 用户: {msg}")
    print(f"{'-'*60}")
    resp = await agent.run(msg, sid, **ctx)
    print(f"[{tag}] Agent: {resp}")
    return resp


async def main():
    agent = await get_agent()
    print("🤖 购票辅助Agent 测试开始\n")

    ctx = {"authorization": TOKEN, "latitude": float(LAT), "longitude": float(LNG)} if TOKEN else {"latitude": float(LAT), "longitude": float(LNG)}

    # ---- 场景1：普通问答，查热映电影 ----
    print("\n########## 场景1：普通问答 · 热映电影 ##########")
    await chat(agent, SID, "最近有什么热映的电影？", "S1", **ctx)

    # ---- 场景2：一句话含多个购票要点 ----
    print("\n########## 场景2：一句话含多个购票要点 ##########")
    await chat(agent, SID, "我想看《流浪地球2》，帮我找今天附近评分最高的电影院", "S2", **ctx)

    # ---- 场景3：继续补充信息（验证记忆） ----
    print("\n########## 场景3：多轮补全 · 验证 bookingdraft 记忆 ##########")
    await chat(agent, SID, "就选第一个影院吧，晚上7点左右的场次，2张票", "S3", **ctx)

    # ---- 场景4：普通闲聊，验证不强行引导购票 ----
    print("\n########## 场景4：普通闲聊 ##########")
    await chat(agent, SID, "你好呀，今天天气怎么样？", "S4", **ctx)

    await agent.close()
    print("\n🏁 测试完成")


if __name__ == "__main__":
    asyncio.run(main())
