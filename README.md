# Brillian-World

**基于 Spring Cloud 微服务架构的社区即时通讯（IM）与内容平台后端**

面向用户社区、帖子互动、即时消息、WebSocket 实时推送与可观测链路的一体化后端服务工程实践。

---

## 项目简介

Brillian-World 是一套基于 Spring Boot 3 + Spring Cloud + Dubbo 的微服务后端系统，核心围绕社区内容管理与即时通讯两大能力构建。

它将单体业务拆解为清晰的服务边界，以网关统一入口、Nacos 统一注册发现、Dubbo 完成跨服务调用，并通过自研 Netty WebSocket 引擎承载百万级长连接场景下的实时消息推送。

从代码结构看，Brillian-World 以 **bw-im**（IM服务）、**bw-user**（用户服务）、**bw-post**（帖子服务）三个核心微服务为主体，配合 **bw-gateway**（网关）对外暴露统一 API，共同构成一个可维护、可扩展、可观测的社区后端系统。

---

## 设计目标

1. **微服务化与职责清晰**
   将用户、帖子、IM 拆分为独立微服务，服务间通过 Dubbo RPC 通信，边界明确，独立部署。

2. **高性能实时通信**
   基于 Netty 构建 WebSocket 服务器，支持长连接管理与消息推送，替代传统 Spring WebSocket 方案。

3. **统一的即时通讯协议**
   定义 22 种标准消息类型，涵盖登录认证、私聊、已读/送达回执、撤回、删除、心跳、踢出等完整 IM 生命周期。

4. **用户状态持续沉淀**
   通过内存 Map + Redis 双写机制管理用户在线会话，支持多设备登录、同设备替换、跨设备踢出。

5. **柔性的消息推送链路**
   通过全链路日志标记（`[WS-HANDSHAKE]` → `[WS-PUSH-SENDING]` → `[WS-PUSH-SENT]`）追踪每条消息的完整生命周期。

6. **统一的 API 规范与文档**
   使用 Knife4j / Swagger 自动生成接口文档，统一 BaseResponse 响应格式与错误码体系。

---

## 核心能力

### 1. 用户服务（bw-user）

提供完整的用户生命周期管理：

- 用户注册与登录（支持密码 MD5 加密 + JWT Token）
- 用户信息查询与分页
- 用户信息修改与管理
- Token 刷新、校验与登出
- 管理员权限控制（`@AuthCheck` 注解切面）

**Dubbo RPC 暴露**：通过 `UserServiceRpcImpl` 将用户查询、Token 校验、批量获取用户信息等能力暴露给其他微服务。

对应接口：

```
POST   /api/user/register          # 用户注册
POST   /api/user/login             # 用户登录（返回用户信息）
POST   /api/user/login/rpc         # 用户登录（返回 token）
POST   /api/user/logout            # 用户注销
GET    /api/user/get/login         # 获取当前登录用户
POST   /api/user/add               # 创建用户（管理员）
GET    /api/user/get               # 根据 ID 获取用户
GET    /api/user/list/page         # 分页查询用户列表
POST   /api/user/delete            # 删除用户
POST   /api/user/update            # 更新用户
POST   /api/user/update/my         # 更新当前用户信息
```

---

### 2. 帖子服务（bw-post）

提供社区内容的核心 CRUD 及互动能力：

- 帖子创建、编辑、删除（逻辑删除）、分页查询
- 帖子评论（支持一级评论 + 二级回复）
- 帖子点赞/取消点赞
- 帖子收藏/取消收藏
- 帖子与评论的用户信息自动填充（通过 Dubbo 调用 User 服务）
- ElasticSearch 集成（PostEsDTO 预留）

**Dubbo RPC 暴露**：通过 `PostServiceRpcImpl`、`PostCommentRpcImpl` 对外提供帖子查询与评论统计。

对应接口：

