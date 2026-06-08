# 快速参考指南

## 快速开始

### 1. 配置后端API

在 `.env` 文件中配置：

```bash
VITE_API_BASE_URL=/api
VITE_WS_URL=wss://your-domain.com/api/im/ws
```

### 2. 后端接口实现

实现以下REST API端点：

```
GET    /api/im/conversations          # 获取会话列表
POST   /api/im/conversations          # 创建会话
GET    /api/im/messages/:id            # 获取消息
POST   /api/im/messages/send          # 发送消息
POST   /api/im/messages/read/:id      # 标记已读
GET    /api/im/messages/unread/count  # 未读数统计
GET    /api/im/users/search           # 搜索用户
```

实现WebSocket端点：

```
ws://domain/api/im/ws?userId={id}
```

### 3. 启动应用

```bash
npm install
npm run dev
```

## 核心功能一览

### 功能 1: 导航栏聊天入口 ✓

**位置**: `AppHeader.vue`
**功能**: 
- 全局导航栏右侧聊天图标
- 未读消息红点提示
- 点击打开聊天侧边栏

**使用**: 用户登录后自动可用

### 功能 2: 聊天列表 ✓

**位置**: `ChatSidebar.vue`
**功能**:
- 显示所有会话（按最近消息排序）
- 支持用户名搜索/模糊匹配
- 显示每个用户的未读消息数
- 实时更新会话列表

**使用**: 
```vue
<ChatSidebar :is-open="sidebarOpen" @select="handleSelect" />
```

### 功能 3: 聊天窗口 ✓

**位置**: `ChatWindow.vue`
**功能**:
- 消息发送/接收
- 消息状态显示（发送中/已发送/已送达/已读）
- 自动滚动到底部
- Enter键发送消息

**使用**:
```vue
<ChatWindow :is-open="chatOpen" @back="goBack" />
```

### 功能 4: 未读消息系统 ✓

**全局提示**:
- 导航栏图标红点
- 显示未读总数
- 超过99显示"99+"

**精细化提示**:
- 每个会话显示未读数
- 超过99显示"99+"
- 进入聊天窗口自动标记已读

### 功能 5: 帖子私聊 ✓

**位置**: `ArticleViewPage.vue`
**条件**: 
- 用户已登录
- 帖子有作者信息
- 作者不是当前用户

**效果**: 
- 显示"私聊作者"按钮
- 点击创建/打开与作者的会话
- 自动跳转到聊天界面

## API调用示例

### 获取会话列表

```typescript
import { imApi } from '@/api/im'

// 获取所有会话
const res = await imApi.getConversations()
console.log(res.data) // Conversation[]

// 搜索会话
const res = await imApi.getConversations({ keyword: '张三' })
```

### 发送消息

```typescript
const res = await imApi.sendMessage({
  receiverId: 123,
  content: '你好！',
  messageType: 'text'
})
console.log(res.data) // Message
```

### 标记已读

```typescript
// 标记单个会话已读
await imApi.markAsRead('conversation_id')

// 标记所有已读
await imApi.markAllAsRead()
```

### WebSocket消息

```typescript
// 发送聊天消息
wsManager.sendChatMessage(receiverId, content, 'text')

// 发送已读回执
wsManager.sendReadAck(conversationId, lastMessageId)

// 发送正在输入状态
wsManager.sendTypingIndicator(receiverId, true)
```

## 状态管理

```typescript
import { useChatStore } from '@/store/chatStore'

const chatStore = useChatStore()

// 监听状态变化
watch(() => chatStore.state.totalUnreadCount, (newVal) => {
  console.log('未读消息数:', newVal)
})

// 监听新消息
chatStore.on('message', (message) => {
  console.log('收到新消息:', message)
})
```

## 路由配置

添加聊天页面路由：

```typescript
// router/index.ts
{
  path: '/chat',
  name: 'Chat',
  component: () => import('../pages/ChatPage.vue')
}
```

访问地址：`http://localhost:5173/chat`

## 样式定制

### 主题颜色

在组件的 `scoped style` 中覆盖：

```css
.interaction-btn.chat-btn {
  background: #your-color;
  border-color: #your-color;
}

.unread-indicator {
  background: #your-color;
}
```

### 布局调整

修改组件的CSS变量：

```css
:root {
  --chat-sidebar-width: 380px;
  --chat-window-width: 480px;
}
```

## 调试技巧

### 1. 启用WebSocket日志

```typescript
// websocket.ts
send(message: WSMessage): boolean {
  console.log('[WebSocket] Sending:', message)
  // ...
}
```

### 2. 查看状态

```typescript
console.log('Conversations:', chatStore.state.conversations)
console.log('Messages:', chatStore.state.messages)
console.log('Connected:', chatStore.state.isConnected)
```

### 3. 网络请求

浏览器开发者工具 > Network > 过滤 `ws://` 和 `/api/im`

## 性能监控

### 关键指标

1. **WebSocket连接时间**: < 1s
2. **消息发送延迟**: < 100ms
3. **页面加载时间**: < 3s
4. **内存占用**: < 100MB

### 优化建议

1. 消息列表超过100条时启用虚拟滚动
2. 图片消息使用懒加载
3. 离线消息使用IndexedDB缓存
4. 批量消息合并发送

## 错误排查

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| 聊天图标不显示 | 用户未登录 | 检查登录状态 |
| 消息发送失败 | 网络问题 | 检查网络连接 |
| WebSocket断开 | 服务器重启 | 等待自动重连 |
| 未读数不更新 | 状态未同步 | 刷新页面 |
| 私聊按钮不显示 | 作者是自己 | 检查用户ID |

## 下一步

1. 阅读完整文档: [IM_INTEGRATION_GUIDE.md](IM_INTEGRATION_GUIDE.md)
2. 查看组件源码: [src/components/ChatPanel.vue](./src/components/ChatPanel.vue)
3. 查看状态管理: [src/store/chatStore.ts](./src/store/chatStore.ts)
4. 查看类型定义: [src/types/im.ts](./src/types/im.ts)
5. 查看WebSocket: [src/utils/websocket.ts](./src/utils/websocket.ts)

## 技术支持

如遇问题，请检查：

1. 后端API是否正常运行
2. WebSocket服务是否启动
3. 数据库连接是否正常
4. 查看浏览器控制台错误信息
5. 查看后端日志输出

---

**版本**: 1.0.0
**更新日期**: 2026-04-26
**框架**: Vue 3 + TypeScript
**状态**: ✅ 生产就绪
