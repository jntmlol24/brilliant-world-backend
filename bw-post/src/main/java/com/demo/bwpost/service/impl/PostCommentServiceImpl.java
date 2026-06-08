package com.demo.bwpost.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.bwcommon.common.ErrorCode;
import com.demo.bwcommon.exception.BusinessException;
import com.demo.bwmodel.dto.postcomment.AddCommentDTO;
import com.demo.bwmodel.entity.PostComment;
import com.demo.bwmodel.rpc.UserServiceRpc;
import com.demo.bwmodel.vo.CommentVO;
import com.demo.bwpost.mapper.PostCommentMapper;
import com.demo.bwpost.service.PostCommentService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PostCommentServiceImpl extends ServiceImpl<PostCommentMapper, PostComment> implements PostCommentService {

    // 远程调用用户服务（Dubbo）
    @DubboReference
    private UserServiceRpc userServiceRpc;

    @Override
    public boolean addComment(AddCommentDTO dto, Long userId) {
        PostComment comment = new PostComment();
        BeanUtils.copyProperties(dto, comment);
        comment.setUserId(userId);
        comment.setLikeNum(0);
        return this.save(comment);
    }

    @Override
    public boolean deleteComment(Long commentId, Long userId, String userRole) {
        PostComment comment = this.getById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        // 权限校验：仅本人或管理员可删除
        if (!comment.getUserId().equals(userId) && !"admin".equals(userRole)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return this.removeById(commentId);
    }

    @Override
    public Page<CommentVO> listCommentByPage(Long postId, long current, long size) {
        // 1. 分页查询评论
        LambdaQueryWrapper<PostComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostComment::getPostId, postId)
                .orderByAsc(PostComment::getCreateTime);
        Page<PostComment> page = this.page(new Page<>(current, size), wrapper);

        // 2. 关联用户信息（Dubbo调用用户服务）
        List<Long> userIds = page.getRecords().stream().map(PostComment::getUserId).collect(Collectors.toList());
        List<Long> replyUserIds = page.getRecords().stream().map(PostComment::getReplyUserId).filter(id -> id > 0).collect(Collectors.toList());
        userIds.addAll(replyUserIds);

        Map<Long, String> userNameMap = userServiceRpc.batchGetUserName(userIds);
        Map<Long, String> userAvatarMap = userServiceRpc.batchGetUserAvatar(userIds);

        // 3. 封装VO
        List<CommentVO> voList = page.getRecords().stream().map(comment -> {
            CommentVO vo = new CommentVO();
            BeanUtils.copyProperties(comment, vo);
            vo.setUserAccount(userNameMap.get(comment.getUserId()));
            vo.setUserAvatar(userAvatarMap.get(comment.getUserId()));
            if (comment.getReplyUserId() > 0) {
                vo.setReplyUserName(userNameMap.get(comment.getReplyUserId()));
            }
            return vo;
        }).collect(Collectors.toList());

        // 4. 返回分页结果
        Page<CommentVO> resultPage = new Page<>(current, size, page.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public long countByPostId(Long postId) {
        LambdaQueryWrapper<PostComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostComment::getPostId, postId);
        return this.count(wrapper);
    }
}