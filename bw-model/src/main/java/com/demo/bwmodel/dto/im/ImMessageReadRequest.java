package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImMessageReadRequest implements Serializable {

    private String conversationId;

    private Long messageId;
}
