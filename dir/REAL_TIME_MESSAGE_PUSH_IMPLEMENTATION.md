# 实时消息推送逻辑实现方案

## 1. 系统架构设计

### 1.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              实时消息推送系统架构                                  │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│   ┌──────────────┐      HTTP/REST      ┌──────────────┐                       │
│   │   前端应用    │ ──────────────────→ │   API网关     │                       │
│   │   (Web/APP)   │ ←───────────────── │   (Gateway)   │                       │
│   └──────┬───────┘                     └──────┬───────┘                       │
│          │                                     │                               │
│          │ WebSocket                           │                               │
│          ↓                                     ↓                               │
│   ┌──────────────┐                     ┌──────────────┐                       │
│   │   Netty      │ ←─────────────────→ │   IM服务      │                       │
│   │ WebSocket    │    消息推送           │  (bw-im)     │                       │
│   │   Server     │                     │               │                       │
│   └──────┬───────┘                     └──────┬───────┘                       │
│          │                                     │                               │
│          │ 连接管理/消息分发                     │ 业务逻辑处理                    │
│          ↓                                     ↓                               │
│   ┌──────────────┐                     ┌──────────────┐                       │
│   │  Session     │ ←─────────────────→ │  Message     │                       │
│   │  Manager     │    用户状态同步         │  Service     │                       │
│   │   (Redis)    │                     │   (Impl)     │                       │
│   └──────────────┘                     └──────┬───────┘                       │
│                                               │                               │
│                                               ↓                               │
│                          ┌──────────────────────────────────┐                  │
│                          │            MySQL                 │                  │
│                          │  ┌──────────┐  ┌──────────────┐  │                  │
│                          │  │im_message│  │conversation │  │                  │
│                          │  └──────────┘  └──────────────┘  │                  │
│                          └──────────────────────────────────┘                  │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 架构组件说明

| 组件 | 职责 | 技术选型 |
|------|------|----------|
| **前端应用** | 用户界面展示、消息渲染、WebSocket连接管理 | React/Vue + WebSocket API |
| **API网关** | 请求路由、负载均衡、认证鉴权 | Spring Cloud Gateway |
| **Netty WebSocket Server** | 实时消息推送、连接管理、心跳检测 | Netty 4.x |
| **IM服务** | 消息业务逻辑、会话管理、消息路由 | Spring Boot |
| **Session Manager** | 用户在线状态管理、设备管理 | Redis Hash |
| **MySQL** | 消息持久化、会话存储 | MySQL 8.0+ |

---

## 2. 技术选型依据

### 2.1 实时通信机制对比

| 特性 | WebSocket | SSE (Server-Sent Events) | Long Polling |
|------|-----------|--------------------------|--------------|
| **双向通信** | 支持 | 仅服务端推送 | 模拟支持 |
| **连接开销** | 低（单连接） | 低 | 高（频繁建立） |
| **浏览器兼容性** | 良好 | 良好 | 优秀 |
| **实时性** | 毫秒级 | 秒级 | 秒级 |
| **资源占用** | 低 | 中等 | 高 |
| **适用场景** | 即时通讯、游戏 | 实时通知、监控 | 兼容性要求高 |

**选型结论**：选择 **WebSocket** 作为实时通信机制，基于以下理由：
- 需要双向通信（消息发送和接收）
- 低延迟要求（即时消息场景）
- 已在项目中实现 Netty WebSocket Server

### 2.2 消息存储方案

- **MySQL**：存储消息持久化数据、会话信息
- **Redis**：存储用户在线状态、会话缓存

---

## 3. 核心流程与时序图

