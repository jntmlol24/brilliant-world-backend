package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ImConversationCreateRequest implements Serializable {

    private String type;

    private List<Long> participantIds;

    private Long peerUserId;

    private String extra;
}