```
POST   /api/post/add               # 创建帖子
POST   /api/post/delete            # 删除帖子
POST   /api/post/update            # 更新帖子（管理员）
GET    /api/post/get/vo            # 根据 ID 获取帖子 VO
POST   /api/post/list/page/vo      # 分页查询帖子列表 VO
POST   /api/post/search/page/vo    # ES 搜索帖子

# 帖子评论
POST   /api/post/comment/add       # 添加评论
POST   /api/post/comment/delete    # 删除评论
GET    /api/post/comment/list/page # 分页查询评论列表

# 帖子点赞
POST   /api/post/thumb/add         # 点赞
POST   /api/post/thumb/delete      # 取消点赞

# 帖子收藏
POST   /api/post/favour/add        # 收藏
POST   /api/post/favour/delete     # 取消收藏
POST   /api/post/favour/list/page  # 分页查询收藏列表
```

---

### 3. 即时通讯服务（bw-im）

IM 服务是系统的核心模块，提供完整的私聊消息处理与实时推送能力。

#### REST API（用于 HTTP 通道的消息操作）

```
GET    /api/im/conversations           # 分页查询会话列表
POST   /api/im/conversations           # 创建或获取会话
GET    /api/im/conversations/{id}      # 会话详情（含最近消息）
POST   /api/im/conversation/list       # 会话列表（POST 变体）
GET    /api/im/messages/{id}           # 游标分页查询消息
POST   /api/im/message/history         # 历史消息查询
POST   /api/im/messages/send           # 发送消息
POST   /api/im/messages/read/{id}      # 标记会话已读
POST   /api/im/messages/read/all       # 全部标记已读
GET    /api/im/messages/unread/count   # 未读消息数
POST   /api/im/message/unread/clear    # 清除未读
POST   /api/im/message/recall          # 撤回消息（2分钟窗口）
POST   /api/im/message/delete          # 删除消息
GET    /api/im/message/get             # 获取单条消息
GET    /api/im/message/unread          # 未读详情
POST   /api/im/online/status           # 批量查询在线状态
GET    /api/im/users/search            # 搜索用户
GET    /api/im/ws/token                # 获取 WebSocket 连接 Token
```

#### WebSocket 实时通信（Netty，端口 8081）

WebSocket 服务器基于 Netty 自研，提供独立的长连接通道用于实时消息推送：

**连接建立**：
```
ws://host:8081/ws?token=xxx&deviceId=xxx
```

**消息协议格式**（JSON）：

发送消息：
```json
{
  "type": "private_message",
  "data": {
    "clientMsgId": "msg_xxx",
    "toUserId": 123456,
    "content": "你好",
    "msgType": "TEXT",
    "extra": null
  }
}
```

接收推送：
```json
{
  "type": "message_push",
  "code": 0,
  "message": "ok",
  "timestamp": 1714000000000,
  "data": {
    "id": 123,
    "conversationId": "chat_1001_1002",
    "fromUserId": 1001,
    "toUserId": 1002,
    "content": "你好",
    "status": 1
  }
}
```

**支持的消息类型**（定义于 `IMMessageType.java`）：

| 类型 | 值 | 方向 | 说明 |
|------|-----|------|------|
| `MESSAGE_LOGIN` | `login` | Client → Server | 用户登录认证 |
| `MESSAGE_LOGIN_ACK` | `login_ack` | Server → Client | 登录确认 |
| `MESSAGE_PRIVATE_MESSAGE` | `private_message` / `chat` | Client → Server | 发送私聊消息 |
| `MESSAGE_MESSAGE_ACK` | `message_ack` | Server → Client | 消息已接收确认 |
| `MESSAGE_MESSAGE_PUSH` | `message_push` | Server → Client | 消息实时推送 |
| `MESSAGE_DELIVER_ACK` | `deliver_ack` | Client → Server | 送达回执 |
| `MESSAGE_DELIVERED` | `delivered` | Server → Client | 消息已送达 |
| `MESSAGE_MESSAGE_READ` | `message_read` | Client → Server | 已读回执 |
| `MESSAGE_READ_ACK` | `read_ack` | Server → Client | 已读确认 |
| `MESSAGE_RECALL` | `recall` | 双向 | 撤回消息 |
| `MESSAGE_DELETE` | `delete` | Client → Server | 删除消息 |
| `MESSAGE_SYNC_OFFLINE` | `offline_sync` | Server → Client | 离线消息同步 |
| `MESSAGE_CONVERSATION_SYNC` | `conversation_sync` | Server → Client | 会话更新同步 |
| `MESSAGE_ONLINE_STATUS` | `online_status` | Server → Client | 在线状态变更通知 |
| `MESSAGE_KICKOUT` | `kickout` | Server → Client | 被踢出 |
| `MESSAGE_ERROR` | `error` | Server → Client | 错误响应 |
| `MESSAGE_HEARTBEAT` | `heartbeat` | Client → Server | 心跳 |
| `MESSAGE_HEARTBEAT_ACK` | `heartbeat_ack` | Server → Client | 心跳响应 |
| `MESSAGE_LOGOUT` | `logout` | Client → Server | 主动登出 |
| `MESSAGE_DISCONNECT` | `disconnect` | Client → Server | 断开连接 |
| `MESSAGE_DISCONNECT_ACK` | `disconnect_ack` | Server → Client | 断开确认 |

