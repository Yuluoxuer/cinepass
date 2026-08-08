"""测试Agent功能"""
import asyncio
from agent import get_agent


async def test_basic_chat():
    """测试基本对话"""
    print("=== 测试基本对话 ===")
    agent = await get_agent()
    
    session_id = "test_session_001"
    
    # 测试问候
    response = await agent.run("你好", session_id)
    print(f"User: 你好")
    print(f"Agent: {response}\n")
    
    # 测试获取时间
    response = await agent.run("现在几点了？", session_id)
    print(f"User: 现在几点了？")
    print(f"Agent: {response}\n")
    
    await agent.close()


async def test_movie_search():
    """测试电影搜索"""
    print("=== 测试电影搜索 ===")
    agent = await get_agent()
    
    session_id = "test_session_002"
    
    # 测试搜索电影
    response = await agent.run("有什么热映电影？", session_id)
    print(f"User: 有什么热映电影？")
    print(f"Agent: {response}\n")
    
    await agent.close()


async def test_multi_turn():
    """测试多轮对话记忆"""
    print("=== 测试多轮对话 ===")
    agent = await get_agent()
    
    session_id = "test_session_003"
    
    messages = [
        "我想看电影",
        "有什么推荐吗？",
        "第一部电影详细信息",
    ]
    
    for msg in messages:
        response = await agent.run(msg, session_id)
        print(f"User: {msg}")
        print(f"Agent: {response}\n")
    
    await agent.close()


async def main():
    """运行所有测试"""
    try:
        await test_basic_chat()
        print("\n" + "="*60 + "\n")
        
        await test_movie_search()
        print("\n" + "="*60 + "\n")
        
        await test_multi_turn()
        
    except Exception as e:
        print(f"测试出错: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    asyncio.run(main())