### 3.1 用户发送消息流程

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  前端用户1  │     │  WebSocket   │     │  ImMessage   │     │    MySQL     │
│   (Sender)  │     │   Handler    │     │    Service   │     │              │
└─────┬──────┘     └──────┬───────┘     └──────┬───────┘     └──────┬───────┘
      │                   │                    │                    │
      │ 1. 发送消息        │                    │                    │
      │ ─────────────────→ │                    │                    │
      │                   │                    │                    │
      │                   │ 2. 验证请求        │                    │
      │                   │ ─────────────────→ │                    │
      │                   │                    │                    │
      │                   │                    │ 3. 生成消息ID      │
      │                   │                    │ ←───────────────── │
      │                   │                    │                    │
      │                   │                    │ 4. 保存消息        │
      │                   │                    │ ─────────────────→ │
      │                   │                    │                    │
      │                   │ 5. 返回ACK         │                    │
      │ 6. 消息ACK        │ ←───────────────── │                    │
      │ ←───────────────── │                    │                    │
      │                   │                    │                    │
      │                   │ 7. 推送给接收者    │                    │
      │                   │ ←───────────────── │                    │
      │                   │                    │                    │
      │                   │ 8. 更新会话        │                    │
      │                   │ ←───────────────── │                    │
      │                   │                    │                    │
┌─────┴──────┐     ┌──────┴───────┐     ┌──────┴───────┐     ┌──────┴───────┐
│  前端用户1  │     │  WebSocket   │     │  ImMessage   │     │    MySQL     │
│   (Sender)  │     │   Handler    │     │    Service   │     │              │
└─────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
```

### 3.2 消息接收与推送流程

```
┌──────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────┐
│  用户2    │     │SessionManager│     │  ImMessage   │     │ 用户2     │
│  (在线)   │     │              │     │    Service   │     │ (离线)    │
└─────┬──────┘     └──────┬───────┘     └──────┬───────┘     └──────┬─────┘
      │                   │                    │                    │
      │                   │                    │ 1. 查询用户状态    │
      │                   │ ←───────────────── │                    │
      │                   │                    │                    │
      │                   │ 2. 用户在线       │                    │
      │ 3. 实时推送消息   │ ←───────────────── │                    │
      │ ←───────────────── │                    │                    │
      │                   │                    │                    │
      │                   │                    │ 4. 用户离线       │
      │                   │                    │ ─────────────────→ │
      │                   │                    │                    │
      │                   │                    │ 5. 保存离线消息    │
      │                   │                    │ ←───────────────── │
      │                   │                    │                    │
      │ 6. 用户上线时     │                    │                    │
      │ 同步离线消息      │                    │                    │
      │ ─────────────────→ │                    │                    │
      │                   │ 7. 查询离线消息    │                    │
      │                   │ ─────────────────→ │                    │
      │ 8. 返回离线消息   │ ←───────────────── │                    │
      │ ←───────────────── │                    │                    │
