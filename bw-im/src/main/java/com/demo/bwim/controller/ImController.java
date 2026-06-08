package com.demo.bwim.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.bwcommon.common.BaseResponse;
import com.demo.bwcommon.common.ResultUtils;
import com.demo.bwim.service.ImMessageService;
import com.demo.bwim.support.ImAuthHelper;
import com.demo.bwmodel.dto.im.*;
import com.demo.bwmodel.vo.ImConversationVO;
import com.demo.bwmodel.vo.ImMessageVO;
import com.demo.bwmodel.vo.ImOnlineStatusVO;
import com.demo.bwmodel.vo.ImUnreadCountVO;
import com.demo.bwmodel.vo.ImUserInfoVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/")
public class ImController {

    @Resource
    private ImMessageService imMessageService;

    @Resource
    private ImAuthHelper imAuthHelper;

    @GetMapping("/conversations")
    public BaseResponse<Page<ImConversationVO>> getConversations(
            @RequestParam(required = false) Long current,
            @RequestParam(required = false) Long pageSize,
            @RequestParam(required = false) String keyword,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        ImConversationQueryRequest request = new ImConversationQueryRequest();
        request.setCurrent(current);
        request.setPageSize(pageSize);
        return ResultUtils.success(imMessageService.listConversations(userId, request));
    }

    @PostMapping("/conversations")
    public BaseResponse<ImConversationVO> createConversation(
            @RequestBody ImConversationCreateRequest request,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        return ResultUtils.success(imMessageService.getOrCreateConversation(userId, request));
    }

    @GetMapping("/conversations/{id}")
    public BaseResponse<ImConversationVO> getConversationDetail(
            @PathVariable("id") String conversationId,
            @RequestParam(required = false, defaultValue = "false") Boolean includeMessages,
            @RequestParam(required = false, defaultValue = "20") Integer messageLimit,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        ImConversationDetailRequest request = new ImConversationDetailRequest();
        request.setConversationId(conversationId);
        request.setIncludeMessages(includeMessages);
        request.setMessageLimit(messageLimit);
        return ResultUtils.success(imMessageService.getConversationDetail(userId, request));
    }

    @GetMapping("/messages/{id}")
    public BaseResponse<List<ImMessageVO>> getMessages(
            @PathVariable("id") String conversationId,
            @RequestParam(required = false) Long cursorMsgId,
            @RequestParam(required = false, defaultValue = "BACKWARD") String direction,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false, defaultValue = "false") Boolean reverse,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        ImMessageQueryRequest request = new ImMessageQueryRequest();
        request.setConversationId(conversationId);
        request.setCursorMsgId(cursorMsgId);
        request.setDirection(direction);
        request.setPageSize(pageSize);
        request.setReverse(reverse);
        return ResultUtils.success(imMessageService.listMessages(userId, request));
    }

    @PostMapping("/messages/send")
    public BaseResponse<ImMessageVO> sendMessage(
            @RequestBody ImSendMessageRequest request,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        return ResultUtils.success(imMessageService.sendPrivateMessage(userId, null, request));
    }

    @PostMapping("/messages/read/{id}")
    public BaseResponse<ImUnreadCountVO> markAsRead(
            @PathVariable("id") String conversationId,
            @RequestBody(required = false) ImMessageReadRequest request,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        if (request == null) {
            request = new ImMessageReadRequest();
        }
        request.setConversationId(conversationId);
        return ResultUtils.success(imMessageService.markRead(userId, request));
    }

    @PostMapping("/messages/read/all")
    public BaseResponse<ImUnreadCountVO> markAllAsRead(HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        return ResultUtils.success(imMessageService.markAllRead(userId));
    }

    @GetMapping("/messages/unread/count")
    public BaseResponse<ImUnreadCountVO> getUnreadCount(
            @RequestParam(required = false) String conversationId,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        return ResultUtils.success(imMessageService.getUnreadCount(userId, conversationId));
    }

    @GetMapping("/users/search")
    public BaseResponse<List<ImUserInfoVO>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize,
            @RequestParam(required = false, defaultValue = "1") Integer current,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        
        ImSearchUserRequest request = new ImSearchUserRequest();
        request.setKeyword(keyword);
        request.setPageSize(pageSize);
        request.setCurrent(current);
        return ResultUtils.success(imMessageService.searchUsers(userId, request));
    }

    @GetMapping("/ws/token")
    public BaseResponse<String> getWsToken(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false, defaultValue = "false") Boolean kickOthers,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        ImWsTokenRequest request = new ImWsTokenRequest();
        request.setDeviceId(deviceId);
        request.setKickOthers(kickOthers);
        return ResultUtils.success(imMessageService.generateWsToken(userId, request));
    }

    @GetMapping("/message/get")
    public BaseResponse<ImMessageVO> getMessage(
            @RequestParam Long messageId,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        return ResultUtils.success(imMessageService.getMessage(userId, messageId));
    }

    @PostMapping("/message/history")
    public BaseResponse<List<ImMessageVO>> listHistory(
            @RequestBody ImHistoryQueryRequest request,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        return ResultUtils.success(imMessageService.listHistory(userId, request));
    }

    @PostMapping("/message/recall")
    public BaseResponse<Boolean> recall(
            @RequestBody ImMessageRecallRequest request,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        imMessageService.recallMessage(userId, request);
        return ResultUtils.success(true);
    }

    @PostMapping("/message/delete")
    public BaseResponse<Boolean> delete(
            @RequestBody ImMessageDeleteRequest request,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        imMessageService.deleteMessage(userId, request);
        return ResultUtils.success(true);
    }

    @GetMapping("/message/unread")
    public BaseResponse<ImUnreadCountVO> getUnreadCountDetail(
            @RequestParam(required = false) String conversationId,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        return ResultUtils.success(imMessageService.getUnreadCount(userId, conversationId));
    }

    @PostMapping("/message/unread/clear")
    public BaseResponse<ImUnreadCountVO> clearUnread(
            @RequestBody ImUnreadClearRequest request,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        return ResultUtils.success(imMessageService.clearUnread(userId, request));
    }

    @PostMapping("/conversation/list")
    public BaseResponse<Page<ImConversationVO>> listConversations(
            @RequestBody(required = false) ImConversationQueryRequest request,
            HttpServletRequest httpServletRequest) {
        Long userId = imAuthHelper.requireUserId(httpServletRequest);
        ImConversationQueryRequest actualRequest = request == null ? new ImConversationQueryRequest() : request;
        return ResultUtils.success(imMessageService.listConversations(userId, actualRequest));
    }

    @PostMapping("/online/status")
    public BaseResponse<List<ImOnlineStatusVO>> listOnlineStatus(
            @RequestBody ImOnlineStatusQueryRequest request,
            HttpServletRequest httpServletRequest) {
        imAuthHelper.requireUserId(httpServletRequest);
        return ResultUtils.success(imMessageService.listOnlineStatus(request.getUserIds()));
    }
}
