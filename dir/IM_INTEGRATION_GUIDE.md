# 聊天功能集成指南

## 功能概述

本系统实现了完整的即时通讯（IM）功能，包括：

1. **导航栏聊天入口** - 全局聊天按钮，支持未读消息红点提示
2. **聊天列表** - 显示所有会话，支持搜索和实时更新
3. **聊天详情** - 消息发送、接收、状态显示
4. **未读消息系统** - 全局红点 + 精细化未读数显示（≤99显示数字，>99显示"99+"）
5. **私聊功能** - 帖子页面直接发起与作者的私聊
6. **WebSocket实时通信** - 基于WebSocket的消息实时同步
7. **断线重连** - 自动重连机制，确保连接稳定

## 文件结构

```
src/
├── api/
│   └── im.ts                 # IM API接口层
├── components/
│   ├── AppHeader.vue         # 导航栏（含聊天按钮）
│   ├── ChatPanel.vue         # 聊天面板整合组件
│   ├── ChatSidebar.vue       # 聊天侧边栏
│   └── ChatWindow.vue        # 聊天窗口
├── pages/
│   ├── ChatPage.vue          # 独立聊天页面
│   └── ArticleViewPage.vue   # 文章详情页（含私聊按钮）
├── store/
│   └── chatStore.ts          # 聊天状态管理
├── types/
│   └── im.ts                 # IM相关类型定义
├── utils/
│   └── websocket.ts          # WebSocket管理器
└── App.vue                   # 应用入口（含聊天面板）
```

## API接口规范

### REST API端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/im/conversations` | GET | 获取会话列表 |
| `/im/conversations` | POST | 创建或获取会话 |
| `/im/conversations/:id` | GET | 获取会话详情 |
| `/im/messages/:conversationId` | GET | 获取消息列表 |
| `/im/messages/send` | POST | 发送消息 |
| `/im/messages/read/:conversationId` | POST | 标记消息已读 |
| `/im/messages/read/all` | POST | 标记所有消息已读 |
| `/im/messages/unread/count` | GET | 获取未读消息数 |
| `/im/users/search` | GET | 搜索用户 |
| `/im/ws/token` | GET | 获取WebSocket认证令牌 |

### WebSocket协议

**连接地址**: `ws://domain/api/im/ws?userId={userId}`

**消息格式**:
```json
{
  "type": "chat|ack|read|typing|system|ping|pong",
  "data": {},
  "timestamp": 1234567890,
  "messageId": "msg_xxx"
}
```

**消息类型说明**:

- `chat`: 聊天消息
- `ack`: 消息送达确认
- `read`: 消息已读确认
- `typing`: 正在输入状态
- `system`: 系统消息
- `ping`: 心跳检测
- `pong`: 心跳响应

## 使用方法

### 1. 全局聊天面板

聊天面板已集成到 `App.vue` 中，自动显示在页面右侧。

**主要功能**:
- 点击导航栏聊天图标打开侧边栏
- 显示会话列表和未读消息数
- 点击会话打开聊天窗口
- 支持会话搜索

### 2. 在文章页面发起私聊

在 `ArticleViewPage` 中，帖子的作者信息会自动显示"私聊作者"按钮：

```vue
<button 
  v-if="canStartChat"
  class="interaction-btn chat-btn"
  @click="startPrivateChat"
>
  <span>私聊作者</span>
</button>
```

**条件**:
- 用户已登录
- 帖子有作者信息
- 作者不是当前登录用户

### 3. 独立聊天页面

访问 `/chat` 路由可进入完整的聊天页面。

**功能**:
- 左右分栏布局（会话列表 + 聊天窗口）
- 响应式设计，适配移动端
- 支持刷新会话列表

### 4. 自定义聊天组件

如需在其他页面集成聊天功能：

```vue
<template>
  <button @click="openChat">打开聊天</button>
  <ChatPanel ref="chatPanelRef" />
</template>

<script setup>
import { ref } from 'vue'
import ChatPanel from '@/components/ChatPanel.vue'

const chatPanelRef = ref(null)

const openChat = () => {
  chatPanelRef.value?.openSidebar()
}
</script>
```

## 未读消息处理

### 全局未读提示

在 `AppHeader` 中，未读消息总数显示在聊天图标上：

```vue
<span 
  v-if="chatStore.state.totalUnreadCount > 0" 
  class="unread-indicator"
>
  {{ chatStore.state.totalUnreadCount > 99 ? '99+' : chatStore.state.totalUnreadCount }}
</span>
```

### 会话未读数

在 `ChatSidebar` 中，每个会话显示对应的未读数：

```vue
<span 
  v-if="conversation.unreadCount > 0" 
  class="unread-badge"
>
  {{ conversation.unreadCount > 99 ? '99+' : conversation.unreadCount }}
</span>
```

### 标记已读

进入聊天窗口时自动标记该会话已读：

```typescript
watch(() => props.isOpen, (newVal) => {
  if (newVal && chatStore.state.currentConversation) {
    chatStore.markAsRead(chatStore.state.currentConversation.id)
  }
})
```

## WebSocket配置

### 环境变量

创建 `.env` 文件：

```bash
VITE_WS_URL=wss://your-domain.com/api/im/ws
```