┌─────┴──────┐     ┌──────┴───────┐     ┌──────┴───────┐     ┌──────┴─────┐
│  用户2    │     │SessionManager│     │  ImMessage   │     │ 用户2     │
│  (在线)   │     │              │     │    Service   │     │ (离线)    │
└─────────────┘     └──────────────┘     └──────────────┘     └────────────┘
```

---

## 4. 接口定义规范

### 4.1 REST API 接口

#### 4.1.1 发送消息

| 属性 | 值 |
|------|-----|
| **URL** | `POST /api/im/messages/send` |
| **认证** | JWT Token (Header: `Authorization: Bearer xxx`) |
| **Content-Type** | `application/json` |

**请求体**：
```json
{
  "clientMsgId": "msg_1234567890",
  "toUserId": 1002,
  "content": "Hello, World!",
  "msgType": "TEXT",
  "extra": "{\"customData\": \"value\"}"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `clientMsgId` | String | 否 | 客户端消息ID，不传则自动生成 |
| `toUserId` | Long | 是 | 接收者用户ID |
| `content` | String | 是 | 消息内容 |
| `msgType` | String | 否 | 消息类型（TEXT/IMAGE/VIDEO等），默认TEXT |
| `extra` | String | 否 | 额外数据（JSON格式） |

**成功响应**（200 OK）：
```json
{
  "code": 0,
  "data": {
    "id": 1234567890123456789,
    "clientMsgId": "msg_1234567890",
    "conversationId": "chat_1001_1002",
    "fromUserId": 1001,
    "toUserId": 1002,
    "content": "Hello, World!",
    "msgType": "TEXT",
    "status": 0,
    "createTime": 1699999999999
  },
  "message": "success"
}
```

#### 4.1.2 获取会话列表

| 属性 | 值 |
|------|-----|
| **URL** | `GET /api/im/conversations` |
| **认证** | JWT Token |

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `current` | Long | 否 | 1 | 当前页码 |
| `pageSize` | Long | 否 | 20 | 每页数量 |

**成功响应**（200 OK）：
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "conversationId": "chat_1001_1002",
        "peerUserId": 1002,
        "lastMsgId": 1234567890123456789,
        "lastMsgContent": "Hello, World!",
        "lastMsgTime": 1699999999999,
        "unreadCount": 3,
        "peerUser": {
          "userId": 1002,
          "userAccount": "user2",
          "userAvatar": "avatar.png",
          "online": true
        }
      }
    ],
    "total": 10,
    "size": 20,
    "current": 1
  },
  "message": "success"
}
```

#### 4.1.3 获取历史消息

| 属性 | 值 |
|------|-----|
| **URL** | `GET /api/im/messages/{conversationId}` |
| **认证** | JWT Token |

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `cursorMsgId` | Long | 否 | - | 游标消息ID（分页起点） |
| `direction` | String | 否 | BACKWARD | 方向（BACKWARD/FORWARD） |
| `pageSize` | Integer | 否 | 20 | 每页数量（最大100） |

**成功响应**（200 OK）：
```json
{
  "code": 0,
  "data": [
    {
      "id": 1234567890123456789,
      "clientMsgId": "msg_1234567890",
      "conversationId": "chat_1001_1002",
      "fromUserId": 1001,
      "toUserId": 1002,
      "content": "Hello, World!",
      "msgType": "TEXT",
      "status": 2,
      "createTime": 1699999999999
    }
  ],
  "message": "success"
}
```

#### 4.1.4 获取WebSocket Token

| 属性 | 值 |
|------|-----|
| **URL** | `GET /api/im/ws/token` |
| **认证** | JWT Token |

**请求参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `deviceId` | String | 否 | - | 设备ID，不传则自动生成 |
| `kickOthers` | Boolean | 否 | false | 是否踢掉其他设备 |

**成功响应**（200 OK）：
```json
{
  "code": 0,
  "data": "{\"token\":\"xxx\",\"userId\":1001,\"deviceId\":\"device001\",\"wsUrl\":\"/ws\",\"expiresAt\":1700000000000}",
  "message": "success"
}
```

### 4.2 WebSocket 消息协议

#### 4.2.1 消息格式

所有 WebSocket 消息采用 JSON 格式，结构如下：

```json
{
  "type": "message_type",
  "data": { ... },
  "timestamp": 1699999999999
}
```

#### 4.2.2 消息类型定义

| 类型 | 方向 | 说明 |
|------|------|------|
| `login` | 客户端→服务端 | 用户登录 |
| `login_ack` | 服务端→客户端 | 登录成功响应 |
| `private_message` | 客户端→服务端 | 发送私聊消息 |
| `message_ack` | 服务端→客户端 | 消息发送成功响应 |
| `message_push` | 服务端→客户端 | 推送新消息 |
| `message_read` | 客户端→服务端 | 消息已读 |
| `read_ack` | 服务端→客户端 | 已读确认 |
| `deliver_ack` | 客户端→服务端 | 消息投递确认 |
| `delivered` | 服务端→客户端 | 消息已投递 |
| `recall` | 客户端→服务端/双向 | 撤回消息 |
| `delete` | 客户端→服务端/双向 | 删除消息 |
| `heartbeat` | 客户端→服务端 | 心跳 |
| `heartbeat_ack` | 服务端→客户端 | 心跳响应 |
| `logout` | 客户端→服务端 | 用户退出 |
| `disconnect` | 客户端→服务端 | 断开连接 |
| `offline_sync` | 服务端→客户端 | 离线消息同步 |
| `conversation_sync` | 服务端→客户端 | 会话同步 |
| `online_status` | 服务端→客户端 | 在线状态变更 |
| `kickout` | 服务端→客户端 | 被踢下线 |
| `error` | 服务端→客户端 | 错误消息 |

#### 4.2.3 登录消息示例

**请求**：
```json
{
  "type": "login",
  "data": {
    "token": "jwt_token_here",
    "deviceId": "device001",
    "kickOthers": false
  }
}
```

**响应**：
```json
{
  "type": "login_ack",
  "data": {
    "userId": 1001,
    "deviceId": "device001",
    "deviceCount": 1,
    "totalUnreadCount": 5
  }
}
```

#### 4.2.4 消息推送示例

```json
{
  "type": "message_push",
  "data": {
    "id": 1234567890123456789,
    "clientMsgId": "msg_1234567890",
    "conversationId": "chat_1001_1002",
    "fromUserId": 1001,
    "toUserId": 1002,
    "content": "Hello, World!",
    "msgType": "TEXT",
    "status": 0,
    "createTime": 1699999999999
  }
}
```

---

## 5. 核心代码实现

### 5.1 消息发送核心逻辑

```java
// ImMessageServiceImpl.java
@Override
@Transactional(rollbackFor = Exception.class)
public ImMessageVO sendPrivateMessage(Long senderId, String senderDeviceId, ImSendMessageRequest request) {
    // 自动生成clientMsgId
    if (StringUtils.isBlank(request.getClientMsgId())) {
        request.setClientMsgId("msg_" + IdUtil.getSnowflakeNextId());
    }
    
    // 验证请求
    validateSendRequest(senderId, request);
    
    // 幂等性检查
    ImPrivateMessage existed = getByClientMsgId(request.getClientMsgId());
    if (existed != null) {
        if (!Objects.equals(existed.getFromUserId(), senderId)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "clientMsgId already exists");
        }
        return toMessageVO(existed);
    }

    // 构建消息实体
    long now = System.currentTimeMillis();
    ImPrivateMessage message = new ImPrivateMessage();
    message.setId(IdUtil.getSnowflakeNextId());
    message.setClientMsgId(request.getClientMsgId());
    message.setConversationId(buildConversationId(senderId, request.getToUserId()));
    message.setFromUserId(senderId);
    message.setToUserId(request.getToUserId());
    message.setContent(request.getContent());
    message.setMsgType(StringUtils.defaultIfBlank(request.getMsgType(), "TEXT"));
    message.setExtra(request.getExtra());
    message.setStatus(STATUS_SENDING);
    message.setCreateTime(now);

    // 保存消息
    save(message);

    // 更新会话
    updateConversationAfterSend(message);
    
    // 构建返回VO
    ImMessageVO messageVO = toMessageVO(message);
    
    // 推送消息给发送者和接收者
    imSessionManager.sendToUser(senderId, ImWsEnvelope.ok(IMMessageType.MESSAGE_MESSAGE_PUSH, messageVO), senderDeviceId);
    imSessionManager.sendToUser(request.getToUserId(), ImWsEnvelope.ok(IMMessageType.MESSAGE_MESSAGE_PUSH, messageVO));
    
    // 推送会话更新
    pushConversationUpdate(senderId, message.getConversationId());
    pushConversationUpdate(request.getToUserId(), message.getConversationId());
    
    return messageVO;
}
```

### 5.2 WebSocket 消息处理

```java
// WebSocketHandler.java
@Override
protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
    JsonNode root = objectMapper.readTree(frame.text());
    String type = root.path("type").asText();
    JsonNode data = root.path("data");
    
    try {
        switch (type) {
            case IMMessageType.MESSAGE_LOGIN -> handleLogin(ctx, data);
            case IMMessageType.MESSAGE_LOGOUT, IMMessageType.MESSAGE_DISCONNECT -> handleLogout(ctx, data);
            case IMMessageType.MESSAGE_HEARTBEAT -> handleHeartbeat(ctx);
            case IMMessageType.MESSAGE_PRIVATE_MESSAGE -> handlePrivateMessage(ctx, data);
            case IMMessageType.MESSAGE_DELIVER_ACK -> handleDeliverAck(ctx, data);
            case IMMessageType.MESSAGE_MESSAGE_READ -> handleRead(ctx, data);
            case IMMessageType.MESSAGE_RECALL -> handleRecall(ctx, data);
            case IMMessageType.MESSAGE_DELETE -> handleDelete(ctx, data);
            default -> imSessionManager.send(ctx.channel(), ImWsEnvelope.error(IMMessageType.MESSAGE_ERROR, ErrorCode.PARAMS_ERROR, "unknown message type"));
        }
    } catch (BusinessException e) {
        imSessionManager.send(ctx.channel(), ImWsEnvelope.error(type, e.getCode(), e.getMessage()));
    }
}

