package com.demo.bwmodel.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 评论响应VO（返回前端，带用户信息）
 */
@Data
public class CommentVO {
    // 评论信息
    private Long id;
    private Long postId;
    private String content;
    private Long parentId;
    private Long replyUserId;
    private Integer likeNum;
    private Date createTime;

    // 发布用户信息（关联user表）
    private Long userId;
    private String userAccount;
    private String userAvatar;

    // 被回复用户名称（二级回复展示）
    private String replyUserName;
}