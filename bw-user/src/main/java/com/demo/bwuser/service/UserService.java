package com.demo.bwuser.service;


import com.demo.bwcommon.common.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.demo.bwmodel.dto.user.UserQueryRequest;
import com.demo.bwmodel.entity.User;
import com.demo.bwmodel.vo.LoginUserVO;
import com.demo.bwmodel.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;


import java.util.List;

/**
 * 用户服务
 *
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param userEmail    用户邮箱
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String userEmail, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 用户登录（返回token）
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @return token
     */
    String userLoginRpc(String userAccount, String userPassword);

    User getUserByRedis();

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    User getLoginUserPermitNull(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    boolean isAdmin(HttpServletRequest request);

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏的用户信息
     *
     * @param userList
     * @return
     */
    List<UserVO> getUserVO(List<User> userList);

    /**
     * 获取查询条件
     *
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * Fuzzy search users by account name (case-insensitive).
     *
     * @param name  keyword
     * @param limit max rows
     * @return user list
     */
    List<UserVO> searchUsersByName(String name, long limit);

    /**
     * Paged fuzzy search users by account name (case-insensitive).
     *
     * @param name     keyword
     * @param pageNum  page number, starts from 1
     * @param pageSize page size
     * @return paged result
     */
    PageResult<UserVO> searchUsersByNamePage(String name, long pageNum, long pageSize);

}