private void handlePrivateMessage(ChannelHandlerContext ctx, JsonNode data) {
    ImWsSession session = requireSession(ctx).orElseThrow();
    ImSendMessageRequest request = convert(data, ImSendMessageRequest.class);
    ImMessageVO messageVO = imMessageService.sendPrivateMessage(session.getUserId(), session.getDeviceId(), request);
    imSessionManager.send(ctx.channel(), ImWsEnvelope.ok(IMMessageType.MESSAGE_MESSAGE_ACK, messageVO));
}
```

### 5.3 会话管理

```java
// ImSessionManager.java
public BindResult bind(Long userId, String token, String deviceId, Channel channel, boolean kickOthers) {
    String actualDeviceId = StringUtils.defaultIfBlank(deviceId, channel.id().asShortText());
    unbind(channel);

    // 用户设备映射
    ConcurrentMap<String, Channel> deviceMap = userChannels.computeIfAbsent(userId, key -> new ConcurrentHashMap<>());
    Channel oldSameDevice = deviceMap.put(actualDeviceId, channel);
    if (oldSameDevice != null && oldSameDevice != channel) {
        closeChannel(oldSameDevice, ImWsEnvelope.ok(IMMessageType.MESSAGE_KICKOUT, "same_device_replaced"));
    }

    // 踢掉其他设备
    if (kickOthers) {
        List<Map.Entry<String, Channel>> channelsToKick = deviceMap.entrySet().stream()
                .filter(entry -> !Objects.equals(entry.getKey(), actualDeviceId))
                .toList();
        for (Map.Entry<String, Channel> entry : channelsToKick) {
            closeChannel(entry.getValue(), ImWsEnvelope.ok(IMMessageType.MESSAGE_KICKOUT, "kicked_by_new_login"));
        }
    }

    // 创建会话
    long now = System.currentTimeMillis();
    ImWsSession session = ImWsSession.builder()
            .userId(userId)
            .token(token)
            .deviceId(actualDeviceId)
            .channelId(channel.id().asLongText())
            .loginTime(now)
            .lastActiveTime(now)
            .build();
    channelSessions.put(channel.id().asLongText(), session);

    // 更新在线状态
    boolean wasOnline = hashSize(userId) > 0;
    touch(userId, actualDeviceId, now);
    return new BindResult(session, !wasOnline, deviceMap.size());
}