### 连接管理

WebSocket管理器 (`websocket.ts`) 提供：

- **自动连接**: 用户登录后自动建立连接
- **自动重连**: 连接断开后自动重试（最多5次）
- **心跳检测**: 30秒发送一次心跳包
- **消息队列**: 断线期间的消息自动缓存，连接恢复后发送

### 自定义配置

```typescript
const wsManager = new WebSocketManager({
  autoReconnect: true,           // 自动重连
  reconnectInterval: 3000,       // 重连间隔（ms）
  maxReconnectAttempts: 5,        // 最大重连次数
  heartbeatInterval: 30000,       // 心跳间隔（ms）
  heartbeatTimeout: 10000         // 心跳超时（ms）
})
```

## 状态管理

聊天状态通过 `chatStore` 统一管理：

```typescript
const chatStore = useChatStore()

// 状态
chatStore.state.conversations      // 会话列表
chatStore.state.currentConversation // 当前会话
chatStore.state.messages            // 消息字典
chatStore.state.totalUnreadCount    // 未读总数
chatStore.state.isConnected         // 连接状态

// 方法
chatStore.loadConversations()       // 加载会话列表
chatStore.loadMessages(id)          // 加载消息
chatStore.sendMessage(userId, content) // 发送消息
chatStore.markAsRead(conversationId) // 标记已读
chatStore.getOrCreateConversation(userId) // 创建会话
```

## 错误处理

### 网络异常

```typescript
// API请求错误
try {
  await chatStore.sendMessage(userId, content)
} catch (error: any) {
  alert(error.message || '发送失败')
}

// WebSocket错误
wsManager.on('error', (error) => {
  console.error('WebSocket错误:', error)
})

wsManager.on('reconnectFailed', () => {
  alert('连接失败，请检查网络')
})
```

### 连接中断

WebSocket管理器会自动处理：

1. 检测连接断开
2. 启动重连流程
3. 最多重试5次
4. 超过最大次数后停止并通知

### 用户体验优化

- 消息发送失败时显示重发按钮
- 连接断开时显示离线状态
- 加载中显示loading动画
- 空状态显示友好提示

## 响应式设计

### 桌面端

- 侧边栏宽度: 380px
- 聊天窗口宽度: 480px
- 完整页面宽度: 1400px

### 移动端

- 全屏显示
- 左右滑动切换会话列表和聊天窗口
- 触摸友好的按钮尺寸（最小44x44px）

### 断点

```css
/* 平板 */
@media (max-width: 1024px) {
  .chat-sidebar { width: 300px; }
}

/* 手机 */
@media (max-width: 640px) {
  .chat-sidebar { width: 100%; right: -100%; }
  .chat-window { width: 100%; right: -100%; }
}
```

## 性能优化

1. **消息懒加载**: 进入聊天窗口时加载最新消息
2. **滚动优化**: 使用 `nextTick` 确保DOM更新后再滚动
3. **虚拟列表**: 消息列表过长时考虑使用虚拟滚动
4. **WebSocket优化**: 
   - 消息压缩
   - 心跳间隔合理设置
   - 批量消息处理

## 安全考虑

1. **身份验证**: WebSocket连接需要携带用户Token
2. **消息加密**: 敏感内容建议使用HTTPS/WSS传输
3. **输入验证**: 前端对输入内容进行基本过滤
4. **XSS防护**: 消息内容HTML转义

## 浏览器兼容性

- Chrome/Edge 90+
- Firefox 88+
- Safari 14+
- 不支持IE浏览器

## 后续扩展

### 可添加功能

1. **消息类型**: 图片、文件、表情
2. **消息撤回**: 支持撤回一定时间内的消息
3. **消息引用**: 引用回复功能
4. **在线状态**: 显示用户实时在线状态
5. **消息提醒**: 浏览器通知
6. **历史记录**: 支持加载更早的消息
7. **群聊功能**: 支持群组聊天
8. **已读回执**: 显示消息是否被对方阅读

### 性能提升

1. **消息分页**: 按时间分页加载历史消息
2. **增量同步**: 只同步新消息，而非全量拉取
3. **本地缓存**: 使用IndexedDB缓存消息
4. **CDN加速**: 静态资源使用CDN

## 常见问题

### Q: WebSocket连接失败？

A: 检查：
1. 后端WebSocket服务是否启动
2. 网络是否正常
3. CORS配置是否正确
4. 浏览器控制台是否有错误信息

### Q: 消息发送失败？

A: 检查：
1. 网络连接是否正常
2. 对方是否在线
3. 消息内容是否符合要求
4. 查看控制台错误信息

### Q: 未读数不准确？

A: 可能原因：
1. 多设备登录导致状态不同步
2. WebSocket消息处理延迟
3. 后端计数逻辑问题

建议刷新页面或联系后端排查。

## 技术栈

- **框架**: Vue 3 (Composition API)
- **语言**: TypeScript
- **路由**: Vue Router 4
- **状态**: Vue 3 Reactive
- **HTTP**: Axios
- **WebSocket**: 原生WebSocket API
- **样式**: CSS3 (Scoped Styles)

## 许可证

本项目遵循项目本身的许可证。
