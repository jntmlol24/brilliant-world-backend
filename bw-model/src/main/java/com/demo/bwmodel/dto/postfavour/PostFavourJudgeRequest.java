package com.demo.bwmodel.dto.postfavour;


import com.demo.bwcommon.common.PageRequest;
import com.demo.bwmodel.dto.post.PostQueryRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 帖子收藏查询请求
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostFavourJudgeRequest extends PageRequest implements Serializable {

    /**
     * 帖子 id
     */
    private Long postId;

    /**
     * 用户 id
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}