public int sendToUser(Long userId, Object payload, String excludeDeviceId) {
    ConcurrentMap<String, Channel> deviceMap = userChannels.getOrDefault(userId, new ConcurrentHashMap<>());
    int count = 0;
    for (Map.Entry<String, Channel> entry : deviceMap.entrySet()) {
        if (Objects.equals(entry.getKey(), excludeDeviceId)) {
            continue;
        }
        if (send(entry.getValue(), payload)) {
            count++;
        }
    }
    return count;
}
```

### 5.4 离线消息同步

```java
// ImMessageServiceImpl.java
@Override
public List<ImMessageVO> listOfflineMessages(Long userId, int limit) {
    int actualLimit = limit <= 0 ? 200 : Math.min(limit, 500);
    List<ImPrivateMessage> messages = list(new LambdaQueryWrapper<ImPrivateMessage>()
            .eq(ImPrivateMessage::getToUserId, userId)
            .eq(ImPrivateMessage::getReceiverDeleted, FLAG_NO)
            .lt(ImPrivateMessage::getStatus, STATUS_DELIVERED)
            .orderByAsc(ImPrivateMessage::getId)
            .last("limit " + actualLimit));
    return messages.stream().map(this::toMessageVO).toList();
}
```

---

## 6. 数据库设计

### 6.1 消息表 (im_private_message)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| `id` | BIGINT | PRIMARY KEY | 消息ID（雪花算法） |
| `client_msg_id` | VARCHAR(64) | UNIQUE, NOT NULL | 客户端消息ID |
| `conversation_id` | VARCHAR(64) | NOT NULL, INDEX | 会话ID |
| `from_user_id` | BIGINT | NOT NULL, INDEX | 发送者ID |
| `to_user_id` | BIGINT | NOT NULL, INDEX | 接收者ID |
| `content` | TEXT | NOT NULL | 消息内容 |
| `msg_type` | VARCHAR(32) | DEFAULT 'TEXT' | 消息类型 |
| `extra` | TEXT | - | 额外数据(JSON) |
| `status` | INT | DEFAULT 0 | 状态(0:发送中,1:已投递,2:已读) |
| `create_time` | BIGINT | NOT NULL | 创建时间 |
| `is_recalled` | INT | DEFAULT 0 | 是否撤回 |
| `recall_time` | BIGINT | - | 撤回时间 |
| `sender_deleted` | INT | DEFAULT 0 | 发送者删除标记 |
| `receiver_deleted` | INT | DEFAULT 0 | 接收者删除标记 |

**索引设计**：
- `idx_client_msg_id` (client_msg_id) - 幂等性检查
- `idx_conversation_id` (conversation_id) - 会话消息查询
- `idx_from_to_user` (from_user_id, to_user_id) - 消息路由
- `idx_to_user_status` (to_user_id, status) - 离线消息查询

### 6.2 会话表 (im_conversation)

| 字段名 | 类型 | 约束 | 说明 |
|--------|------|------|------|
| `id` | BIGINT | PRIMARY KEY | 自增ID |
| `user_id` | BIGINT | NOT NULL, INDEX | 用户ID |
| `peer_user_id` | BIGINT | NOT NULL | 对方用户ID |
| `conversation_id` | VARCHAR(64) | NOT NULL, INDEX | 会话ID |
| `last_msg_id` | BIGINT | - | 最后消息ID |
| `last_msg_content` | VARCHAR(200) | - | 最后消息摘要 |
| `last_msg_time` | BIGINT | - | 最后消息时间 |
| `unread_count` | INT | DEFAULT 0 | 未读计数 |
| `last_read_msg_id` | BIGINT | - | 最后已读消息ID |
| `is_deleted` | INT | DEFAULT 0 | 删除标记 |
| `create_time` | BIGINT | NOT NULL | 创建时间 |
| `update_time` | BIGINT | NOT NULL | 更新时间 |

**索引设计**：
- `idx_user_conversation` (user_id, conversation_id) - 会话查询
- `idx_user_deleted` (user_id, is_deleted) - 用户会话列表

---

## 7. 错误处理策略

### 7.1 错误码定义

| 错误码 | 错误信息 | 说明 |
|--------|----------|------|
| 0 | success | 成功 |
| 40000 | params error | 参数错误 |
| 40001 | not login error | 未登录 |
| 40002 | no auth error | 无权限 |
| 40003 | not found error | 资源不存在 |
| 50000 | operation error | 操作失败 |

### 7.2 异常处理流程

```
客户端请求
    ↓