#### 消息推送生命周期

```
用户A发送消息
  │
  ├─① WebSocket / HTTP 接收消息
  │    └─ WebSocketHandler.handlePrivateMessage()  → 日志 [WS-PUSH-BEGIN]
  │
  ├─② ImMessageServiceImpl.sendPrivateMessage()
  │    ├─ 校验请求参数（clientMsgId、toUserId、content）
  │    ├─ 幂等检查（按 clientMsgId 去重）
  │    ├─ 持久化到 MySQL（im_private_message 表）  → 日志 [MSG-SAVE]
  │    ├─ 更新会话（im_conversation 表）
  │    └─ 触发推送                              → 日志 [MSG-PUSH-TRIGGER]
  │
  ├─③ MessagePushServiceImpl.pushToUser()
  │    └─ 调用 ImSessionManager.sendToUser()     → 日志 [MSG-PUSH]
  │
  ├─④ ImSessionManager.sendToUser()
  │    ├─ 从 userChannels 查找目标用户的所有在线 Channel
  │    ├─ 对每个 Channel 发送 TextWebSocketFrame → 日志 [WS-PUSH-SENDING]
  │    └─ 发送结果                              → 日志 [WS-PUSH-SENT]
  │
  └─⑤ 前端 onmessage 事件触发，解析消息并更新 DOM
```

#### 会话管理机制

- **双重存储**：内存 `ConcurrentHashMap`（快速查找）+ Redis Hash（跨实例在线状态）
- **多设备支持**：同一用户可在多个设备同时在线，按 `userId:deviceId` 管理 Channel
- **设备替换**：同一 deviceId 的旧 Channel 自动关闭
- **强制下线**：支持 `kickOthers` 参数踢出其他所有设备
- **心跳超时**：仅 READER_IDLE（30s 无客户端消息）或 ALL_IDLE（60s 完全无活动）触发踢出

#### 消息状态流转

```
SENDING(0) ──→ DELIVERED(1) ──→ READ(2)
  │                │               │
  │ 消息已存储       │ 接收者确认收到  │ 接收者已读
```

#### 离线消息机制

用户登录时自动拉取 `status < DELIVERED` 的未送达消息（最多 200 条），通过 `offline_sync` 消息类型推送给用户。

---

### 4. API 网关（bw-gateway）

基于 Spring Cloud Gateway 的统一入口：

- 路由分发到各微服务（用户 / 帖子 / IM）
- WebSocket 升级协议无缝转发（`lb:ws://bw-im`）
- 全局 CORS 跨域配置
- WebSocket 转发日志追踪（`WebSocketGatewayLogFilter`）

**路由配置**：
```yaml
routes:
  - id: bw-user       → lb://bw-user     (Path=/api/user/**)
  - id: bw-post       → lb://bw-post     (Path=/api/post/**)
  - id: bw-im         → lb://bw-im       (Path=/api/im/**)
  - id: bw-im-token   → lb://bw-im       (Path=/api/im/ws/token)
  - id: bw-websocket  → lb:ws://bw-im    (Path=/ws/**)
```

---

## 系统架构

