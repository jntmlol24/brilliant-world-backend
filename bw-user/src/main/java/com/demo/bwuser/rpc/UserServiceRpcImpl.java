package com.demo.bwuser.rpc;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.demo.bwcommon.common.ErrorCode;
import com.demo.bwcommon.exception.BusinessException;

import com.demo.bwcommon.utils.JwtUtil;
import com.demo.bwmodel.dto.user.*;
import com.demo.bwmodel.entity.User;
import com.demo.bwmodel.enums.UserRoleEnum;
import com.demo.bwmodel.rpc.UserServiceRpc;
import com.demo.bwmodel.vo.UserVO;
import com.demo.bwuser.mapper.UserMapper;
import com.demo.bwuser.service.UserService;
import jakarta.annotation.Resource;
import jakarta.json.Json;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.demo.bwcommon.constant.UserConstant.DEFAULT_AVATAR;
import static com.demo.bwcommon.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务Dubbo实现类
 */
@DubboService
@Service
@Slf4j
public class UserServiceRpcImpl implements UserServiceRpc {

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTemplate<String, String> redisTemplate;

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "bw--mansui";

    /**
     * Token前缀
     */
    private static final String TOKEN_PREFIX = "bw_token:";

    /**
     * Token过期时间（7天）
     */
    private static final long TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    /**
     *
     * @return
     */
    @Override
    public User getUserByRedis() {
        if(null == redisTemplate.opsForValue().get(USER_LOGIN_STATE)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Long userId = Long.parseLong(redisTemplate.opsForValue().get(USER_LOGIN_STATE).toString());
        return userService.getById(userId);
    }
    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        currentUser = userService.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }
    @Override
    public User getLoginUser(Object userObj) {
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        currentUser = userService.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param request
     * @return
     */
    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            return null;
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        return userService.getById(userId);
    }
    /**
     * 获取当前登录用户（允许未登录）
     *
     * @param userObj
     * @return
     */
    @Override
    public User getLoginUserPermitNull(Object userObj) {
        // 先判断是否已登录
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            return null;
        }
        // 从数据库查询（追求性能的话可以注释，直接走缓存）
        long userId = currentUser.getId();
        return userService.getById(userId);
    }

    @Override
    public long userRegister(UserRegisterRequest userAddRequest) {
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        
        String userAccount = userAddRequest.getUserAccount();
        String userPassword = userAddRequest.getUserPassword();
        String userEmail = userAddRequest.getUserEmail();
        String checkPassword = userAddRequest.getCheckPassword();

        // 使用原有的UserService进行注册
        return userService.userRegister(userAccount, userPassword, userEmail, checkPassword);
    }

    @Override
    public String userLoginRpc(UserLoginRequest userLoginRequest) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();

        // 参数校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }

        // 加密密码
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());

        // 查询用户
        User user = userService.lambdaQuery()
                .eq(User::getUserAccount, userAccount)
                .eq(User::getUserPassword, encryptPassword)
                .one();

        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }

        // 生成token
        String token = JwtUtil.generateToken(String.valueOf(user.getId()));
        
        // 将用户信息存入Redis（序列化为JSON字符串）
        String redisKey = TOKEN_PREFIX + token;
        String userJson = JSON.toJSONString(user);
        log.info("user info to redis: {}", userJson);
        redisTemplate.opsForValue().set(redisKey, userJson, TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);

        return token;
    }

    @Override
    public User getUserByToken(String token) {
        if (StringUtils.isBlank(token)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "token不能为空");
        }

        // 验证token格式
        if (!JwtUtil.validateToken(token)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "token无效");
        }

        // 从Redis获取用户信息
        String redisKey = TOKEN_PREFIX + token;
        String userJson = redisTemplate.opsForValue().get(redisKey);
        User user = null;
        if (userJson != null) {
            try {
                user = JSON.parseObject(userJson, User.class);
            } catch (Exception e) {
                log.warn("Failed to parse user JSON from Redis, will fallback to database: {}", e.getMessage());
                // If parsing fails (e.g., old non-JSON data), treat it as null and fallback to database
                user = null;
            }
        }

        if (user == null) {
            // 如果Redis中没有，从数据库查询并缓存
            try {
                long userId = Long.parseLong(JwtUtil.extractUserId(token));
                user = userService.getById(userId);
                if (user != null) {
                    String cachedUserJson = JSON.toJSONString(user);
                    redisTemplate.opsForValue().set(redisKey, cachedUserJson, TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);
                }
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "token格式错误");
            }
        }

        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        return user;
    }

    @Override
    public boolean validateToken(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }

        // 验证JWT token
        if (!JwtUtil.validateToken(token)) {
            return false;
        }

        // 检查Redis中是否存在
        String redisKey = TOKEN_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    @Override
    public boolean userLogoutRpc(String token) {
        if (StringUtils.isBlank(token)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "token不能为空");
        }

        // 从Redis中删除token
        String redisKey = TOKEN_PREFIX + token;
        return Boolean.TRUE.equals(redisTemplate.delete(redisKey));
    }

    @Override
    public boolean deleteUser(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ID不合法");
        }
        return userService.removeById(id);
    }

    @Override
    public boolean updateUser(UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }

        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        return userService.updateById(user);
    }

    @Override
    public User getUserById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ID不合法");
        }
        return userService.getById(id);
    }

    @Override
    public List<User> listUser(UserQueryRequest userQueryRequest) {
        return userService.list(userService.getQueryWrapper(userQueryRequest));
    }

    @Override
    public List<User> listUserByPage(UserQueryRequest userQueryRequest, int pageSize, int currentPage) {
        if (pageSize <= 0 || currentPage <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不合法");
        }

        var page = userService.page(
                new Page<>(currentPage, pageSize),
                userService.getQueryWrapper(userQueryRequest)
        );

        return page.getRecords();
    }

    @Override
    public User getUserByUserAccount(String userAccount) {
        if (StringUtils.isBlank(userAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号不能为空");
        }
        return userService.lambdaQuery()
                .eq(User::getUserAccount, userAccount)
                .one();
    }

    @Override
    public boolean isAdmin(long userId) {
        User user = userService.getById(userId);
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return isAdmin(user);
    }

    @Override
    public boolean isAdminByToken(String token) {
        User user = getUserByToken(token);
        return isAdmin(user.getId());
    }

    @Override
    public long countUser() {
        return userService.count();
    }

    @Override
    public List<User> listByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        return userService.listByIds(userIds);
    }


    /**
     * 批量查询用户名
     */
    @Override
    public Map<Long, String> batchGetUserName(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        // 1. 批量查询未删除的用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, userIds)
                .eq(User::getIsDelete, 0) // 逻辑删除过滤
                .select(User::getId, User::getUserAccount); // 只查需要的字段，提升性能

        List<User> userList = userMapper.selectList(wrapper);

        // 2. 转成Map：key=用户ID，value=用户名
        return userList.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        User::getUserAccount,
                        // 重复ID覆盖
                        (oldValue, newValue) -> newValue
                ));
    }

    /**
     * 批量查询用户头像
     */
    @Override
    public Map<Long, String> batchGetUserAvatar(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        // 1. 批量查询未删除的用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, userIds)
                .eq(User::getIsDelete, 0)
                .select(User::getId, User::getUserAvatar);

        List<User> userList = userMapper.selectList(wrapper);

        // 2. 转成Map：key=用户ID，value=头像
        return userList.stream()
                .filter(user -> user != null && user.getId() != null)
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user.getUserAvatar() != null && !user.getUserAvatar().isEmpty()
                                ? user.getUserAvatar()
                                : DEFAULT_AVATAR
                ));
    }
}