参数校验失败?
    ├─ 是 → 返回 40000 参数错误
    └─ 否
        ↓
用户认证失败?
    ├─ 是 → 返回 40001 未登录
    └─ 否
        ↓
业务逻辑处理
    ↓
业务异常?
    ├─ 是 → 返回对应业务错误码
    └─ 否
        ↓
数据库操作失败?
    ├─ 是 → 返回 50000 操作失败 + 回滚事务
    └─ 否
        ↓
返回成功响应
```

### 7.3 WebSocket 错误响应格式

```json
{
  "type": "error",
  "data": {
    "code": 40001,
    "message": "token is required"
  }
}
```

---

## 8. 性能优化建议

### 8.1 数据库优化

1. **索引优化**：
   - 为频繁查询的字段创建复合索引
   - 避免索引过多导致写入性能下降
   - 定期分析慢查询日志

2. **读写分离**：
   - 使用MySQL主从复制
   - 读操作路由到从库
   - 写操作路由到主库

3. **分表策略**：
   - 按conversation_id哈希分表
   - 按时间范围分表
   - 预估单表数据量不超过1000万

### 8.2 缓存优化

1. **会话缓存**：
   - 使用Redis缓存用户会话列表
   - 设置合理的过期时间
   - 采用LRU淘汰策略

2. **在线状态缓存**：
   - 使用Redis Hash存储设备在线状态
   - 设置过期时间实现自动离线

3. **消息预热**：
   - 热门会话消息预热到缓存
   - 减少数据库查询压力

### 8.3 连接优化

1. **连接池配置**：
   - 数据库连接池：HikariCP
   - 合理设置最小/最大连接数
   - 配置连接超时和空闲超时

2. **WebSocket优化**：
   - 设置合理的心跳间隔
   - 限制单用户设备数量
   - 定期清理无效连接

### 8.4 异步处理

1. **消息推送异步化**：
   - 使用消息队列解耦发送和推送
   - 支持消息重试和死信队列

2. **会话更新异步化**：
   - 批量更新会话
   - 延迟更新策略

---

## 9. 部署与测试

### 9.1 依赖服务

| 服务 | 版本 | 说明 |
|------|------|------|
| MySQL | 8.0+ | 消息持久化 |
| Redis | 6.0+ | 在线状态管理 |
| Nacos | 2.0+ | 服务发现与配置 |

### 9.2 配置说明

```yaml
# application.yml
server:
  port: 8104
  servlet:
    context-path: /api/im

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bw_db?characterEncoding=utf-8&useSSL=false
    username: root
    password: password
  data:
    redis:
      host: localhost
      port: 6379

