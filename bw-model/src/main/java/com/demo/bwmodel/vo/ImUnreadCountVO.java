package com.demo.bwmodel.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImUnreadCountVO implements Serializable {

    private String conversationId;

    private Integer unreadCount;

    private Integer totalUnreadCount;
}
