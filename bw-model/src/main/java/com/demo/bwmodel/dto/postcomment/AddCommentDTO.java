package com.demo.bwmodel.dto.postcomment;

import lombok.Data;

/**
 * 添加评论请求
 */
@Data
public class AddCommentDTO {

    /**
     * 帖子ID
     */
    private Long postId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 父评论ID（一级评论为0）
     */
    private Long parentId = 0L;

    /**
     * 被回复用户ID
     */
    private Long replyUserId = 0L;
}