netty:
  websocket:
    port: 8081
    path: /ws
    heartbeat:
      reader-idle-time: 30
      writer-idle-time: 30
      all-idle-time: 60
```

### 9.3 启动步骤

1. **启动依赖服务**：
   ```bash
   # 启动MySQL
   docker start mysql

   # 启动Redis
   docker start redis

   # 启动Nacos
   docker start nacos
   ```

2. **启动IM服务**：
   ```bash
   cd bw-im
   mvn spring-boot:run
   ```

3. **验证服务**：
   - REST API: `http://localhost:8104/api/im/conversations`
   - WebSocket: `ws://localhost:8081/ws`

### 9.4 测试方法

#### 9.4.1 单元测试

```java
@Test
void testSendPrivateMessage_Success() {
    ImSendMessageRequest request = new ImSendMessageRequest();
    request.setClientMsgId("msg_test_001");
    request.setToUserId(testPeerUserId);
    request.setContent("Hello, World!");
    request.setMsgType("TEXT");

    ImMessageVO result = imMessageService.sendPrivateMessage(testUserId, null, request);

    assertNotNull(result);
    assertEquals(request.getContent(), result.getContent());
    assertEquals(request.getToUserId(), result.getToUserId());
    assertEquals(STATUS_SENDING, result.getStatus());
    
    // 验证消息已保存
    ImPrivateMessage saved = imPrivateMessageMapper.selectById(result.getId());
    assertNotNull(saved);
}
```

#### 9.4.2 集成测试

| 测试场景 | 步骤 | 预期结果 |
|----------|------|----------|
| 用户A发送消息给用户B | 1. 用户A登录WebSocket<br>2. 用户B登录WebSocket<br>3. A发送消息 | B收到消息推送 |
| 用户离线时接收消息 | 1. 用户A登录<br>2. 用户B离线<br>3. A发送消息<br>4. B登录 | B收到离线消息同步 |
| 消息已读状态同步 | 1. A发送消息给B<br>2. B标记已读 | A收到已读通知 |

#### 9.4.3 性能测试

| 测试指标 | 目标值 | 测试方法 |
|----------|--------|----------|
| 消息发送延迟 | < 100ms | JMeter压测 |
| 消息推送延迟 | < 50ms | WebSocket压测 |
| 并发连接数 | > 10000 | 连接压测 |
| TPS | > 1000 | 消息发送压测 |

---

## 10. 前端实现建议

### 10.1 WebSocket 连接管理

