package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImUnreadClearRequest implements Serializable {

    private String conversationId;
}
