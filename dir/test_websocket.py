#!/usr/bin/env python3
"""
WebSocket实时消息推送测试脚本
用于验证用户1发送消息后，用户2能否实时接收消息
"""

import asyncio
import websockets
import json
import time

# WebSocket连接URL
WS_URL = "ws://localhost:8104/ws"

# 测试用户配置
USER1 = {
    "userId": 1,
    "token": "test_token_1",
    "deviceId": "device_1"
}

USER2 = {
    "userId": 2,
    "token": "test_token_2", 
    "deviceId": "device_2"
}

async def login(websocket, user_config):
    """发送登录消息"""
    login_message = {
        "type": "MESSAGE_LOGIN",
        "data": {
            "token": user_config["token"],
            "deviceId": user_config["deviceId"],
            "kickOthers": False
        }
    }
    await websocket.send(json.dumps(login_message))
    response = await websocket.recv()
    print(f"{user_config['userId']}号用户登录响应: {response}")
    return json.loads(response)

async def send_private_message(websocket, to_user_id, content):
    """发送私聊消息"""
    message = {
        "type": "MESSAGE_PRIVATE_MESSAGE",
        "data": {
            "toUserId": to_user_id,
            "content": content,
            "msgType": "TEXT",
            "clientMsgId": f"msg_{int(time.time() * 1000)}"
        }
    }
    await websocket.send(json.dumps(message))
    response = await websocket.recv()
    print(f"发送消息响应: {response}")
    return json.loads(response)

async def receive_messages(websocket, user_id, timeout=5):
    """接收消息"""
    messages = []
    start_time = time.time()
    
    while time.time() - start_time < timeout:
        try:
            message = await asyncio.wait_for(websocket.recv(), timeout=1.0)
            messages.append(json.loads(message))
            print(f"{user_id}号用户收到消息: {message}")
        except asyncio.TimeoutError:
            continue
    
    return messages

async def test_user2_websocket():
    """用户2的WebSocket连接，用于接收消息"""
    print("\n=== 用户2启动WebSocket连接 ===")
    async with websockets.connect(f"{WS_URL}?token={USER2['token']}&deviceId={USER2['deviceId']}") as websocket:
        # 登录
        await login(websocket, USER2)
        
        # 等待接收消息
        print("用户2等待接收消息...")
        messages = await receive_messages(websocket, USER2["userId"], timeout=10)
        
        print(f"\n用户2共收到 {len(messages)} 条消息")
        return messages

async def test_user1_send_message():
    """用户1发送消息"""
    print("\n=== 用户1启动WebSocket连接 ===")
    await asyncio.sleep(2)  # 等待用户2连接
    
    async with websockets.connect(f"{WS_URL}?token={USER1['token']}&deviceId={USER1['deviceId']}") as websocket:
        # 登录
        await login(websocket, USER1)
        
        # 发送消息给用户2
        print("\n用户1发送消息给用户2...")
        await send_private_message(websocket, USER2["userId"], "你好，这是一条测试消息！")
        
        # 等待响应
        await asyncio.sleep(1)

async def main():
    """主测试函数"""
    print("=== WebSocket实时消息推送测试 ===")
    print(f"WebSocket服务器地址: {WS_URL}")
    
    # 同时启动用户1和用户2的连接
    user2_task = asyncio.create_task(test_user2_websocket())
    user1_task = asyncio.create_task(test_user1_send_message())
    
    # 等待两个任务完成
    await user1_task
    messages = await user2_task
    
    # 分析结果
    print("\n=== 测试结果分析 ===")
    if messages:
        print("✅ 测试成功！用户2实时收到了用户1发送的消息")
        print(f"✅ 用户2收到消息数量: {len(messages)}")
        for i, msg in enumerate(messages, 1):
            print(f"   消息{i}: {msg.get('type', 'unknown')}")
    else:
        print("❌ 测试失败！用户2没有收到实时消息")
        print("   可能原因：")
        print("   1. WebSocket连接配置不正确")
        print("   2. 消息推送逻辑有问题")
        print("   3. 用户认证失败")

if __name__ == "__main__":
    try:
        asyncio.run(main())
    except Exception as e:
        print(f"测试过程中发生错误: {e}")
        import traceback
        traceback.print_exc()