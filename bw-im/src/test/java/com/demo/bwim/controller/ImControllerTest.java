package com.demo.bwim.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.bwcommon.common.BaseResponse;
import com.demo.bwim.service.ImMessageService;
import com.demo.bwim.support.ImAuthHelper;
import com.demo.bwmodel.dto.im.*;
import com.demo.bwmodel.vo.ImConversationVO;
import com.demo.bwmodel.vo.ImMessageVO;
import com.demo.bwmodel.vo.ImUnreadCountVO;
import com.demo.bwmodel.vo.ImUserInfoVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ImController.class)
class ImControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ImMessageService imMessageService;

    @MockBean
    private ImAuthHelper imAuthHelper;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpServletRequest mockRequest;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        testUserId = 1001L;
        mockRequest = new MockHttpServletRequest();
        when(imAuthHelper.requireUserId(any())).thenReturn(testUserId);
    }

    @Test
    void testGetConversations() throws Exception {
        Page<ImConversationVO> page = new Page<>();
        List<ImConversationVO> conversations = new ArrayList<>();
        ImConversationVO vo = new ImConversationVO();
        vo.setConversationId("chat_1001_1002");
        vo.setPeerUserId(1002L);
        conversations.add(vo);
        page.setRecords(conversations);

        when(imMessageService.listConversations(eq(testUserId), any())).thenReturn(page);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/conversations")
                        .param("current", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isArray())
                .andExpect(jsonPath("$.data.records[0].conversationId").value("chat_1001_1002"));
    }

    @Test
    void testCreateConversation() throws Exception {
        ImConversationCreateRequest request = new ImConversationCreateRequest();
        request.setPeerUserId(1002L);

        ImConversationVO vo = new ImConversationVO();
        vo.setConversationId("chat_1001_1002");
        vo.setPeerUserId(1002L);

        when(imMessageService.getOrCreateConversation(eq(testUserId), any())).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/im/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value("chat_1001_1002"))
                .andExpect(jsonPath("$.data.peerUserId").value(1002));
    }

    @Test
    void testGetConversationDetail() throws Exception {
        ImConversationVO vo = new ImConversationVO();
        vo.setConversationId("chat_1001_1002");
        vo.setPeerUserId(1002L);

        when(imMessageService.getConversationDetail(eq(testUserId), any())).thenReturn(vo);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/conversations/chat_1001_1002")
                        .param("includeMessages", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value("chat_1001_1002"));
    }

    @Test
    void testGetMessages() throws Exception {
        List<ImMessageVO> messages = new ArrayList<>();
        ImMessageVO message = new ImMessageVO();
        message.setId(1L);
        message.setContent("Test message");
        messages.add(message);

        when(imMessageService.listMessages(eq(testUserId), any())).thenReturn(messages);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/messages/chat_1001_1002")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].content").value("Test message"));
    }

    @Test
    void testSendMessage() throws Exception {
        ImSendMessageRequest request = new ImSendMessageRequest();
        request.setToUserId(1002L);
        request.setContent("Hello");
        request.setMsgType("TEXT");
        request.setClientMsgId("msg_123");

        ImMessageVO messageVO = new ImMessageVO();
        messageVO.setId(1L);
        messageVO.setFromUserId(testUserId);
        messageVO.setToUserId(1002L);
        messageVO.setContent("Hello");

        when(imMessageService.sendPrivateMessage(eq(testUserId), isNull(), any())).thenReturn(messageVO);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/im/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Hello"));
    }

    @Test
    void testSendMessage_WithoutClientMsgId() throws Exception {
        ImSendMessageRequest request = new ImSendMessageRequest();
        request.setToUserId(1002L);
        request.setContent("Hello");
        request.setMsgType("TEXT");

        ImMessageVO messageVO = new ImMessageVO();
        messageVO.setId(1L);
        messageVO.setFromUserId(testUserId);
        messageVO.setToUserId(1002L);
        messageVO.setContent("Hello");

        when(imMessageService.sendPrivateMessage(eq(testUserId), isNull(), any())).thenReturn(messageVO);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/im/messages/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Hello"));
    }

    @Test
    void testMarkAsRead() throws Exception {
        ImMessageReadRequest request = new ImMessageReadRequest();
        request.setMessageId(1L);

        ImUnreadCountVO unreadCountVO = new ImUnreadCountVO();
        unreadCountVO.setUnreadCount(0);

        when(imMessageService.markRead(eq(testUserId), any())).thenReturn(unreadCountVO);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/im/messages/read/chat_1001_1002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void testMarkAllAsRead() throws Exception {
        ImUnreadCountVO unreadCountVO = new ImUnreadCountVO();
        unreadCountVO.setTotalUnreadCount(0);

        when(imMessageService.markAllRead(testUserId)).thenReturn(unreadCountVO);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/im/messages/read/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUnreadCount").value(0));
    }

    @Test
    void testGetUnreadCount_SpecificConversation() throws Exception {
        ImUnreadCountVO unreadCountVO = new ImUnreadCountVO();
        unreadCountVO.setConversationId("chat_1001_1002");
        unreadCountVO.setUnreadCount(5);

        when(imMessageService.getUnreadCount(testUserId, "chat_1001_1002")).thenReturn(unreadCountVO);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/messages/unread/count")
                        .param("conversationId", "chat_1001_1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(5));
    }

    @Test
    void testGetUnreadCount_AllConversations() throws Exception {
        Map<String, Object> unreadMap = new HashMap<>();
        unreadMap.put("conversations", Map.of("chat_1001_1002", 5, "chat_1001_1003", 3));
        unreadMap.put("totalUnreadCount", 8);
        unreadMap.put("conversationCount", 2);

        when(imMessageService.getUnreadCountByConversations(testUserId)).thenReturn(unreadMap);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/messages/unread/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUnreadCount").value(8));
    }

    @Test
    void testSearchUsers() throws Exception {
        List<ImUserInfoVO> users = new ArrayList<>();
        ImUserInfoVO user = new ImUserInfoVO();
        user.setUserId(1002L);
        user.setUserAccount("testuser");
        user.setOnline(true);
        users.add(user);

        when(imMessageService.searchUsers(eq(testUserId), any())).thenReturn(users);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/users/search")
                        .param("keyword", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userAccount").value("testuser"));
    }

    @Test
    void testSearchUsers_PartialMatch() throws Exception {
        List<ImUserInfoVO> users = new ArrayList<>();
        ImUserInfoVO user = new ImUserInfoVO();
        user.setUserId(1002L);
        user.setUserAccount("testuser");
        user.setOnline(true);
        users.add(user);

        when(imMessageService.searchUsers(eq(testUserId), any())).thenReturn(users);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/users/search")
                        .param("keyword", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userAccount").value("testuser"));
    }

    @Test
    void testSearchUsers_CaseInsensitive() throws Exception {
        List<ImUserInfoVO> users = new ArrayList<>();
        ImUserInfoVO user = new ImUserInfoVO();
        user.setUserId(1002L);
        user.setUserAccount("testuser");
        user.setOnline(true);
        users.add(user);

        when(imMessageService.searchUsers(eq(testUserId), any())).thenReturn(users);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/users/search")
                        .param("keyword", "TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userAccount").value("testuser"));
    }

    @Test
    void testSearchUsers_EmptyResult() throws Exception {
        when(imMessageService.searchUsers(eq(testUserId), any())).thenReturn(java.util.Collections.emptyList());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/users/search")
                        .param("keyword", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void testSearchUsers_OptionalKeyword() throws Exception {
        List<ImUserInfoVO> users = new ArrayList<>();
        ImUserInfoVO user = new ImUserInfoVO();
        user.setUserId(1002L);
        user.setUserAccount("testuser");
        user.setOnline(true);
        users.add(user);

        when(imMessageService.searchUsers(eq(testUserId), any())).thenReturn(users);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/users/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void testSearchUsers_WithPagination() throws Exception {
        List<ImUserInfoVO> users = new ArrayList<>();
        ImUserInfoVO user = new ImUserInfoVO();
        user.setUserId(1002L);
        user.setUserAccount("testuser");
        user.setOnline(true);
        users.add(user);

        when(imMessageService.searchUsers(eq(testUserId), any())).thenReturn(users);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/users/search")
                        .param("keyword", "test")
                        .param("pageSize", "10")
                        .param("current", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].userAccount").value("testuser"));
    }

    @Test
    void testGetWsToken() throws Exception {
        String tokenJson = "{\"token\":\"jwt_token\",\"userId\":1001,\"deviceId\":\"device123\"}";

        when(imMessageService.generateWsToken(eq(testUserId), any())).thenReturn(tokenJson);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/ws/token")
                        .param("deviceId", "device123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void testGetMessage() throws Exception {
        ImMessageVO message = new ImMessageVO();
        message.setId(1L);
        message.setContent("Test message");

        when(imMessageService.getMessage(testUserId, 1L)).thenReturn(message);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/im/message/get")
                        .param("messageId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Test message"));
    }

    @Test
    void testListHistory() throws Exception {
        ImHistoryQueryRequest request = new ImHistoryQueryRequest();
        request.setConversationId("chat_1001_1002");
        request.setPageSize(20);

        List<ImMessageVO> messages = new ArrayList<>();
        ImMessageVO message = new ImMessageVO();
        message.setId(1L);
        messages.add(message);

        when(imMessageService.listHistory(eq(testUserId), any())).thenReturn(messages);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/im/message/history")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void testClearUnread() throws Exception {
        ImUnreadClearRequest request = new ImUnreadClearRequest();
        request.setConversationId("chat_1001_1002");

        ImUnreadCountVO unreadCountVO = new ImUnreadCountVO();
        unreadCountVO.setUnreadCount(0);

        when(imMessageService.clearUnread(eq(testUserId), any())).thenReturn(unreadCountVO);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/im/message/unread/clear")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    @Test
    void testListOnlineStatus() throws Exception {
        List<com.demo.bwmodel.vo.ImOnlineStatusVO> statusList = new ArrayList<>();
        com.demo.bwmodel.vo.ImOnlineStatusVO status = new com.demo.bwmodel.vo.ImOnlineStatusVO();
        status.setUserId(1002L);
        status.setOnline(true);
        statusList.add(status);

        when(imMessageService.listOnlineStatus(any())).thenReturn(statusList);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/im/online/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[1002]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].online").value(true));
    }
}
