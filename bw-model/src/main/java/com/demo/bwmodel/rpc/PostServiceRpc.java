package com.demo.bwmodel.rpc;

import com.demo.bwmodel.dto.post.PostAddRequest;
import com.demo.bwmodel.dto.post.PostQueryRequest;
import com.demo.bwmodel.dto.post.PostUpdateRequest;
import com.demo.bwmodel.entity.Post;

import java.util.List;

/**
 * 帖子服务Dubbo接口
 * 提供帖子相关的RPC服务
 */
public interface PostServiceRpc {

    /**
     * 根据ID获取帖子
     *
     * @param id 帖子ID
     * @return 帖子信息
     */
    Post getPostById(long id);

    /**
     * 获取帖子列表（不带分页）
     *
     * @param postQueryRequest 查询条件
     * @return 帖子列表
     */
    List<Post> listPost(PostQueryRequest postQueryRequest);

    /**
     * 获取帖子列表（带分页）
     *
     * @param postQueryRequest 查询条件
     * @param pageSize 每页大小
     * @param currentPage 当前页码
     * @return 帖子列表
     */
    List<Post> listPostByPage(PostQueryRequest postQueryRequest, int pageSize, int currentPage);

    /**
     * 搜索帖子
     *
     * @param searchText 搜索文本
     * @param pageSize 每页大小
     * @param currentPage 当前页码
     * @return 帖子列表
     */
    List<Post> searchPost(String searchText, int pageSize, int currentPage);

    /**
     * 创建帖子
     *
     * @param postAddRequest 帖子创建请求
     * @param userId 创建用户ID
     * @return 创建结果和帖子ID
     */
    long createPost(PostAddRequest postAddRequest, long userId);

    /**
     * 更新帖子
     *
     * @param postUpdateRequest 帖子更新请求
     * @param userId 更新用户ID
     * @return 更新结果
     */
    boolean updatePost(PostUpdateRequest postUpdateRequest, long userId);

    /**
     * 删除帖子
     *
     * @param id 帖子ID
     * @param userId 删除用户ID
     * @return 删除结果
     */
    boolean deletePost(long id, long userId);

    /**
     * 验证帖子权限（检查用户是否有权操作帖子）
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @param requireOwner 是否要求是帖子所有者
     * @return 是否有权限
     */
    boolean validatePostPermission(long postId, long userId, boolean requireOwner);

    /**
     * 统计帖子数量
     *
     * @return 帖子总数
     */
    long countPost();

    /**
     * 验证帖子信息
     *
     * @param post 帖子信息
     * @param isAdd 是否为新增操作
     */
    void validPost(Post post, boolean isAdd);
}