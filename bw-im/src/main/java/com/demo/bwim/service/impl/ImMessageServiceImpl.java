package com.demo.bwim.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.bwcommon.common.ErrorCode;
import com.demo.bwcommon.constant.IMMessageType;
import com.demo.bwcommon.exception.BusinessException;
import com.demo.bwim.mapper.ImConversationMapper;
import com.demo.bwim.mapper.ImPrivateMessageMapper;
import com.demo.bwim.service.ImMessageService;
import com.demo.bwim.service.MessagePushService;
import com.demo.bwim.support.ImSessionManager;
import com.demo.bwim.support.ImWsEnvelope;
import com.demo.bwmodel.dto.im.*;
import com.demo.bwmodel.entity.ImConversation;
import com.demo.bwmodel.entity.ImPrivateMessage;
import com.demo.bwmodel.entity.User;
import com.demo.bwmodel.vo.ImConversationVO;
import com.demo.bwmodel.vo.ImMessageVO;
import com.demo.bwmodel.vo.ImOnlineStatusVO;
import com.demo.bwmodel.vo.ImUnreadCountVO;
import com.demo.bwmodel.vo.ImUserInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import com.demo.bwmodel.rpc.UserServiceRpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ImMessageServiceImpl extends ServiceImpl<ImPrivateMessageMapper, ImPrivateMessage> implements ImMessageService {

    private static final int STATUS_SENDING = 0;

    private static final int STATUS_DELIVERED = 1;

    private static final int STATUS_READ = 2;

    private static final int FLAG_NO = 0;

    private static final int FLAG_YES = 1;

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    private static final int MAX_HISTORY_LIMIT = 100;

    private static final long RECALL_LIMIT_MILLIS = 2 * 60 * 1000L;

    @Resource
    private ImConversationMapper imConversationMapper;

    @Resource
    private ImSessionManager imSessionManager;

    @Resource
    private MessagePushService messagePushService;

    @DubboReference
    private UserServiceRpc userServiceRpc;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImMessageVO sendPrivateMessage(Long senderId, String senderDeviceId, ImSendMessageRequest request) {
        if (StringUtils.isBlank(request.getClientMsgId())) {
            request.setClientMsgId("msg_" + IdUtil.getSnowflakeNextId());
        }
        
        validateSendRequest(senderId, request);
        ImPrivateMessage existed = getByClientMsgId(request.getClientMsgId());
        if (existed != null) {
            if (!Objects.equals(existed.getFromUserId(), senderId)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "clientMsgId already exists");
            }
            log.info("[MSG-SEND] dedup, sender={}, toUser={}, msgId={}, existedMsgId={}",
                    senderId, request.getToUserId(), request.getClientMsgId(), existed.getId());
            return toMessageVO(existed);
        }

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
        message.setIsRecalled(FLAG_NO);
        message.setSenderDeleted(FLAG_NO);
        message.setReceiverDeleted(FLAG_NO);

        try {
            save(message);
            log.info("[MSG-SAVE] msgId={}, sender={}, toUser={}, conversationId={}, type={}",
                    message.getId(), senderId, request.getToUserId(), message.getConversationId(), message.getMsgType());
        } catch (DuplicateKeyException e) {
            ImPrivateMessage duplicate = getByClientMsgId(request.getClientMsgId());
            if (duplicate != null) {
                return toMessageVO(duplicate);
            }
            throw e;
        }

        updateConversationAfterSend(message);
        ImMessageVO messageVO = toMessageVO(message);
        
        ImWsEnvelope envelope = ImWsEnvelope.ok(IMMessageType.MESSAGE_MESSAGE_PUSH, messageVO);
        log.info("[MSG-PUSH-TRIGGER] msgId={}, sender={}, toUser={}, pushing to both users",
                message.getId(), senderId, request.getToUserId());
        messagePushService.pushToUser(senderId, envelope);
        messagePushService.pushToUser(request.getToUserId(), envelope);
        
        pushConversationUpdate(senderId, message.getConversationId());
        pushConversationUpdate(request.getToUserId(), message.getConversationId());
        log.info("[MSG-SEND-COMPLETE] msgId={}, sender={}, toUser={}", message.getId(), senderId, request.getToUserId());
        return messageVO;
    }

    @Override
    public Page<ImConversationVO> listConversations(Long userId, ImConversationQueryRequest request) {
        long current = request == null || request.getCurrent() == null || request.getCurrent() <= 0 ? 1L : request.getCurrent();
        long pageSize = request == null || request.getPageSize() == null || request.getPageSize() <= 0 ? 20L : Math.min(request.getPageSize(), 100L);
        Page<ImConversation> page = new Page<>(current, pageSize);
        LambdaQueryWrapper<ImConversation> wrapper = new LambdaQueryWrapper<ImConversation>()
                .eq(ImConversation::getUserId, userId)
                .eq(ImConversation::getIsDeleted, FLAG_NO)
                .orderByDesc(ImConversation::getLastMsgTime)
                .orderByDesc(ImConversation::getUpdateTime);
        Page<ImConversation> conversationPage = imConversationMapper.selectPage(page, wrapper);
        List<ImConversationVO> records = buildConversationVOs(conversationPage.getRecords());
        Page<ImConversationVO> result = new Page<>(conversationPage.getCurrent(), conversationPage.getSize(), conversationPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public List<ImMessageVO> listHistory(Long userId, ImHistoryQueryRequest request) {
        if (request == null || StringUtils.isBlank(request.getConversationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "conversationId is required");
        }
        int pageSize = request.getPageSize() == null || request.getPageSize() <= 0
                ? DEFAULT_HISTORY_LIMIT : Math.min(request.getPageSize(), MAX_HISTORY_LIMIT);
        String direction = StringUtils.defaultIfBlank(request.getDirection(), "BACKWARD").toUpperCase();

        LambdaQueryWrapper<ImPrivateMessage> wrapper = visibleMessageWrapper(userId, request.getConversationId());
        if (request.getCursorMsgId() != null && request.getCursorMsgId() > 0) {
            if ("FORWARD".equals(direction)) {
                wrapper.gt(ImPrivateMessage::getId, request.getCursorMsgId()).orderByAsc(ImPrivateMessage::getId);
            } else {
                wrapper.lt(ImPrivateMessage::getId, request.getCursorMsgId()).orderByDesc(ImPrivateMessage::getId);
            }
        } else if ("FORWARD".equals(direction)) {
            wrapper.orderByAsc(ImPrivateMessage::getId);
        } else {
            wrapper.orderByDesc(ImPrivateMessage::getId);
        }
        wrapper.last("limit " + pageSize);
        List<ImPrivateMessage> messages = list(wrapper);
        if (!"FORWARD".equals(direction)) {
            messages.sort(Comparator.comparing(ImPrivateMessage::getId));
        }
        return messages.stream().map(this::toMessageVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImUnreadCountVO markDelivered(Long userId, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return getUnreadCount(userId, null);
        }
        List<ImPrivateMessage> messages = list(new LambdaQueryWrapper<ImPrivateMessage>()
                .in(ImPrivateMessage::getId, messageIds)
                .eq(ImPrivateMessage::getToUserId, userId));
        for (ImPrivateMessage message : messages) {
            if (message.getStatus() < STATUS_DELIVERED) {
                message.setStatus(STATUS_DELIVERED);
                updateById(message);
                ImMessageVO messageVO = toMessageVO(message);
                log.info("[MSG-DELIVERED-PUSH] msgId={}, fromUserId={}, toUserId={}",
                        message.getId(), message.getFromUserId(), message.getToUserId());
                imSessionManager.sendToUser(message.getFromUserId(), ImWsEnvelope.ok(IMMessageType.MESSAGE_DELIVERED, messageVO));
                imSessionManager.sendToUser(message.getToUserId(), ImWsEnvelope.ok(IMMessageType.MESSAGE_DELIVERED, messageVO));
            }
        }
        return getUnreadCount(userId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImUnreadCountVO markRead(Long userId, ImMessageReadRequest request) {
        if (request == null || StringUtils.isBlank(request.getConversationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "conversationId is required");
        }
        ImConversation conversation = getConversation(userId, request.getConversationId());
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "conversation not found");
        }
        long readMessageId = request.getMessageId() != null && request.getMessageId() > 0
                ? request.getMessageId()
                : (conversation.getLastMsgId() == null ? 0L : conversation.getLastMsgId());
        if (readMessageId <= 0) {
            return getUnreadCount(userId, request.getConversationId());
        }

        List<ImPrivateMessage> unreadMessages = list(new LambdaQueryWrapper<ImPrivateMessage>()
                .eq(ImPrivateMessage::getConversationId, request.getConversationId())
                .eq(ImPrivateMessage::getToUserId, userId)
                .eq(ImPrivateMessage::getReceiverDeleted, FLAG_NO)
                .le(ImPrivateMessage::getId, readMessageId)
                .lt(ImPrivateMessage::getStatus, STATUS_READ));
        for (ImPrivateMessage message : unreadMessages) {
            message.setStatus(STATUS_READ);
            updateById(message);
        }

        conversation.setLastReadMsgId(Math.max(readMessageId, conversation.getLastReadMsgId() == null ? 0L : conversation.getLastReadMsgId()));
        conversation.setUnreadCount(countUnreadMessages(userId, request.getConversationId()));
        conversation.setUpdateTime(System.currentTimeMillis());
        imConversationMapper.updateById(conversation);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", request.getConversationId());
        payload.put("messageId", readMessageId);
        payload.put("userId", userId);
        log.info("[MSG-READ-PUSH] userId={}, conversationId={}, readMessageId={}, notifying peer={}",
                userId, request.getConversationId(), readMessageId, conversation.getPeerUserId());
        imSessionManager.sendToUser(conversation.getPeerUserId(), ImWsEnvelope.ok(IMMessageType.MESSAGE_MESSAGE_READ, payload));
        pushConversationUpdate(userId, request.getConversationId());
        pushConversationUpdate(conversation.getPeerUserId(), request.getConversationId());
        return getUnreadCount(userId, request.getConversationId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recallMessage(Long userId, ImMessageRecallRequest request) {
        if (request == null || request.getMessageId() == null || request.getMessageId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "messageId is required");
        }
        ImPrivateMessage message = getById(request.getMessageId());
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "message not found");
        }
        if (!Objects.equals(message.getFromUserId(), userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "only sender can recall");
        }
        if (Objects.equals(message.getIsRecalled(), FLAG_YES)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - message.getCreateTime() > RECALL_LIMIT_MILLIS) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "recall window expired");
        }
        message.setIsRecalled(FLAG_YES);
        message.setRecallTime(now);
        updateById(message);

        updateConversationSummaryIfLast(message.getConversationId(), message.getId(), "[RECALLED]");
        ImMessageVO messageVO = toMessageVO(message);
        log.info("[MSG-RECALL-PUSH] msgId={}, fromUserId={}, toUserId={}",
                message.getId(), message.getFromUserId(), message.getToUserId());
        imSessionManager.sendToUser(message.getFromUserId(), ImWsEnvelope.ok(IMMessageType.MESSAGE_RECALL, messageVO));
        imSessionManager.sendToUser(message.getToUserId(), ImWsEnvelope.ok(IMMessageType.MESSAGE_RECALL, messageVO));
        pushConversationUpdate(message.getFromUserId(), message.getConversationId());
        pushConversationUpdate(message.getToUserId(), message.getConversationId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Long userId, ImMessageDeleteRequest request) {
        if (request == null || request.getMessageId() == null || request.getMessageId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "messageId is required");
        }
        ImPrivateMessage message = getById(request.getMessageId());
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "message not found");
        }
        if (Objects.equals(message.getFromUserId(), userId)) {
            message.setSenderDeleted(FLAG_YES);
        } else if (Objects.equals(message.getToUserId(), userId)) {
            message.setReceiverDeleted(FLAG_YES);
        } else {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "no permission to delete message");
        }
        updateById(message);
        refreshConversationSnapshot(userId, message.getConversationId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", message.getId());
        payload.put("conversationId", message.getConversationId());
        payload.put("userId", userId);
        log.info("[MSG-DELETE-PUSH] msgId={}, userId={}, conversationId={}",
                message.getId(), userId, message.getConversationId());
        imSessionManager.sendToUser(userId, ImWsEnvelope.ok(IMMessageType.MESSAGE_DELETE, payload));
        pushConversationUpdate(userId, message.getConversationId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImUnreadCountVO clearUnread(Long userId, ImUnreadClearRequest request) {
        if (request == null || StringUtils.isBlank(request.getConversationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "conversationId is required");
        }
        ImConversation conversation = getConversation(userId, request.getConversationId());
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "conversation not found");
        }
        ImMessageReadRequest readRequest = new ImMessageReadRequest();
        readRequest.setConversationId(request.getConversationId());
        readRequest.setMessageId(conversation.getLastMsgId());
        return markRead(userId, readRequest);
    }

    @Override
    public ImUnreadCountVO getUnreadCount(Long userId, String conversationId) {
        ImUnreadCountVO unreadCountVO = new ImUnreadCountVO();
        unreadCountVO.setConversationId(conversationId);
        if (StringUtils.isNotBlank(conversationId)) {
            ImConversation conversation = getConversation(userId, conversationId);
            unreadCountVO.setUnreadCount(conversation == null ? 0 : conversation.getUnreadCount());
        } else {
            unreadCountVO.setUnreadCount(0);
        }

        List<ImConversation> conversations = imConversationMapper.selectList(new LambdaQueryWrapper<ImConversation>()
                .eq(ImConversation::getUserId, userId)
                .eq(ImConversation::getIsDeleted, FLAG_NO)
                .select(ImConversation::getUnreadCount));
        int totalUnreadCount = conversations.stream()
                .map(ImConversation::getUnreadCount)
                .filter(Objects::nonNull)
                .reduce(0, Integer::sum);
        unreadCountVO.setTotalUnreadCount(totalUnreadCount);
        if (StringUtils.isBlank(conversationId)) {
            unreadCountVO.setUnreadCount(totalUnreadCount);
        }
        return unreadCountVO;
    }

    @Override
    public List<ImOnlineStatusVO> listOnlineStatus(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ImOnlineStatusVO> result = new ArrayList<>();
        for (Long userId : new LinkedHashSet<>(userIds)) {
            ImOnlineStatusVO onlineStatusVO = new ImOnlineStatusVO();
            onlineStatusVO.setUserId(userId);
            onlineStatusVO.setOnline(imSessionManager.isUserOnline(userId));
            onlineStatusVO.setLastActiveTime(imSessionManager.getLastActiveTime(userId));
            result.add(onlineStatusVO);
        }
        return result;
    }

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

    @Override
    public ImMessageVO getMessage(Long userId, Long messageId) {
        if (messageId == null || messageId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "messageId is required");
        }
        ImPrivateMessage message = getById(messageId);
        if (message == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "message not found");
        }
        boolean visible = Objects.equals(message.getFromUserId(), userId) && !Objects.equals(message.getSenderDeleted(), FLAG_YES)
                || Objects.equals(message.getToUserId(), userId) && !Objects.equals(message.getReceiverDeleted(), FLAG_YES);
        if (!visible) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "no permission to access message");
        }
        return toMessageVO(message);
    }

    @Override
    public void notifyUserOnlineStatus(Long userId, boolean online, Long lastActiveTime) {
        ImOnlineStatusVO statusVO = new ImOnlineStatusVO();
        statusVO.setUserId(userId);
        statusVO.setOnline(online);
        statusVO.setLastActiveTime(lastActiveTime);
        Set<Long> peerIds = imConversationMapper.selectList(new LambdaQueryWrapper<ImConversation>()
                        .eq(ImConversation::getPeerUserId, userId)
                        .eq(ImConversation::getIsDeleted, FLAG_NO)
                        .select(ImConversation::getUserId))
                .stream()
                .map(ImConversation::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        peerIds.add(userId);
        log.info("[MSG-ONLINE-STATUS] userId={}, online={}, notifying {} peers", userId, online, peerIds.size());
        imSessionManager.sendToUsers(peerIds, ImWsEnvelope.ok(IMMessageType.MESSAGE_ONLINE_STATUS, statusVO));
    }

    private void validateSendRequest(Long senderId, ImSendMessageRequest request) {
        if (senderId == null || senderId <= 0 || request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (StringUtils.isBlank(request.getClientMsgId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "clientMsgId is required");
        }
        if (request.getToUserId() == null || request.getToUserId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "toUserId is required");
        }
        if (StringUtils.isBlank(request.getContent())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "content is required");
        }
        if (userServiceRpc.getUserById(request.getToUserId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "receiver not found");
        }
    }

    private ImPrivateMessage getByClientMsgId(String clientMsgId) {
        return getOne(new LambdaQueryWrapper<ImPrivateMessage>()
                .eq(ImPrivateMessage::getClientMsgId, clientMsgId)
                .last("limit 1"));
    }

    private void updateConversationAfterSend(ImPrivateMessage message) {
        String summary = buildConversationSummary(message);
        long now = message.getCreateTime();
        upsertConversation(message.getFromUserId(), message.getToUserId(), message.getConversationId(), message.getId(), summary, now, false);
        upsertConversation(message.getToUserId(), message.getFromUserId(), message.getConversationId(), message.getId(), summary, now, true);
    }

    private void upsertConversation(Long userId, Long peerUserId, String conversationId, Long messageId, String summary, long now, boolean increaseUnread) {
        ImConversation conversation = getConversation(userId, conversationId);
        if (conversation == null) {
            conversation = new ImConversation();
            conversation.setUserId(userId);
            conversation.setPeerUserId(peerUserId);
            conversation.setConversationId(conversationId);
            conversation.setCreateTime(now);
            conversation.setUnreadCount(increaseUnread ? 1 : 0);
            conversation.setLastReadMsgId(messageId != null ? messageId : 0L);
        } else {
            conversation.setUnreadCount(increaseUnread ? conversation.getUnreadCount() + 1 : conversation.getUnreadCount());
        }
        conversation.setPeerUserId(peerUserId);
        conversation.setLastMsgId(messageId);
        conversation.setLastMsgContent(summary);
        conversation.setLastMsgTime(now);
        conversation.setIsDeleted(FLAG_NO);
        conversation.setUpdateTime(now);
        if (conversation.getId() == null) {
            imConversationMapper.insert(conversation);
        } else {
            imConversationMapper.updateById(conversation);
        }
    }

    private ImConversation getConversation(Long userId, String conversationId) {
        return imConversationMapper.selectOne(new LambdaQueryWrapper<ImConversation>()
                .eq(ImConversation::getUserId, userId)
                .eq(ImConversation::getConversationId, conversationId)
                .last("limit 1"));
    }

    private void refreshConversationSnapshot(Long userId, String conversationId) {
        ImConversation conversation = getConversation(userId, conversationId);
        if (conversation == null) {
            return;
        }
        ImPrivateMessage latestVisibleMessage = findLatestVisibleMessage(userId, conversationId);
        if (latestVisibleMessage == null) {
            conversation.setLastMsgId(null);
            conversation.setLastMsgContent(null);
            conversation.setLastMsgTime(null);
        } else {
            conversation.setLastMsgId(latestVisibleMessage.getId());
            conversation.setLastMsgContent(buildConversationSummary(latestVisibleMessage));
            conversation.setLastMsgTime(latestVisibleMessage.getCreateTime());
        }
        conversation.setUnreadCount(countUnreadMessages(userId, conversationId));
        conversation.setUpdateTime(System.currentTimeMillis());
        imConversationMapper.updateById(conversation);
    }

    private ImPrivateMessage findLatestVisibleMessage(Long userId, String conversationId) {
        return getOne(visibleMessageWrapper(userId, conversationId)
                .orderByDesc(ImPrivateMessage::getId)
                .last("limit 1"));
    }

    private LambdaQueryWrapper<ImPrivateMessage> visibleMessageWrapper(Long userId, String conversationId) {
        return new LambdaQueryWrapper<ImPrivateMessage>()
                .eq(ImPrivateMessage::getConversationId, conversationId)
                .and(wrapper -> wrapper
                        .eq(ImPrivateMessage::getFromUserId, userId)
                        .eq(ImPrivateMessage::getSenderDeleted, FLAG_NO)
                        .or()
                        .eq(ImPrivateMessage::getToUserId, userId)
                        .eq(ImPrivateMessage::getReceiverDeleted, FLAG_NO));
    }

    private int countUnreadMessages(Long userId, String conversationId) {
        Long count = baseMapper.selectCount(new LambdaQueryWrapper<ImPrivateMessage>()
                .eq(ImPrivateMessage::getConversationId, conversationId)
                .eq(ImPrivateMessage::getToUserId, userId)
                .eq(ImPrivateMessage::getReceiverDeleted, FLAG_NO)
                .lt(ImPrivateMessage::getStatus, STATUS_READ));
        return count == null ? 0 : count.intValue();
    }

    private void updateConversationSummaryIfLast(String conversationId, Long lastMsgId, String summary) {
        LambdaUpdateWrapper<ImConversation> updateWrapper = new LambdaUpdateWrapper<ImConversation>()
                .eq(ImConversation::getConversationId, conversationId)
                .eq(ImConversation::getLastMsgId, lastMsgId)
                .set(ImConversation::getLastMsgContent, summary)
                .set(ImConversation::getUpdateTime, System.currentTimeMillis());
        imConversationMapper.update(null, updateWrapper);
    }

    private void pushConversationUpdate(Long userId, String conversationId) {
        ImConversation conversation = getConversation(userId, conversationId);
        if (conversation == null) {
            return;
        }
        List<ImConversationVO> conversationVOS = buildConversationVOs(List.of(conversation));
        if (conversationVOS.isEmpty()) {
            return;
        }
        imSessionManager.sendToUser(userId, ImWsEnvelope.ok(IMMessageType.MESSAGE_CONVERSATION_SYNC, conversationVOS.get(0)));
    }

    private List<ImConversationVO> buildConversationVOs(List<ImConversation> conversations) {
        if (conversations == null || conversations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> peerUserIds = conversations.stream()
                .map(ImConversation::getPeerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> userNameMap = userServiceRpc.batchGetUserName(peerUserIds);
        Map<Long, String> userAvatarMap = userServiceRpc.batchGetUserAvatar(peerUserIds);
        Map<Long, Boolean> onlineStatusMap = imSessionManager.batchOnlineStatus(peerUserIds);

        List<ImConversationVO> result = new ArrayList<>();
        for (ImConversation conversation : conversations) {
            ImConversationVO vo = new ImConversationVO();
            vo.setConversationId(conversation.getConversationId());
            vo.setPeerUserId(conversation.getPeerUserId());
            vo.setLastMsgId(conversation.getLastMsgId());
            vo.setLastMsgContent(conversation.getLastMsgContent());
            vo.setLastMsgTime(conversation.getLastMsgTime());
            vo.setUnreadCount(conversation.getUnreadCount());
            vo.setLastReadMsgId(conversation.getLastReadMsgId());

            ImUserInfoVO peer = new ImUserInfoVO();
            peer.setUserId(conversation.getPeerUserId());
            peer.setUserAccount(userNameMap.get(conversation.getPeerUserId()));
            peer.setUserAvatar(userAvatarMap.get(conversation.getPeerUserId()));
            peer.setOnline(onlineStatusMap.getOrDefault(conversation.getPeerUserId(), false));
            vo.setPeerUser(peer);
            result.add(vo);
        }
        return result;
    }

    private ImMessageVO toMessageVO(ImPrivateMessage message) {
        ImMessageVO messageVO = new ImMessageVO();
        BeanUtils.copyProperties(message, messageVO);
        if (Objects.equals(message.getIsRecalled(), FLAG_YES)) {
            messageVO.setContent("[RECALLED]");
        }
        return messageVO;
    }

    private String buildConversationSummary(ImPrivateMessage message) {
        if (Objects.equals(message.getIsRecalled(), FLAG_YES)) {
            return "[RECALLED]";
        }
        String msgType = StringUtils.defaultIfBlank(message.getMsgType(), "TEXT").toUpperCase();
        return switch (msgType) {
            case "IMAGE" -> "[IMAGE]";
            case "VIDEO" -> "[VIDEO]";
            case "TEXT" -> StringUtils.left(StringUtils.defaultString(message.getContent()), 200);
            default -> "[CUSTOM]";
        };
    }

    private String buildConversationId(Long userA, Long userB) {
        long left = Math.min(userA, userB);
        long right = Math.max(userA, userB);
        return "chat_" + left + "_" + right;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImConversationVO getOrCreateConversation(Long userId, ImConversationCreateRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId is required");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "request is required");
        }

        Long peerUserId = request.getPeerUserId();
        if (peerUserId == null || peerUserId <= 0) {
            if (request.getParticipantIds() != null && !request.getParticipantIds().isEmpty()) {
                peerUserId = request.getParticipantIds().stream()
                        .filter(id -> !Objects.equals(id, userId))
                        .findFirst()
                        .orElse(null);
            }
        }

        if (peerUserId == null || peerUserId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "peerUserId is required");
        }

        if (Objects.equals(userId, peerUserId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "cannot create conversation with yourself");
        }

        if (userServiceRpc.getUserById(peerUserId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "user not found");
        }

        String conversationId = buildConversationId(userId, peerUserId);
        ImConversation conversation = getConversation(userId, conversationId);

        if (conversation == null) {
            long now = System.currentTimeMillis();
            upsertConversation(userId, peerUserId, conversationId, null, null, now, false);
            conversation = getConversation(userId, conversationId);
        }

        return buildSingleConversationVO(conversation);
    }

    private ImConversationVO buildSingleConversationVO(ImConversation conversation) {
        if (conversation == null) {
            return null;
        }
        ImConversationVO vo = new ImConversationVO();
        vo.setConversationId(conversation.getConversationId());
        vo.setPeerUserId(conversation.getPeerUserId());
        vo.setLastMsgId(conversation.getLastMsgId());
        vo.setLastMsgContent(conversation.getLastMsgContent());
        vo.setLastMsgTime(conversation.getLastMsgTime());
        vo.setUnreadCount(conversation.getUnreadCount());
        vo.setLastReadMsgId(conversation.getLastReadMsgId());

        ImUserInfoVO peer = new ImUserInfoVO();
        User peerUser = userServiceRpc.getUserById(conversation.getPeerUserId());
        peer.setUserId(peerUser.getId());
        peer.setUserAccount(peerUser.getUserAccount());
        peer.setUserAvatar(peerUser.getUserAvatar());
        peer.setOnline(imSessionManager.isUserOnline(conversation.getPeerUserId()));
        vo.setPeerUser(peer);
        log.info("buildSingleConversationVO: {}", vo);
        return vo;
    }

    @Override
    public ImConversationVO getConversationDetail(Long userId, ImConversationDetailRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId is required");
        }
        if (request == null || StringUtils.isBlank(request.getConversationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "conversationId is required");
        }

        ImConversation conversation = getConversation(userId, request.getConversationId());
        if (conversation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "conversation not found");
        }

        List<ImConversationVO> vos = buildConversationVOs(List.of(conversation));
        if (vos.isEmpty()) {
            return null;
        }

        ImConversationVO vo = vos.get(0);
        if (Boolean.TRUE.equals(request.getIncludeMessages())) {
            ImMessageQueryRequest messageRequest = new ImMessageQueryRequest();
            messageRequest.setConversationId(request.getConversationId());
            messageRequest.setPageSize(request.getMessageLimit() != null ? request.getMessageLimit() : 20);
            messageRequest.setDirection("BACKWARD");
            vo.setRecentMessages(listMessages(userId, messageRequest));
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImUnreadCountVO markAllRead(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId is required");
        }

        List<ImConversation> conversations = imConversationMapper.selectList(
                new LambdaQueryWrapper<ImConversation>()
                        .eq(ImConversation::getUserId, userId)
                        .eq(ImConversation::getIsDeleted, FLAG_NO)
                        .isNotNull(ImConversation::getLastMsgId)
                        .gt(ImConversation::getUnreadCount, 0)
        );

        long now = System.currentTimeMillis();
        for (ImConversation conversation : conversations) {
            if (conversation.getLastMsgId() != null && conversation.getLastMsgId() > 0) {
                ImMessageReadRequest readRequest = new ImMessageReadRequest();
                readRequest.setConversationId(conversation.getConversationId());
                readRequest.setMessageId(conversation.getLastMsgId());
                markRead(userId, readRequest);
            }
        }

        imSessionManager.sendToUser(userId, ImWsEnvelope.ok(IMMessageType.MESSAGE_ALL_READ, Map.of(
                "userId", userId,
                "timestamp", now
        )));

        return getUnreadCount(userId, null);
    }

    @Override
    public List<ImMessageVO> listMessages(Long userId, ImMessageQueryRequest request) {
        if (request == null || StringUtils.isBlank(request.getConversationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "conversationId is required");
        }

        ImHistoryQueryRequest historyRequest = new ImHistoryQueryRequest();
        historyRequest.setConversationId(request.getConversationId());
        historyRequest.setCursorMsgId(request.getCursorMsgId());
        historyRequest.setDirection(request.getDirection());
        historyRequest.setPageSize(request.getPageSize());
        historyRequest.setReverse(request.getReverse());

        return listHistory(userId, historyRequest);
    }

    @Override
    public Map<String, Object> getUnreadCountByConversations(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId is required");
        }

        List<ImConversation> conversations = imConversationMapper.selectList(
                new LambdaQueryWrapper<ImConversation>()
                        .eq(ImConversation::getUserId, userId)
                        .eq(ImConversation::getIsDeleted, FLAG_NO)
                        .select(ImConversation::getConversationId, ImConversation::getUnreadCount)
        );

        Map<String, Integer> unreadMap = conversations.stream()
                .collect(Collectors.toMap(
                        ImConversation::getConversationId,
                        c -> c.getUnreadCount() != null ? c.getUnreadCount() : 0,
                        (v1, v2) -> v1
                ));

        int totalUnread = unreadMap.values().stream().reduce(0, Integer::sum);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversations", unreadMap);
        result.put("totalUnreadCount", totalUnread);
        result.put("conversationCount", unreadMap.size());

        return result;
    }

    @Override
    public List<ImUserInfoVO> searchUsers(Long userId, ImSearchUserRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId is required");
        }
        if (request == null || StringUtils.isBlank(request.getKeyword())) {
            return Collections.emptyList();
        }

        String keyword = request.getKeyword().trim();
        if (keyword.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "keyword length cannot exceed 64 characters");
        }
        
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0
                ? Math.min(request.getPageSize(), 50) : 20;
        int current = request.getCurrent() != null && request.getCurrent() > 0
                ? request.getCurrent() : 1;

        com.demo.bwmodel.dto.user.UserQueryRequest userQueryRequest = new com.demo.bwmodel.dto.user.UserQueryRequest();
        userQueryRequest.setUserAccount(keyword);
        userQueryRequest.setFuzzySearch(Boolean.TRUE.equals(request.getFuzzySearch()) ? true : false);
        userQueryRequest.setCurrent(current);
        userQueryRequest.setPageSize(pageSize);

        List<com.demo.bwmodel.entity.User> users = userServiceRpc.listUserByPage(userQueryRequest, pageSize, current);

        if (users.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> userIds = users.stream()
                .map(com.demo.bwmodel.entity.User::getId)
                .filter(id -> !Objects.equals(id, userId))
                .toList();

        Map<Long, String> userNameMap = userServiceRpc.batchGetUserName(userIds);
        Map<Long, String> userAvatarMap = userServiceRpc.batchGetUserAvatar(userIds);
        Map<Long, Boolean> onlineStatusMap = imSessionManager.batchOnlineStatus(userIds);

        return userIds.stream().map(uid -> {
            ImUserInfoVO vo = new ImUserInfoVO();
            vo.setUserId(uid);
            vo.setUserAccount(userNameMap.get(uid));
            vo.setUserAvatar(userAvatarMap.get(uid));
            vo.setOnline(onlineStatusMap.getOrDefault(uid, false));
            return vo;
        }).toList();
    }

    @Override
    public String generateWsToken(Long userId, ImWsTokenRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "userId is required");
        }

        com.demo.bwmodel.entity.User user = userServiceRpc.getUserById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "user not found");
        }

        String deviceId = request != null && StringUtils.isNotBlank(request.getDeviceId())
                ? request.getDeviceId()
                : cn.hutool.core.util.IdUtil.simpleUUID();

        String token = com.demo.bwcommon.utils.JwtUtil.generateToken(String.valueOf(userId));

        Map<String, Object> tokenData = new LinkedHashMap<>();
        tokenData.put("token", token);
        tokenData.put("userId", userId);
        tokenData.put("deviceId", deviceId);
        tokenData.put("wsUrl", "/ws");
        tokenData.put("expiresAt", com.demo.bwcommon.utils.JwtUtil.getExpirationDate(token).getTime());

        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tokenData);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Failed to generate WebSocket token");
        }
    }
}
