package com.demo.bwpost.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.demo.bwmodel.dto.postcomment.AddCommentDTO;
import com.demo.bwmodel.entity.PostComment;
import com.demo.bwmodel.vo.CommentVO;

/**
 * 评论服务
 */
public interface PostCommentService extends IService<PostComment> {

    /**
     * 添加评论
     */
    boolean addComment(AddCommentDTO dto, Long userId);

    /**
     * 删除评论（仅本人/管理员）
     */
    boolean deleteComment(Long commentId, Long userId, String userRole);

    /**
     * 分页查询帖子评论（带用户信息）
     */
    Page<CommentVO> listCommentByPage(Long postId, long current, long size);

    /**
     * 根据帖子ID统计评论数
     */
    long countByPostId(Long postId);
}