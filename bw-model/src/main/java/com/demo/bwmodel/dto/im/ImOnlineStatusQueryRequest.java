package com.demo.bwmodel.dto.im;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ImOnlineStatusQueryRequest implements Serializable {

    private List<Long> userIds;
}
