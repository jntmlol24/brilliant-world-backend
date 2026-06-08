package com.demo.bwmodel.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ImConversationVO implements Serializable {

    private String conversationId;

    private Long peerUserId;

    private Long lastMsgId;

    private String lastMsgContent;

    private Long lastMsgTime;

    private Integer unreadCount;

    private Long lastReadMsgId;

    private ImUserInfoVO peerUser;

    private List<ImMessageVO> recentMessages;
}
