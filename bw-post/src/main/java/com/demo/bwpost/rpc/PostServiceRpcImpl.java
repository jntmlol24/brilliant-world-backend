package com.demo.bwpost.rpc;

import com.demo.bwcommon.common.ErrorCode;
import com.demo.bwcommon.exception.BusinessException;
import com.demo.bwmodel.dto.post.PostAddRequest;
import com.demo.bwmodel.dto.post.PostQueryRequest;
import com.demo.bwmodel.dto.post.PostUpdateRequest;
import com.demo.bwmodel.entity.Post;
import com.demo.bwmodel.rpc.PostServiceRpc;
import com.demo.bwpost.service.PostService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 帖子服务Dubbo实现类
 */
@DubboService
@Service
@Slf4j
public class PostServiceRpcImpl implements PostServiceRpc {

    @Resource
    private PostService postService;

    @Override
    public Post getPostById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "帖子ID不合法");
        }
        return postService.getById(id);
    }

    @Override
    public List<Post> listPost(PostQueryRequest postQueryRequest) {
        return postService.list(postService.getQueryWrapper(postQueryRequest));
    }

    @Override
    public List<Post> listPostByPage(PostQueryRequest postQueryRequest, int pageSize, int currentPage) {
        if (pageSize <= 0 || currentPage <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        }

        var page = postService.page(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(currentPage, pageSize),
                postService.getQueryWrapper(postQueryRequest)
        );

        return page.getRecords();
    }

    @Override
    public List<Post> searchPost(String searchText, int pageSize, int currentPage) {
        if (pageSize <= 0 || currentPage <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        }

        // 创建查询请求
        PostQueryRequest postQueryRequest = new PostQueryRequest();
        postQueryRequest.setSearchText(searchText);
        
        var page = postService.page(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(currentPage, pageSize),
                postService.getQueryWrapper(postQueryRequest)
        );

        return page.getRecords();
    }

    @Override
    public long createPost(PostAddRequest postAddRequest, long userId) {
        if (postAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        
        Post post = new Post();
        BeanUtils.copyProperties(postAddRequest, post);
        post.setUserId(userId);
        
        // 验证帖子信息
        validPost(post, true);
        
        // 创建帖子
        boolean result = postService.save(post);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建失败");
        }
        
        return post.getId();
    }

    @Override
    public boolean updatePost(PostUpdateRequest postUpdateRequest, long userId) {
        if (postUpdateRequest == null || postUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        
        // 验证权限
        if (!validatePostPermission(postUpdateRequest.getId(), userId, true)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无操作权限");
        }
        
        Post post = new Post();
        BeanUtils.copyProperties(postUpdateRequest, post);
        
        // 验证帖子信息
        validPost(post, false);
        
        return postService.updateById(post);
    }

    @Override
    public boolean deletePost(long id, long userId) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "帖子ID不合法");
        }
        
        // 验证权限
        if (!validatePostPermission(id, userId, true)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无删除权限");
        }
        
        return postService.removeById(id);
    }

    @Override
    public boolean validatePostPermission(long postId, long userId, boolean requireOwner) {
        Post post = postService.getById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        }
        
        // 如果是管理员，拥有所有权限
        if (isAdmin(userId)) {
            return true;
        }
        
        // 检查是否是帖子所有者
        boolean isOwner = post.getUserId().equals(userId);
        
        // 如果要求是所有者，则必须满足；否则有阅读权限即可
        return requireOwner ? isOwner : true;
    }

    @Override
    public long countPost() {
        return postService.count();
    }

    @Override
    public void validPost(Post post, boolean isAdd) {
        if (post == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        
        String title = post.getTitle();
        String content = post.getContent();
        
        // 创建时，参数不能为空
        if (isAdd) {
            if (title == null || title.trim().length() == 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题不能为空");
            }
            if (content == null || content.trim().length() == 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容不能为空");
            }
        }
        
        // 如果内容不为空，则进行长度校验
        if (title != null && title.length() > 80) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (content != null && content.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容过长");
        }
        
        // 使用原有的验证逻辑
        postService.validPost(post, isAdd);
    }

    /**
     * 判断是否为管理员
     */
    private boolean isAdmin(long userId) {
        // 此处需要依赖用户服务进行判断
        // 在实际实现中，可以通过Dubbo调用UserServiceRpc.isAdmin方法
        // 暂时返回false，后续在实际使用时通过Dubbo调用实现
        return false;
    }
}