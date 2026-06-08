package com.demo.bwmodel.rpc;

import com.demo.bwmodel.dto.user.*;
import com.demo.bwmodel.entity.User;
import com.demo.bwmodel.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户服务Dubbo接口
 * 提供用户相关的RPC服务
 */
public interface UserServiceRpc {

    UserVO getUserVO(User user);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);
    User getLoginUser(Object userObj);

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    User getLoginUserPermitNull(HttpServletRequest request);

    User getLoginUserPermitNull(Object userObj);

    User getUserByRedis();
    /**
     * 用户注册
     *
     * @param userAddRequest 用户注册请求
     * @return 新用户ID
     */
    long userRegister(UserRegisterRequest userAddRequest);

    /**
     * 用户登录（RPC版，返回token替代session）
     *
     * @param userLoginRequest 用户登录请求
     * @return 登录token
     */
    String userLoginRpc(UserLoginRequest userLoginRequest);

    /**
     * 根据token获取用户信息
     *
     * @param token 用户token
     * @return 用户信息
     */
    User getUserByToken(String token);

    /**
     * 验证token有效性
     *
     * @param token 用户token
     * @return 是否有效
     */
    boolean validateToken(String token);

    /**
     * 用户注销（RPC版）
     *
     * @param token 用户token
     * @return 注销结果
     */
    boolean userLogoutRpc(String token);


    /**
     * 删除用户
     *
     * @param id 用户ID
     * @return 删除结果
     */
    boolean deleteUser(long id);

    /**
     * 更新用户
     *
     * @param userUpdateRequest 用户更新请求
     * @return 更新结果
     */
    boolean updateUser(UserUpdateRequest userUpdateRequest);

    /**
     * 根据ID获取用户
     *
     * @param id 用户ID
     * @return 用户信息
     */
    User getUserById(long id);

    /**
     * 获取用户列表
     *
     * @param userQueryRequest 查询条件
     * @return 用户列表
     */
    List<User> listUser(UserQueryRequest userQueryRequest);




    /**
     * 分页获取用户列表
     *
     * @param userQueryRequest 查询条件
     * @param pageSize 每页大小
     * @param currentPage 当前页码
     * @return 分页用户列表
     */
    List<User> listUserByPage(UserQueryRequest userQueryRequest, int pageSize, int currentPage);

    /**
     * 根据用户账号获取用户
     *
     * @param userAccount 用户账号
     * @return 用户信息
     */
    User getUserByUserAccount(String userAccount);

    /**
     * 验证用户是否为管理员
     *
     * @param userId 用户ID
     * @return 是否为管理员
     */
    boolean isAdmin(long userId);

    boolean isAdmin(User user);

    boolean isAdmin(HttpServletRequest request);

    /**
     * 验证用户是否为管理员（根据token）
     *
     * @param token 用户token
     * @return 是否为管理员
     */
    boolean isAdminByToken(String token);

    /**
     * 统计用户数量
     *
     * @return 用户总数
     */
    long countUser();

    /**
     * 根据ID列表批量获取用户
     *
     * @param userIds 用户ID集合
     * @return 用户列表
     */
    List<User> listByIds(Set<Long> userIds);

    /**
     * 批量根据用户ID查询 用户名
     * @param userIds 用户ID集合
     * @return key=用户ID，value=用户名(userAccount)
     */
    Map<Long, String> batchGetUserName(List<Long> userIds);

    /**
     * 批量根据用户ID查询 用户头像
     * @param userIds 用户ID集合
     * @return key=用户ID，value=头像地址(userAvatar)
     */
    Map<Long, String> batchGetUserAvatar(List<Long> userIds);
}