```
                    ┌──────────────────────────────┐
                    │          Client / App         │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │  Spring Cloud Gateway (:8101)│
                    │  ├─ HTTP 路由                │
                    │  ├─ WebSocket 升级转发        │
                    │  └─ CORS / 日志过滤          │
                    └──────┬───────────┬───────────┘
                           │           │
              ┌────────────┼───────────┼──────────────┐
              │            │           │              │
              ▼            ▼           ▼              │
    ┌──────────────┐ ┌──────────┐ ┌──────────┐       │
    │  bw-user     │ │ bw-post  │ │  bw-im   │       │
    │  (:8102)     │ │ (:8103)  │ │ (:8104)  │       │
    │              │ │          │ │          │       │
    │ UserService  │ │PostCrud  │ │ ImMsg    │       │
    │ Dubbo RPC    │ │Comment   │ │ Netty WS │       │
    │ (提供者)      │ │Favour    │ │ (:8081)  │       │
    │              │ │Thumb     │ │ Session  │       │
    └──────┬───────┘ └────┬─────┘ └────┬─────┘       │
           │              │            │              │
           └──────────────┼────────────┘              │
                          │ Dubbo RPC                 │
                          ▼                           │
                 ┌────────────────┐                   │
                 │  Nacos (:8848) │                   │
                 │  服务注册与发现  │                   │
                 └────────────────┘                   │
                          │                           │
              ┌───────────┼───────────┐               │
              ▼           ▼           ▼               │
        ┌──────────┐ ┌──────────┐ ┌──────────┐       │
        │  MySQL   │ │  Redis   │ │ Elastic  │       │
        │  bw_db   │ │ (:6379)  │ │ Search   │       │
        └──────────┘ └──────────┘ └──────────┘       │
                                                     │
        Knife4j / Swagger / WebSocket Log Filter     │
```

---

## 项目结构

```
brillian-world/
├── pom.xml                     # 父 POM（依赖管理、模块聚合）
├── mysql/
│   ├── create_table.sql        # 建库建表 SQL
│   └── add_user_account_index.sql  # 用户索引
│
├── bw-common/                  # 公共模块
│   └── src/main/java/com/demo/bwcommon/
│       ├── annotation/         # @AuthCheck 权限注解
│       ├── common/             # BaseResponse / ErrorCode / ResultUtils / PageRequest
│       ├── config/             # MyBatis-Plus / JSON / 自动填充配置
│       ├── constant/           # CommonConstant / UserConstant / FileConstant / IMMessageType
│       ├── exception/          # BusinessException / GlobalExceptionHandler / ThrowUtils
│       └── utils/              # JwtUtil / SqlUtils / NetUtils
│
├── bw-model/                   # 数据模型模块
│   └── src/main/java/com/demo/bwmodel/
│       ├── entity/             # User / Post / PostComment / PostThumb / PostFavour
│       │                       # ImConversation / ImPrivateMessage
│       ├── dto/                # 请求 DTO（user/ post/ im/ file/ 等子包）
│       ├── vo/                 # 视图对象（UserVO / PostVO / ImMessageVO 等）
│       ├── enums/              # UserRoleEnum / FileUploadBizEnum
│       └── rpc/                # Dubbo RPC 接口定义（UserServiceRpc / PostServiceRpc 等）
│
├── bw-user/                    # 用户微服务 (:8102)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/demo/bwuser/
│       │   ├── BwUserApplication.java        # 启动类
│       │   ├── controller/UserController.java # REST API
│       │   ├── service/UserService.java       # 服务接口
│       │   ├── service/impl/UserServiceImpl.java
│       │   ├── mapper/UserMapper.java
│       │   ├── rpc/UserServiceRpcImpl.java    # Dubbo 服务暴露
│       │   └── config/CorsConfig.java
│       └── resources/application.yml
│
├── bw-post/                    # 帖子微服务 (:8103)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/demo/bwpost/
│       │   ├── BwPostApplication.java
│       │   ├── controller/      # PostController / PostCommentController
│       │   │                    # PostFavourController / PostThumbController
│       │   ├── service/         # Post/Comment/Favour/Thumb 服务接口与实现
│       │   ├── mapper/          # Post/Comment/Favour/Thumb Mapper
│       │   └── rpc/             # Dubbo 服务暴露（PostServiceRpcImpl / PostCommentRpcImpl）
│       └── resources/application.yml
│
├── bw-im/                      # 即时通讯微服务 (:8104 HTTP, :8081 WebSocket)
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/demo/bwim/
│       │   ├── BwImApplication.java
│       │   ├── controller/ImController.java        # REST API（16 个端点）
│       │   ├── handler/
│       │   │   ├── WebSocketHandler.java            # Netty 业务消息分发器
│       │   │   └── WebSocketHandshakeHandler.java   # WebSocket 握手拦截器
│       │   ├── server/NettyWebSocketServer.java     # Netty WebSocket 服务器
│       │   ├── support/
│       │   │   ├── ImSessionManager.java            # 会话管理器（内存+Redis）
│       │   │   ├── ImWsSession.java                 # 会话模型
│       │   │   ├── ImWsEnvelope.java                # WebSocket 消息信封
│       │   │   └── ImAuthHelper.java                # HTTP 认证助手
│       │   ├── service/
│       │   │   ├── ImMessageService.java            # 消息服务接口
│       │   │   ├── MessagePushService.java          # 推送服务接口
│       │   │   └── impl/
│       │   │       ├── ImMessageServiceImpl.java    # 核心消息逻辑（877行）
│       │   │       └── MessagePushServiceImpl.java  # 推送实现
│       │   └── mapper/      # ImConversationMapper / ImPrivateMessageMapper
│       └── resources/application.yml
│
└── bw-gateway/                 # API 网关 (:8101)
    ├── pom.xml
    └── src/main/
        ├── java/com/demo/bwgateway/
        │   ├── BwGatewayApplication.java
        │   ├── config/
        │   │   ├── CorsConfig.java                   # WebFlux CORS
        │   │   └── WebSocketGatewayLogFilter.java     # WS 转发日志
        │   └── controller/Health.java                # 健康检查
        └── resources/application.yml
```

