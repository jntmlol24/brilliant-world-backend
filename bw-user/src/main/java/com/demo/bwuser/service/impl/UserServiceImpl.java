package com.demo.bwuser.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.demo.bwcommon.common.ErrorCode;
import com.demo.bwcommon.common.PageResult;
import com.demo.bwcommon.constant.CommonConstant;
import com.demo.bwcommon.exception.BusinessException;
import com.demo.bwcommon.utils.JwtUtil;
import com.demo.bwcommon.utils.SqlUtils;
import com.demo.bwmodel.dto.user.UserLoginRequest;
import com.demo.bwmodel.dto.user.UserQueryRequest;
import com.demo.bwmodel.entity.User;
import com.demo.bwmodel.enums.UserRoleEnum;
import com.demo.bwmodel.vo.LoginUserVO;
import com.demo.bwmodel.vo.UserVO;
import com.demo.bwuser.mapper.UserMapper;
import com.demo.bwuser.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.demo.bwcommon.constant.UserConstant.USER_LOGIN_STATE;



@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "bw--mansui";
    private static final long MAX_SEARCH_LIMIT = 100;
    private static final long MAX_PAGE_SIZE = 100;
    private static final int MAX_NAME_LENGTH = 64;
    private static final String NAME_ALLOWED_REGEX = "^[a-zA-Z0-9_\\u4e00-\\u9fa5\\-\\s]+$";

    /**
     * Token前缀
     */
    private static final String TOKEN_PREFIX = "bw_token:";

    /**
     * Token过期时间（7天）
     */
    private static final long TOKEN_EXPIRE_TIME = 7 * 24 * 60 * 60 * 1000L;
    @Resource
    private RedisTemplate<String, String> redisTemplate;


    @Override
    public long userRegister(String userAccount, String userPassword,String userEmail, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            // 2. 加密
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            // 3. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            user.setUserEmail(userEmail);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // redis 记录用户的登录态
        redisTemplate.opsForValue().set(USER_LOGIN_STATE, user.getId().toString());
        return this.getLoginUserVO(user);
    }

    @Override
    public String userLoginRpc(String userAccount, String userPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 3. 调用RPC服务生成token
        com.demo.bwmodel.dto.user.UserLoginRequest loginRequest = new com.demo.bwmodel.dto.user.UserLoginRequest();
        loginRequest.setUserAccount(userAccount);
        loginRequest.setUserPassword(userPassword);
        return userLoginRpc(loginRequest);
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
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public User getUserByRedis() {
        if(null == redisTemplate.opsForValue().get(USER_LOGIN_STATE)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        Long userId = Long.parseLong(redisTemplate.opsForValue().get(USER_LOGIN_STATE));
        return this.getById(userId);
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
        return this.getById(userId);
    }

    /**
     * 是否为管理员
     *
     * @param request
     * @return
     */
    @Override
    public boolean isAdmin(HttpServletRequest request) {
        // 仅管理员可查询
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User user = (User) userObj;
        return isAdmin(user);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     *
     * @param request
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (request.getSession().getAttribute(USER_LOGIN_STATE) == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        redisTemplate.delete(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollectionUtils.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        Boolean fuzzySearch = userQueryRequest.getFuzzySearch();
        
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        
        if (StringUtils.isNotBlank(userAccount)) {
            if (Boolean.TRUE.equals(fuzzySearch)) {
                queryWrapper.apply("LOWER(userAccount) LIKE CONCAT('%',{0},'%')", userAccount.toLowerCase(Locale.ROOT));
            } else {
                queryWrapper.eq("userAccount", userAccount);
            }
        }
        
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public List<UserVO> searchUsersByName(String name, long limit) {
        String keyword = validateNameKeyword(name);
        if (limit <= 0 || limit > MAX_SEARCH_LIMIT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "limit must be between 1 and 100");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "userAccount", "userAvatar", "userRole", "createTime");
        queryWrapper.apply("LOWER(userAccount) LIKE CONCAT('%',{0},'%')", keyword.toLowerCase(Locale.ROOT));
        queryWrapper.orderByDesc("createTime");
        queryWrapper.last("LIMIT " + limit);
        List<User> users = this.list(queryWrapper);
        return getUserVO(users);
    }

    @Override
    public PageResult<UserVO> searchUsersByNamePage(String name, long pageNum, long pageSize) {
        String keyword = validateNameKeyword(name);
        if (pageNum <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "pageNum must be greater than 0");
        }
        if (pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "pageSize must be between 1 and 100");
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("id", "userAccount", "userAvatar", "userRole", "createTime");
        queryWrapper.apply("LOWER(userAccount) LIKE CONCAT('%',{0},'%')", keyword.toLowerCase(Locale.ROOT));
        queryWrapper.orderByDesc("createTime");

        Page<User> page = this.page(new Page<>(pageNum, pageSize), queryWrapper);
        long totalPages = page.getSize() == 0 ? 0 : (page.getTotal() + page.getSize() - 1) / page.getSize();
        return new PageResult<>(pageNum, pageSize, page.getTotal(), totalPages, getUserVO(page.getRecords()));
    }

    private String validateNameKeyword(String name) {
        if (StringUtils.isBlank(name)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "name cannot be blank");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "name length cannot exceed 64");
        }
        // Keep keyword characters predictable to prevent wildcard abuse and risky input.
        if (!trimmed.matches(NAME_ALLOWED_REGEX)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "name contains unsupported characters");
        }
        if (SqlUtils.hasSqlInjectionRisk(trimmed)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "name contains risky SQL content");
        }
        return trimmed;
    }
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
        User user = this.lambdaQuery()
                .eq(User::getUserAccount, userAccount)
                .eq(User::getUserPassword, encryptPassword)
                .one();

        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }

        // 生成token
        String token = JwtUtil.generateToken(String.valueOf(user.getId()));

        // 将用户信息存入Redis
        String redisKey = TOKEN_PREFIX + token;
        String userJson = JSON.toJSONString(user);
        redisTemplate.opsForValue().set(redisKey, userJson, TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);

        return token;
    }
}
