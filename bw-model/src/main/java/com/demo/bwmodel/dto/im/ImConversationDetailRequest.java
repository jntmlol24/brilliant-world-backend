package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImConversationDetailRequest implements Serializable {

    private String conversationId;

    private Boolean includeMessages;

    private Integer messageLimit;
}