---

## 技术栈

### 后端框架
- Java 17+
- Spring Boot 3.5.13
- Spring Cloud Gateway 2025.0.1
- Spring Cloud Alibaba 2025.0.0.0

### 微服务基础设施
- Apache Dubbo 3.3.0（服务调用）
- Nacos（注册中心 + 配置中心）
- Spring Cloud LoadBalancer

### 数据存储
- MySQL 8.x（主数据库，MyBatis-Plus 3.5.13 ORM）
- Redis（会话缓存 + 在线状态）
- Elasticsearch（帖子搜索，已集成 Starter）

### 即时通讯
- Netty 4.1.132.Final（WebSocket 服务器）
- 自定义消息协议（22 种消息类型）
- 自研会话管理器（内存 Map + Redis Hash 双写）

### API 文档
- Knife4j 4.4.0（OpenAPI 3 / Swagger）

### 工具库
- Hutool 5.8.32
- Lombok
- Apache Commons Lang3 / Collections4
- Jackson（JSON 序列化）
- JJWT 0.11.5（JWT 令牌）

---

## 运行环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.x
- Redis 6+
- Nacos 2.x（注册中心，默认 8848 端口）
- Elasticsearch（可选，帖子搜索使用）

---

## 快速开始

### 1. 克隆项目

```bash
git clone <repository-url>
cd brillian-world
```

### 2. 初始化数据库

执行 SQL 脚本创建数据库和表结构：

```bash
mysql -u root -p < mysql/create_table.sql
mysql -u root -p < mysql/add_user_account_index.sql
```

### 3. 启动 Nacos

```bash
# 或使用已有的 Nacos 实例
startup.cmd -m standalone
```

### 4. 启动 Redis

确保 Redis 服务运行在 `localhost:6379`，密码为 `aa123123`（可在各模块 `application.yml` 中修改）。

### 5. 启动微服务（按顺序）

```bash
# 1. 网关
cd bw-gateway
mvn spring-boot:run

# 2. 用户服务
cd bw-user
mvn spring-boot:run

# 3. 帖子服务
cd bw-post
mvn spring-boot:run

# 4. IM 服务
cd bw-im
mvn spring-boot:run
```

### 6. 验证服务状态

```bash
# 健康检查
curl http://localhost:8101/health/health

# 期望响应
{"status": "ok"}
```

**服务端口表**：

