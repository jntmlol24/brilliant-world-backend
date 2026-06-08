package com.demo.bwim.service;

import com.demo.bwcommon.common.ErrorCode;
import com.demo.bwcommon.exception.BusinessException;
import com.demo.bwim.mapper.ImConversationMapper;
import com.demo.bwim.mapper.ImPrivateMessageMapper;
import com.demo.bwim.service.impl.ImMessageServiceImpl;
import com.demo.bwim.support.ImSessionManager;
import com.demo.bwmodel.dto.im.*;
import com.demo.bwmodel.entity.ImConversation;
import com.demo.bwmodel.entity.ImPrivateMessage;
import com.demo.bwmodel.vo.ImConversationVO;
import com.demo.bwmodel.vo.ImMessageVO;
import com.demo.bwmodel.vo.ImUnreadCountVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImMessageServiceTest {

    @InjectMocks
    private ImMessageServiceImpl imMessageService;

    @Mock
    private ImConversationMapper imConversationMapper;

    @Mock
    private ImPrivateMessageMapper imPrivateMessageMapper;

    @Mock
    private ImSessionManager imSessionManager;

    @Mock
    private com.demo.bwmodel.rpc.UserServiceRpc userServiceRpc;

    private Long testUserId;
    private Long testPeerUserId;

    @BeforeEach
    void setUp() {
        testUserId = 1001L;
        testPeerUserId = 1002L;
    }

    @Test
    void testGetOrCreateConversation_Success() {
        ImConversationCreateRequest request = new ImConversationCreateRequest();
        request.setPeerUserId(testPeerUserId);

        when(userServiceRpc.getUserById(testPeerUserId)).thenReturn(new com.demo.bwmodel.entity.User());
        when(imConversationMapper.selectOne(any())).thenReturn(null);

        ImConversation savedConversation = createTestConversation(testUserId, testPeerUserId);
        when(imConversationMapper.selectOne(buildConversationQueryWrapper(testUserId))).thenReturn(savedConversation);

        when(userServiceRpc.batchGetUserName(anyList())).thenReturn(java.util.Collections.emptyMap());
        when(userServiceRpc.batchGetUserAvatar(anyList())).thenReturn(java.util.Collections.emptyMap());
        when(imSessionManager.batchOnlineStatus(anyList())).thenReturn(java.util.Collections.emptyMap());

        ImConversationVO result = imMessageService.getOrCreateConversation(testUserId, request);

        assertNotNull(result);
        assertEquals(testPeerUserId, result.getPeerUserId());
    }

    @Test
    void testGetOrCreateConversation_SelfConversation() {
        ImConversationCreateRequest request = new ImConversationCreateRequest();
        request.setPeerUserId(testUserId);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.getOrCreateConversation(testUserId, request);
        });

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testGetOrCreateConversation_UserNotFound() {
        ImConversationCreateRequest request = new ImConversationCreateRequest();
        request.setPeerUserId(testPeerUserId);

        when(userServiceRpc.getUserById(testPeerUserId)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.getOrCreateConversation(testUserId, request);
        });

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testGetOrCreateConversation_InvalidRequest() {
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.getOrCreateConversation(testUserId, null);
        });

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testGetConversationDetail_Success() {
        ImConversationDetailRequest request = new ImConversationDetailRequest();
        request.setConversationId("chat_1001_1002");
        request.setIncludeMessages(false);

        ImConversation conversation = createTestConversation(testUserId, testPeerUserId);
        when(imConversationMapper.selectOne(any())).thenReturn(conversation);

        when(userServiceRpc.batchGetUserName(anyList())).thenReturn(java.util.Collections.emptyMap());
        when(userServiceRpc.batchGetUserAvatar(anyList())).thenReturn(java.util.Collections.emptyMap());
        when(imSessionManager.batchOnlineStatus(anyList())).thenReturn(java.util.Collections.emptyMap());

        ImConversationVO result = imMessageService.getConversationDetail(testUserId, request);

        assertNotNull(result);
        assertEquals(request.getConversationId(), result.getConversationId());
    }

    @Test
    void testGetConversationDetail_NotFound() {
        ImConversationDetailRequest request = new ImConversationDetailRequest();
        request.setConversationId("chat_1001_1002");

        when(imConversationMapper.selectOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.getConversationDetail(testUserId, request);
        });

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testGetConversationDetail_IncludeMessages() {
        ImConversationDetailRequest request = new ImConversationDetailRequest();
        request.setConversationId("chat_1001_1002");
        request.setIncludeMessages(true);
        request.setMessageLimit(10);

        ImConversation conversation = createTestConversation(testUserId, testPeerUserId);
        when(imConversationMapper.selectOne(any())).thenReturn(conversation);

        when(userServiceRpc.batchGetUserName(anyList())).thenReturn(java.util.Collections.emptyMap());
        when(userServiceRpc.batchGetUserAvatar(anyList())).thenReturn(java.util.Collections.emptyMap());
        when(imSessionManager.batchOnlineStatus(anyList())).thenReturn(java.util.Collections.emptyMap());

        List<ImPrivateMessage> messages = new ArrayList<>();
        ImPrivateMessage message = createTestMessage(testUserId, testPeerUserId);
        messages.add(message);

        when(imPrivateMessageMapper.selectList(any())).thenReturn(messages);

        ImConversationVO result = imMessageService.getConversationDetail(testUserId, request);

        assertNotNull(result);
        assertNotNull(result.getRecentMessages());
    }

    @Test
    void testMarkAllRead_Success() {
        List<ImConversation> conversations = new ArrayList<>();
        ImConversation conversation = createTestConversation(testUserId, testPeerUserId);
        conversation.setUnreadCount(5);
        conversations.add(conversation);

        when(imConversationMapper.selectList(any())).thenReturn(conversations);
        when(imConversationMapper.selectOne(any())).thenReturn(conversation);
        when(imPrivateMessageMapper.selectList(any())).thenReturn(new ArrayList<>());
        when(imConversationMapper.updateById(any(ImConversation.class))).thenReturn(1);

        ImUnreadCountVO result = imMessageService.markAllRead(testUserId);

        assertNotNull(result);
        verify(imSessionManager).sendToUser(eq(testUserId), any());
    }

    @Test
    void testMarkAllRead_InvalidUserId() {
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.markAllRead(null);
        });

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testListMessages_Success() {
        ImMessageQueryRequest request = new ImMessageQueryRequest();
        request.setConversationId("chat_1001_1002");
        request.setPageSize(20);

        List<ImPrivateMessage> messages = new ArrayList<>();
        messages.add(createTestMessage(testUserId, testPeerUserId));

        when(imPrivateMessageMapper.selectList(any())).thenReturn(messages);

        List<ImMessageVO> result = imMessageService.listMessages(testUserId, request);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testListMessages_InvalidConversationId() {
        ImMessageQueryRequest request = new ImMessageQueryRequest();

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.listMessages(testUserId, request);
        });

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testGetUnreadCountByConversations_Success() {
        List<ImConversation> conversations = new ArrayList<>();
        ImConversation conv1 = new ImConversation();
        conv1.setConversationId("chat_1001_1002");
        conv1.setUnreadCount(5);
        conversations.add(conv1);

        ImConversation conv2 = new ImConversation();
        conv2.setConversationId("chat_1001_1003");
        conv2.setUnreadCount(3);
        conversations.add(conv2);

        when(imConversationMapper.selectList(any())).thenReturn(conversations);

        java.util.Map<String, Object> result = imMessageService.getUnreadCountByConversations(testUserId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(8, result.get("totalUnreadCount"));
    }

    @Test
    void testGetUnreadCountByConversations_InvalidUserId() {
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.getUnreadCountByConversations(null);
        });

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testSearchUsers_Success() {
        ImSearchUserRequest request = new ImSearchUserRequest();
        request.setKeyword("test");
        request.setPageSize(20);
        request.setCurrent(1);

        List<com.demo.bwmodel.entity.User> users = new ArrayList<>();
        com.demo.bwmodel.entity.User user = new com.demo.bwmodel.entity.User();
        user.setId(testPeerUserId);
        user.setUserAccount("testuser");
        users.add(user);

        when(userServiceRpc.listUserByPage(any(), anyInt(), anyInt())).thenReturn(users);
        when(userServiceRpc.batchGetUserName(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, "testuser"));
        when(userServiceRpc.batchGetUserAvatar(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, "avatar.png"));
        when(imSessionManager.batchOnlineStatus(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, true));

        List<com.demo.bwmodel.vo.ImUserInfoVO> result = imMessageService.searchUsers(testUserId, request);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testSearchUsers_EmptyKeyword() {
        ImSearchUserRequest request = new ImSearchUserRequest();
        request.setKeyword("");

        List<com.demo.bwmodel.vo.ImUserInfoVO> result = imMessageService.searchUsers(testUserId, request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchUsers_InvalidUserId() {
        ImSearchUserRequest request = new ImSearchUserRequest();
        request.setKeyword("test");

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.searchUsers(null, request);
        });

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testSearchUsers_PartialMatch() {
        ImSearchUserRequest request = new ImSearchUserRequest();
        request.setKeyword("user");
        request.setPageSize(20);
        request.setCurrent(1);
        request.setFuzzySearch(true);

        List<com.demo.bwmodel.entity.User> users = new ArrayList<>();
        com.demo.bwmodel.entity.User user = new com.demo.bwmodel.entity.User();
        user.setId(testPeerUserId);
        user.setUserAccount("testuser");
        users.add(user);

        when(userServiceRpc.listUserByPage(any(), anyInt(), anyInt())).thenReturn(users);
        when(userServiceRpc.batchGetUserName(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, "testuser"));
        when(userServiceRpc.batchGetUserAvatar(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, "avatar.png"));
        when(imSessionManager.batchOnlineStatus(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, true));

        List<com.demo.bwmodel.vo.ImUserInfoVO> result = imMessageService.searchUsers(testUserId, request);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("testuser", result.get(0).getUserAccount());
    }

    @Test
    void testSearchUsers_CaseInsensitive() {
        ImSearchUserRequest request = new ImSearchUserRequest();
        request.setKeyword("TEST");
        request.setPageSize(20);
        request.setCurrent(1);
        request.setFuzzySearch(true);

        List<com.demo.bwmodel.entity.User> users = new ArrayList<>();
        com.demo.bwmodel.entity.User user = new com.demo.bwmodel.entity.User();
        user.setId(testPeerUserId);
        user.setUserAccount("testuser");
        users.add(user);

        when(userServiceRpc.listUserByPage(any(), anyInt(), anyInt())).thenReturn(users);
        when(userServiceRpc.batchGetUserName(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, "testuser"));
        when(userServiceRpc.batchGetUserAvatar(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, "avatar.png"));
        when(imSessionManager.batchOnlineStatus(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, true));

        List<com.demo.bwmodel.vo.ImUserInfoVO> result = imMessageService.searchUsers(testUserId, request);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("testuser", result.get(0).getUserAccount());
    }

    @Test
    void testSearchUsers_NonExistentKeyword() {
        ImSearchUserRequest request = new ImSearchUserRequest();
        request.setKeyword("nonexistentuser123");
        request.setPageSize(20);
        request.setCurrent(1);
        request.setFuzzySearch(true);

        when(userServiceRpc.listUserByPage(any(), anyInt(), anyInt())).thenReturn(java.util.Collections.emptyList());

        List<com.demo.bwmodel.vo.ImUserInfoVO> result = imMessageService.searchUsers(testUserId, request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchUsers_KeywordTooLong() {
        ImSearchUserRequest request = new ImSearchUserRequest();
        StringBuilder longKeyword = new StringBuilder();
        for (int i = 0; i < 70; i++) {
            longKeyword.append("a");
        }
        request.setKeyword(longKeyword.toString());
        request.setPageSize(20);
        request.setCurrent(1);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.searchUsers(testUserId, request);
        });

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("64"));
    }

    @Test
    void testSearchUsers_ExcludesCurrentUser() {
        ImSearchUserRequest request = new ImSearchUserRequest();
        request.setKeyword("test");
        request.setPageSize(20);
        request.setCurrent(1);
        request.setFuzzySearch(true);

        List<com.demo.bwmodel.entity.User> users = new ArrayList<>();
        com.demo.bwmodel.entity.User user1 = new com.demo.bwmodel.entity.User();
        user1.setId(testUserId);
        user1.setUserAccount("testuser1");
        users.add(user1);
        
        com.demo.bwmodel.entity.User user2 = new com.demo.bwmodel.entity.User();
        user2.setId(testPeerUserId);
        user2.setUserAccount("testuser2");
        users.add(user2);

        when(userServiceRpc.listUserByPage(any(), anyInt(), anyInt())).thenReturn(users);
        when(userServiceRpc.batchGetUserName(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, "testuser2"));
        when(userServiceRpc.batchGetUserAvatar(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, "avatar.png"));
        when(imSessionManager.batchOnlineStatus(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, true));

        List<com.demo.bwmodel.vo.ImUserInfoVO> result = imMessageService.searchUsers(testUserId, request);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testPeerUserId, result.get(0).getUserId());
        assertEquals("testuser2", result.get(0).getUserAccount());
    }

    @Test
    void testSearchUsers_WithWhitespace() {
        ImSearchUserRequest request = new ImSearchUserRequest();
        request.setKeyword("  test  ");
        request.setPageSize(20);
        request.setCurrent(1);
        request.setFuzzySearch(true);

        List<com.demo.bwmodel.entity.User> users = new ArrayList<>();
        com.demo.bwmodel.entity.User user = new com.demo.bwmodel.entity.User();
        user.setId(testPeerUserId);
        user.setUserAccount("testuser");
        users.add(user);

        when(userServiceRpc.listUserByPage(any(), anyInt(), anyInt())).thenReturn(users);
        when(userServiceRpc.batchGetUserName(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, "testuser"));
        when(userServiceRpc.batchGetUserAvatar(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, "avatar.png"));
        when(imSessionManager.batchOnlineStatus(anyList())).thenReturn(java.util.Collections.singletonMap(testPeerUserId, true));

        List<com.demo.bwmodel.vo.ImUserInfoVO> result = imMessageService.searchUsers(testUserId, request);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void testGenerateWsToken_Success() {
        ImWsTokenRequest request = new ImWsTokenRequest();
        request.setDeviceId("device123");

        com.demo.bwmodel.entity.User user = new com.demo.bwmodel.entity.User();
        user.setId(testUserId);

        when(userServiceRpc.getUserById(testUserId)).thenReturn(user);

        String result = imMessageService.generateWsToken(testUserId, request);

        assertNotNull(result);
        assertTrue(result.contains("token"));
        assertTrue(result.contains("userId"));
    }

    @Test
    void testGenerateWsToken_InvalidUserId() {
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.generateWsToken(null, null);
        });

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testGenerateWsToken_UserNotFound() {
        ImWsTokenRequest request = new ImWsTokenRequest();
        when(userServiceRpc.getUserById(testUserId)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.generateWsToken(testUserId, request);
        });

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testSendPrivateMessage_Success() {
        ImSendMessageRequest request = new ImSendMessageRequest();
        request.setClientMsgId("msg_test_123");
        request.setToUserId(testPeerUserId);
        request.setContent("Hello, World!");
        request.setMsgType("TEXT");

        com.demo.bwmodel.entity.User peerUser = new com.demo.bwmodel.entity.User();
        peerUser.setId(testPeerUserId);

        when(userServiceRpc.getUserById(testPeerUserId)).thenReturn(peerUser);
        when(imPrivateMessageMapper.selectOne(any())).thenReturn(null);
        when(imPrivateMessageMapper.insert(any(ImPrivateMessage.class))).thenReturn(1);
        when(imConversationMapper.selectOne(any())).thenReturn(null);

        ImMessageVO result = imMessageService.sendPrivateMessage(testUserId, null, request);

        assertNotNull(result);
        assertEquals("Hello, World!", result.getContent());
        assertEquals(testPeerUserId, result.getToUserId());
        verify(imPrivateMessageMapper).insert(any(ImPrivateMessage.class));
    }

    @Test
    void testSendPrivateMessage_NullClientMsgId_AutoGenerate() {
        ImSendMessageRequest request = new ImSendMessageRequest();
        request.setClientMsgId(null);
        request.setToUserId(testPeerUserId);
        request.setContent("Hello, World!");
        request.setMsgType("TEXT");

        com.demo.bwmodel.entity.User peerUser = new com.demo.bwmodel.entity.User();
        peerUser.setId(testPeerUserId);

        when(userServiceRpc.getUserById(testPeerUserId)).thenReturn(peerUser);
        when(imPrivateMessageMapper.selectOne(any())).thenReturn(null);
        when(imPrivateMessageMapper.insert(any(ImPrivateMessage.class))).thenReturn(1);
        when(imConversationMapper.selectOne(any())).thenReturn(null);

        ImMessageVO result = imMessageService.sendPrivateMessage(testUserId, null, request);

        assertNotNull(result);
        assertNotNull(request.getClientMsgId());
        assertTrue(request.getClientMsgId().startsWith("msg_"));
        verify(imPrivateMessageMapper).insert(any(ImPrivateMessage.class));
    }

    @Test
    void testSendPrivateMessage_EmptyClientMsgId_AutoGenerate() {
        ImSendMessageRequest request = new ImSendMessageRequest();
        request.setClientMsgId("");
        request.setToUserId(testPeerUserId);
        request.setContent("Hello, World!");
        request.setMsgType("TEXT");

        com.demo.bwmodel.entity.User peerUser = new com.demo.bwmodel.entity.User();
        peerUser.setId(testPeerUserId);

        when(userServiceRpc.getUserById(testPeerUserId)).thenReturn(peerUser);
        when(imPrivateMessageMapper.selectOne(any())).thenReturn(null);
        when(imPrivateMessageMapper.insert(any(ImPrivateMessage.class))).thenReturn(1);
        when(imConversationMapper.selectOne(any())).thenReturn(null);

        ImMessageVO result = imMessageService.sendPrivateMessage(testUserId, null, request);

        assertNotNull(result);
        assertNotNull(request.getClientMsgId());
        assertTrue(request.getClientMsgId().startsWith("msg_"));
        verify(imPrivateMessageMapper).insert(any(ImPrivateMessage.class));
    }

    @Test
    void testSendPrivateMessage_InvalidToUserId() {
        ImSendMessageRequest request = new ImSendMessageRequest();
        request.setClientMsgId("msg_test_123");
        request.setToUserId(null);
        request.setContent("Hello, World!");

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.sendPrivateMessage(testUserId, null, request);
        });

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testSendPrivateMessage_BlankContent() {
        ImSendMessageRequest request = new ImSendMessageRequest();
        request.setClientMsgId("msg_test_123");
        request.setToUserId(testPeerUserId);
        request.setContent("   ");

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.sendPrivateMessage(testUserId, null, request);
        });

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void testSendPrivateMessage_ReceiverNotFound() {
        ImSendMessageRequest request = new ImSendMessageRequest();
        request.setClientMsgId("msg_test_123");
        request.setToUserId(testPeerUserId);
        request.setContent("Hello, World!");

        when(userServiceRpc.getUserById(testPeerUserId)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            imMessageService.sendPrivateMessage(testUserId, null, request);
        });

        assertEquals(ErrorCode.NOT_FOUND_ERROR.getCode(), exception.getCode());
    }

    private ImConversation createTestConversation(Long userId, Long peerUserId) {
        ImConversation conversation = new ImConversation();
        conversation.setId(1L);
        conversation.setUserId(userId);
        conversation.setPeerUserId(peerUserId);
        conversation.setConversationId("chat_" + Math.min(userId, peerUserId) + "_" + Math.max(userId, peerUserId));
        conversation.setUnreadCount(0);
        conversation.setLastMsgTime(System.currentTimeMillis());
        conversation.setIsDeleted(0);
        return conversation;
    }

    private ImPrivateMessage createTestMessage(Long fromUserId, Long toUserId) {
        ImPrivateMessage message = new ImPrivateMessage();
        message.setId(1L);
        message.setFromUserId(fromUserId);
        message.setToUserId(toUserId);
        message.setConversationId("chat_" + Math.min(fromUserId, toUserId) + "_" + Math.max(fromUserId, toUserId));
        message.setContent("Test message");
        message.setMsgType("TEXT");
        message.setStatus(0);
        message.setCreateTime(System.currentTimeMillis());
        message.setIsRecalled(0);
        message.setSenderDeleted(0);
        message.setReceiverDeleted(0);
        return message;
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ImConversation> buildConversationQueryWrapper(Long userId) {
        return any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
    }
}