```javascript
class WebSocketManager {
  constructor() {
    this.ws = null;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.reconnectDelay = 1000;
  }

  connect(url, token, deviceId) {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(url);
      
      this.ws.onopen = () => {
        this.reconnectAttempts = 0;
        this.login(token, deviceId);
        resolve();
      };

      this.ws.onmessage = (event) => {
        this.handleMessage(JSON.parse(event.data));
      };

      this.ws.onerror = (error) => {
        console.error('WebSocket error:', error);
        this.attemptReconnect(url, token, deviceId);
      };

      this.ws.onclose = () => {
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
          this.attemptReconnect(url, token, deviceId);
        }
      };
    });
  }

  login(token, deviceId) {
    this.send({
      type: 'login',
      data: { token, deviceId, kickOthers: false }
    });
  }

  send(message) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify({
        ...message,
        timestamp: Date.now()
      }));
    }
  }

  handleMessage(message) {
    switch (message.type) {
      case 'message_push':
        this.onMessagePush(message.data);
        break;
      case 'login_ack':
        this.onLoginAck(message.data);
        break;
      case 'offline_sync':
        this.onOfflineSync(message.data);
        break;
      // ... 其他消息类型
    }
  }

  attemptReconnect(url, token, deviceId) {
    this.reconnectAttempts++;
    setTimeout(() => {
      this.connect(url, token, deviceId);
    }, this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1));
  }
}
```

### 10.2 消息渲染逻辑

```javascript
// 消息列表组件
function MessageList({ messages, currentUserId }) {
  return (
    <div className="message-list">
      {messages.map((message) => (
        <div 
          key={message.id}
          className={`message ${message.fromUserId === currentUserId ? 'sent' : 'received'}`}
        >
          <div className="message-content">
            {message.isRecalled === 1 ? (
              <span className="recalled">[消息已撤回]</span>
            ) : (
              <span>{message.content}</span>
            )}
          </div>
          <div className="message-time">
            {formatTime(message.createTime)}
          </div>
          {message.status === 2 && message.fromUserId === currentUserId && (
            <div className="message-status">已读</div>
          )}
        </div>
      ))}
    </div>
  );
}
```

---

## 11. 安全考虑

### 11.1 认证与授权

1. **JWT Token验证**：WebSocket连接前必须携带有效Token
2. **设备绑定**：每个连接绑定设备ID，防止会话劫持
3. **权限检查**：发送消息前验证发送者身份

### 11.2 消息安全

1. **内容过滤**：过滤敏感内容和恶意脚本
2. **消息加密**：支持端到端加密
3. **防重放攻击**：使用clientMsgId保证幂等性

### 11.3 连接安全

1. **HTTPS/WSS**：生产环境使用加密连接
2. **连接限制**：限制单用户并发连接数
3. **心跳检测**：及时清理异常连接

---

## 附录：核心类与方法速查

| 类名 | 路径 | 主要职责 |
|------|------|----------|
| `ImController` | `bw-im/controller/` | REST API控制层 |
| `WebSocketHandler` | `bw-im/handler/` | WebSocket消息处理 |
| `ImMessageServiceImpl` | `bw-im/service/impl/` | 消息业务逻辑 |
| `ImSessionManager` | `bw-im/support/` | 会话管理 |
| `NettyWebSocketServer` | `bw-im/server/` | WebSocket服务器 |
| `ImPrivateMessage` | `bw-model/entity/` | 消息实体 |
| `ImConversation` | `bw-model/entity/` | 会话实体 |

| 方法名 | 所属类 | 说明 |
|--------|--------|------|
| `sendPrivateMessage` | ImMessageService | 发送私聊消息 |
| `listConversations` | ImMessageService | 获取会话列表 |
| `listHistory` | ImMessageService | 获取历史消息 |
| `markRead` | ImMessageService | 标记消息已读 |
| `recallMessage` | ImMessageService | 撤回消息 |
| `bind` | ImSessionManager | 绑定用户连接 |
| `sendToUser` | ImSessionManager | 推送消息给用户 |
| `listOfflineMessages` | ImMessageService | 获取离线消息 |

---

**文档版本**: v1.0  
**创建时间**: 2024年  
**适用项目**: brillian-world IM服务