| 服务 | HTTP 端口 | 说明 |
|------|----------|------|
| bw-gateway | 8101 | API 统一入口 |
| bw-user | 8102 | /api/user |
| bw-post | 8103 | /api/post |
| bw-im | 8104 | /api/im |
| bw-im (Netty WS) | 8081 | WebSocket 长连接 |

### 7. 访问 API 文档

- Swagger UI: `http://localhost:8101/doc.html`
- OpenAPI JSON: `http://localhost:8101/v3/api-docs`

---

## 配置说明

各微服务配置集中在 `application.yml` 中，主要配置模块包括：

### 数据源配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/bw_db?characterEncoding=utf-8&useSSL=false
    username: root
    password: root
```

### Redis 配置
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: aa123123
      database: 1
```

### Nacos 配置
```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 127.0.0.1:8848
```

### Dubbo 配置
```yaml
dubbo:
  application:
    name: bw-im-service
  protocol:
    name: dubbo
    port: -1
  registry:
    address: nacos://127.0.0.1:8848
  consumer:
    check: false
    timeout: 30000
    retries: 0
```

### Netty WebSocket 配置（仅 bw-im）
```yaml
netty:
  websocket:
    port: 8081
    path: /ws
    heartbeat:
      reader-idle-time: 30   # 读空闲超时（秒）
      writer-idle-time: 30   # 写空闲超时（秒）
      all-idle-time: 60      # 全空闲超时（秒）
```

---

## 数据库模型

### ER 概要

```
user ───── 1:N ───── post
  │                    │
  │                    ├── 1:N ── post_comment
  │                    ├── 1:N ── post_thumb
  │                    └── 1:N ── post_favour
  │
  ├── 1:N ── im_private_message (from_user_id)
  ├── 1:N ── im_private_message (to_user_id)
  └── 1:N ── im_conversation
```

### 关键表

| 表名 | 用途 | 关键索引 |
|------|------|---------|
| `user` | 用户账户 | 逻辑删除 `isDelete` |
| `post` | 帖子内容 | `idx_userId` |
| `post_comment` | 帖子评论（两级嵌套） | `idx_postId`, `idx_userId`, `idx_parentId` |
| `post_thumb` | 点赞记录（硬删除） | `idx_postId`, `idx_userId` |
| `post_favour` | 收藏记录（硬删除） | `idx_postId`, `idx_userId` |
| `im_private_message` | 私聊消息 | `uk_client_msg_id`（幂等）, `idx_conversation_time`, `idx_from_user`, `idx_to_user`, `idx_status` |
| `im_conversation` | 用户会话（每人一条） | 唯一键 `uk_user_conversation(user_id, conversation_id)`, `idx_peerUserId` |

---

## 可观测性

项目通过结构化日志实现请求与推送链路的全链路追踪：

### 日志标签体系

