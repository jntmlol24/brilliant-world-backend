package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ImMessageDeliverRequest implements Serializable {

    private List<Long> messageIds;
}
