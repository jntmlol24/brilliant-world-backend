package com.demo.bwim.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.demo.bwmodel.dto.im.*;
import com.demo.bwmodel.entity.ImPrivateMessage;
import com.demo.bwmodel.vo.ImConversationVO;
import com.demo.bwmodel.vo.ImMessageVO;
import com.demo.bwmodel.vo.ImOnlineStatusVO;
import com.demo.bwmodel.vo.ImUnreadCountVO;
import com.demo.bwmodel.vo.ImUserInfoVO;
import com.demo.bwmodel.vo.UserVO;

import java.util.List;
import java.util.Map;

public interface ImMessageService extends IService<ImPrivateMessage> {

    ImMessageVO sendPrivateMessage(Long senderId, String senderDeviceId, ImSendMessageRequest request);

    Page<ImConversationVO> listConversations(Long userId, ImConversationQueryRequest request);

    ImConversationVO getOrCreateConversation(Long userId, ImConversationCreateRequest request);

    ImConversationVO getConversationDetail(Long userId, ImConversationDetailRequest request);

    List<ImMessageVO> listHistory(Long userId, ImHistoryQueryRequest request);

    List<ImMessageVO> listMessages(Long userId, ImMessageQueryRequest request);

    ImUnreadCountVO markDelivered(Long userId, List<Long> messageIds);

    ImUnreadCountVO markRead(Long userId, ImMessageReadRequest request);

    ImUnreadCountVO markAllRead(Long userId);

    void recallMessage(Long userId, ImMessageRecallRequest request);

    void deleteMessage(Long userId, ImMessageDeleteRequest request);

    ImUnreadCountVO clearUnread(Long userId, ImUnreadClearRequest request);

    ImUnreadCountVO getUnreadCount(Long userId, String conversationId);

    Map<String, Object> getUnreadCountByConversations(Long userId);

    List<ImOnlineStatusVO> listOnlineStatus(List<Long> userIds);

    List<ImMessageVO> listOfflineMessages(Long userId, int limit);

    ImMessageVO getMessage(Long userId, Long messageId);

    void notifyUserOnlineStatus(Long userId, boolean online, Long lastActiveTime);

    List<ImUserInfoVO> searchUsers(Long userId, ImSearchUserRequest request);

    String generateWsToken(Long userId, ImWsTokenRequest request);
}