| 标签 | 节点 | 说明 |
|------|------|------|
| `[WS-SERVER]` | NettyWebSocketServer | 服务器启动/停止、Channel 初始化 |
| `[WS-HANDSHAKE]` | WebSocketHandshakeHandler | 握手请求、Token 提取 |
| `[WS-CONNECTED]` | WebSocketHandshakeHandler | 握手完成确认 |
| `[WS-DISCONNECTED]` | WebSocketHandshakeHandler | 连接断开 |
| `[WS-MSG-RECV]` | WebSocketHandler | 收到客户端消息 |
| `[WS-LOGIN]` | WebSocketHandler | 用户登录认证 |
| `[WS-LOGOUT]` | WebSocketHandler | 用户登出 |
| `[WS-PUSH-BEGIN]` | WebSocketHandler | 消息推送开始 |
| `[WS-PUSH-ACK]` | WebSocketHandler | 消息接收确认 |
| `[WS-IDLE-TIMEOUT]` | WebSocketHandler | 空闲超时踢出 |
| `[WS-EXCEPTION]` | WebSocketHandler | 异常捕获 |
| `[SESSION-BIND]` | ImSessionManager | 会话绑定/替换/踢出 |
| `[SESSION-UNBIND]` | ImSessionManager | 会话解绑 |
| `[WS-PUSH-USER]` | ImSessionManager | 目标用户离线 |
| `[WS-PUSH-SENDING]` | ImSessionManager | 消息发送中 |
| `[WS-PUSH-SENT]` | ImSessionManager | 消息发送成功 |
| `[WS-PUSH-FAIL]` | ImSessionManager | 消息发送失败 |
| `[WS-CLOSE]` | ImSessionManager | Channel 关闭 |
| `[MSG-PUSH]` | MessagePushServiceImpl | 推送触发 |
| `[MSG-SAVE]` | ImMessageServiceImpl | 消息持久化 |
| `[MSG-PUSH-TRIGGER]` | ImMessageServiceImpl | 推送触发入口 |
| `[MSG-SEND-COMPLETE]` | ImMessageServiceImpl | 消息完整流程结束 |
| `[MSG-DELIVERED-PUSH]` | ImMessageServiceImpl | 送达回执推送 |
| `[MSG-READ-PUSH]` | ImMessageServiceImpl | 已读回执推送 |
| `[MSG-RECALL-PUSH]` | ImMessageServiceImpl | 撤回通知推送 |
| `[MSG-DELETE-PUSH]` | ImMessageServiceImpl | 删除通知推送 |
| `[MSG-ONLINE-STATUS]` | ImMessageServiceImpl | 在线状态广播 |
| `[GW-WS-FORWARD]` | WebSocketGatewayLogFilter | 网关 WebSocket 转发 |
| `[GW-WS-COMPLETE]` | WebSocketGatewayLogFilter | 网关转发完成 |
| `[GW-WS-ERROR]` | WebSocketGatewayLogFilter | 网关转发异常 |

### 推送链路示例

```
[WS-HANDSHAKE]    channel=75af469e, remote=/127.0.0.1:62769
[WS-CONNECTED]    channel=75af469e, uri=/ws
[WS-LOGIN]        channel=75af469e, userId=123456, deviceId=device_xxx
[SESSION-BIND]    userId=123456, deviceId=device_xxx, firstOnline=true
[WS-PUSH-BEGIN]   channel=62e5f7ae, senderUserId=111, toUserId=123456
[MSG-SAVE]        msgId=2049407330873356288, sender=111, toUser=123456
[MSG-PUSH-TRIGGER] msgId=2049407330873356288, pushing to both users
[MSG-PUSH]        pushing to user, targetUserId=123456
[WS-PUSH-SENDING] userId=123456, channel=75af469e
[WS-PUSH-SENT]    userId=123456, channel=75af469e
[MSG-SEND-COMPLETE] msgId=2049407330873356288
```

---

## 消息推送链路（WebSocket）

参考 `dir/tmp.txt` 中定义的推送链路标准：

```
用户A浏览器 ──HTTP/WS──→ 网关 ──→ IM服务 ──→ 消息存储(DB/Redis)
                                    │
                                    │ (推送)
                                    ▼
用户B浏览器 ←──WS── 网关 ←── IM服务 (找到B的WebSocket会话)
```

**标准实现步骤**：

| 步骤 | 组件 | 实现 |
|------|------|------|
| 1 | 浏览器 | `new WebSocket("ws://host:8081/ws?token=xxx")` 建立持久连接 |
| 2 | 网关 | Spring Cloud Gateway 路由 `lb:ws://bw-im` 升级 WebSocket 协议 |
| 3 | IM 服务 | WebSocketHandshakeHandler 提取 Token → WebSocketHandler.handleLogin() 完成认证 → ImSessionManager.bind() 将 userId 与 Channel 绑定入内存 Map + Redis |
| 4 | 用户 A 发送 | HTTP POST `/api/im/messages/send` 或 WebSocket `type=private_message` |
| 5 | IM 服务处理 | ImMessageServiceImpl.sendPrivateMessage()：① 校验消息 ② 存储 MySQL ③ 触发推送 |
| 6 | 推送逻辑 | ImSessionManager.sendToUser() 从 userChannels 取出 B 的 Channel → writeAndFlush TextWebSocketFrame |
| 7 | 用户 B 浏览器 | onmessage 事件触发 → 解析 JSON → 更新 DOM（无需刷新） |

---

## 错误处理体系

所有模块统一使用 `GlobalExceptionHandler`（`@RestControllerAdvice`）处理异常：

