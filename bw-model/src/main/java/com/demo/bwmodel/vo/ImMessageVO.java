package com.demo.bwmodel.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImMessageVO implements Serializable {

    private Long id;

    private String clientMsgId;

    private String conversationId;

    private Long fromUserId;

    private Long toUserId;

    private String content;

    private String msgType;

    private String extra;

    private Integer status;

    private Long createTime;

    private Integer isRecalled;

    private Long recallTime;
}
