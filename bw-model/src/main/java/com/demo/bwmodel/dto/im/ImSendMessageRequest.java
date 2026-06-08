package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImSendMessageRequest implements Serializable {

    private String clientMsgId;

    private Long toUserId;

    private String content;

    private String msgType;

    private String extra;
}