| 错误码 | 含义 | 使用场景 |
|--------|------|---------|
| 0 (SUCCESS) | 成功 | 正常响应 |
| 40000 (PARAMS_ERROR) | 参数错误 | 请求参数校验失败 |
| 40100 (NOT_LOGIN_ERROR) | 未登录 | Token 无效或未传递 |
| 40101 (NO_AUTH_ERROR) | 无权限 | 非资源拥有者或非管理员 |
| 40400 (NOT_FOUND_ERROR) | 不存在 | 资源未找到 |
| 40300 (FORBIDDEN_ERROR) | 禁止访问 | 封禁用户 |
| 50000 (SYSTEM_ERROR) | 系统错误 | 未预期的内部异常 |
| 50001 (OPERATION_ERROR) | 操作失败 | 业务逻辑异常 |

**WebSocket 错误处理**：
- 业务异常（BusinessException）：返回 `{"type":"error","code":xxx,"message":"..."}` 
- 未知消息类型：返回 `type=error, message="unknown message type"`
- 未认证连接：返回 `type=error, code=40100, message="connection not authenticated"`
- 序列化异常：记录日志并返回 false（不关闭连接）
- 网络异常（exceptionCaught）：记录日志并关闭 Channel

---

## 安全特性

- **JWT Token 认证**：HMAC-SHA 算法，7 天过期，支持刷新
- **权限注解**：`@AuthCheck(mustRole = "admin")` 方法级权限校验
- **SQL 注入防护**：SqlUtils 关键字/特殊字符检测 + MyBatis-Plus 参数化查询
- **逻辑删除**：User / Post / PostComment 使用 `isDelete` 字段软删除
- **密码加密**：UserServiceImpl 使用 MD5 对密码加密存储
- **CORS 防护**：网关层与各服务层双层跨域配置
- **ID 安全**：Jackson Long 自动序列化为 String 防止前端精度丢失

---

## Docker 部署

各模块均可通过 Maven 打包为独立 JAR 并容器化部署：

```bash
# 打包
mvn clean package -DskipTests

# 运行（以 bw-gateway 为例）
java -jar bw-gateway/target/bw-gateway-0.0.1-SNAPSHOT.jar
```

---

## 项目亮点

- **双通道 IM 架构**：HTTP REST API + Netty WebSocket 双通道，兼顾 RESTful 操作与实时推送
- **自研 WebSocket 引擎**：完整替换 Spring WebSocket，支持自定义握手、心跳、会话管理
- **完善的消息协议**：22 种标准消息类型，涵盖完整的 IM 生命周期
- **多层会话管理**：内存 Map（高性能）+ Redis Hash（跨实例共享），支持多设备、设备替换、强制下线
- **全链路日志追踪**：每个推送环节均有带时间戳的结构化日志标记
- **微服务化清晰**：用户、帖子、IM 三服务边界明确，通过 Dubbo RPC 解耦
- **消息去重与幂等**：基于 clientMsgId 唯一索引的消息去重机制
- **双人会话模型**：每人维护独立的会话记录，相互隔离的未读计数与删除状态
- **离线消息补偿**：登录时自动拉取未送达消息，通过 WebSocket 异步同步
- **网关透明转发**：Spring Cloud Gateway 原生支持 WebSocket 升级协议路由

---

## 日志文件说明

`dir/tmp.txt` 为运行日志样例，记录了一次完整的消息推送链路：
- 消息持久化（[MSG-SAVE]）
- 双人推送（[WS-PUSH-SENDING] → [WS-PUSH-SENT]）
- 会话同步（[MSG-SEND-COMPLETE]）
- 心跳超时踢出（[WS-IDLE-TIMEOUT]）
- 连接断开与重连（[WS-DISCONNECTED] → [WS-LOGIN]）

该文件用于验证消息推送链路的正确性与调试异常场景。

---

## 致谢

Brillian-World 面向社区即时通讯场景，尝试把微服务治理、Dubbo RPC、Netty 实时通信、MySQL 持久化与 Redis 缓存整合成一个清晰、可落地、可迭代的工程框架。

如果你正在构建下一代社区或 IM 系统，希望这套工程结构能为你提供一个良好的起点。
