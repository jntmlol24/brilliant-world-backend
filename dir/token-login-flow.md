# 用户登录与Token生成完整流程

## 1. 登录请求发起

### 1.1 前端登录请求
- **请求接口**：`POST /api/user/login/rpc`
- **请求方式**：HTTP POST
- **请求体**：
  ```json
  {
    "userAccount": "用户名",
    "userPassword": "密码"
  }
  ```
- **前端代码示例**：
  ```javascript
  // src/api/user.ts
  export const userApi = {
    loginWithToken: (data) => {
      return apiClient.post('/user/login/rpc', data)
    }
  }
  
  // 登录处理
  const handleLogin = async () => {
    const response = await userApi.loginWithToken({
      userAccount: loginForm.userAccount,
      userPassword: loginForm.userPassword
    })
    if (response.code === 0) {
      localStorage.setItem('user_token', response.data)
      // 其他处理...
    }
  }
  ```

## 2. 服务器端验证过程

### 2.1 控制器层处理
- **类**：`UserController`
- **方法**：`userLoginRpc`
- **验证逻辑**：
  1. 检查请求参数是否为空
  2. 提取用户名和密码
  3. 调用 `UserService.userLoginRpc` 方法

### 2.2 服务层处理
- **类**：`UserServiceImpl`
- **方法**：`userLoginRpc`
- **验证逻辑**：
  1. 参数校验（用户名长度≥4，密码长度≥8）
  2. 密码加密（MD5 + 盐值）
  3. 查询用户是否存在
  4. 调用 `UserServiceRpcImpl.userLoginRpc` 生成token

### 2.3 RPC服务处理
- **类**：`UserServiceRpcImpl`
- **方法**：`userLoginRpc`
- **验证逻辑**：
  1. 参数校验
  2. 密码加密
  3. 查询用户
  4. 生成token
  5. 存储用户信息到Redis

## 3. Token生成机制

### 3.1 JWT Token生成
- **工具类**：`JwtUtil`
- **方法**：`generateToken`
- **加密算法**：HMAC SHA256
- **Token组成**：
  - 头部（Header）：包含算法信息
  - 载荷（Payload）：包含用户ID、发行者、发行时间、过期时间
  - 签名（Signature）：使用密钥签名

### 3.2 过期时间设置
- **过期时间**：7天
- **配置**：`EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000L`

### 3.3 密钥管理
- **密钥**：`brillian-world-jwt-secret-key-2025-spring-boot-dubbo-integration`
- **注意**：生产环境应从配置文件中读取

## 4. Token返回方式

### 4.1 响应格式
- **响应结构**：`BaseResponse<String>`
- **成功响应**：
  ```json
  {
    "code": 0,
    "data": "eyJhbGciOiJIUzI1NiJ9...",
    "message": "success"
  }
  ```
- **失败响应**：
  ```json
  {
    "code": 40000,
    "message": "用户不存在或密码错误"
  }
  ```

## 5. 客户端存储Token

### 5.1 存储位置
- **存储方式**：localStorage
- **存储键名**：`user_token`

### 5.2 存储代码
```javascript
// 登录成功后存储token
localStorage.setItem('user_token', response.data)

// 获取token
const token = localStorage.getItem('user_token')
```

### 5.3 安全考虑
- **注意**：localStorage存在XSS攻击风险
- **建议**：生产环境可考虑使用HttpOnly Cookie

## 6. 后续API请求中Token使用

### 6.1 请求拦截器
- **前端拦截器**：
  ```javascript
  // src/api/index.ts
  apiClient.interceptors.request.use(config => {
    const token = localStorage.getItem('user_token')
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  })
  ```

### 6.2 后端验证
- **验证类**：`ImAuthHelper`
- **方法**：`resolveToken`
- **验证流程**：
  1. 从请求头（Authorization）获取token
  2. 从请求参数获取token（备用）
  3. 调用 `UserServiceRpc.getUserByToken` 验证token

### 6.3 RPC验证
- **类**：`UserServiceRpcImpl`
- **方法**：`getUserByToken`
- **验证流程**：
  1. 验证token格式
  2. 从Redis获取用户信息
  3. 如果Redis中没有，从数据库查询并缓存
  4. 返回用户信息

## 7. Token管理

### 7.1 Redis存储
- **存储键**：`bw_token:{token}`
- **存储值**：用户信息（JSON字符串）
- **过期时间**：7天

### 7.2 Token验证
- **JWT验证**：验证签名和过期时间
- **Redis验证**：检查token是否存在于Redis

### 7.3 Token刷新
- **方法**：`JwtUtil.refreshToken`
- **流程**：提取用户ID，生成新token

## 8. 错误处理

### 8.1 常见错误
- **token为空**：`token不能为空`
- **token无效**：`token无效`
- **token过期**：`token已过期`
- **用户不存在**：`用户不存在或密码错误`

### 8.2 错误响应
- **状态码**：40000（参数错误）、40100（未登录）
- **响应格式**：
  ```json
  {
    "code": 40100,
    "message": "token无效"
  }
  ```

## 9. 安全最佳实践

1. **密码加密**：使用MD5 + 盐值加密
2. **Token安全**：使用JWT + Redis双重验证
3. **过期时间**：设置合理的token过期时间
4. **密钥管理**：生产环境使用安全的密钥管理
5. **输入验证**：对所有输入参数进行严格验证
6. **HTTPS**：使用HTTPS传输敏感信息
7. **错误处理**：不暴露详细的错误信息给客户端

## 10. 流程图

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ 前端登录请求 │────>│ 控制器层处理 │────>│ 服务层处理  │────>│ RPC服务处理 │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                                                                 │
                                                                 ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ 前端存储token│<────│ Token返回   │<────│ Redis存储   │<────│ Token生成  │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘

┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│ 后续API请求 │────>│ 请求拦截器  │────>│ 后端验证    │────>│ 业务处理   │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
```

## 11. 代码示例

### 11.1 前端登录代码
```javascript
// 登录请求
const login = async (userAccount, userPassword) => {
  try {
    const response = await axios.post('/api/user/login/rpc', {
      userAccount,
      userPassword
    })
    
    if (response.code === 0) {
      // 存储token
      localStorage.setItem('user_token', response.data)
      console.log('登录成功，token已存储')
      return response.data
    } else {
      throw new Error(response.message)
    }
  } catch (error) {
    console.error('登录失败:', error)
    throw error
  }
}
```

### 11.2 后端Token生成代码
```java
// JwtUtil.generateToken
public static String generateToken(String userId) {
    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + EXPIRATION_TIME);

    return Jwts.builder()
            .setSubject(userId)
            .setIssuer(ISSUER)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(getSigningKey())
            .compact();
}

// UserServiceRpcImpl.userLoginRpc
public String userLoginRpc(UserLoginRequest userLoginRequest) {
    // 参数校验...
    // 密码加密...
    // 查询用户...
    
    // 生成token
    String token = JwtUtil.generateToken(String.valueOf(user.getId()));
    
    // 存储用户信息到Redis
    String redisKey = TOKEN_PREFIX + token;
    String userJson = JSON.toJSONString(user);
    redisTemplate.opsForValue().set(redisKey, userJson, TOKEN_EXPIRE_TIME, TimeUnit.MILLISECONDS);

    return token;
}
```

### 11.3 后端Token验证代码
```java
// ImAuthHelper.resolveToken
public User resolveToken(HttpServletRequest request) {
    // 从请求头获取token
    String token = request.getHeader("Authorization");
    if (token != null && token.startsWith("Bearer ")) {
        token = token.substring(7);
    }
    
    // 从请求参数获取token（备用）
    if (token == null) {
        token = request.getParameter("token");
    }
    
    if (token == null) {
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "token is required");
    }
    
    // 调用RPC服务验证token
    return userServiceRpc.getUserByToken(token);
}
```

## 12. 总结

本系统采用了JWT + Redis的双重token验证机制，确保了用户认证的安全性和可靠性。主要特点包括：

1. **安全性**：使用HMAC SHA256加密算法，结合盐值密码加密
2. **可靠性**：JWT验证 + Redis存储双重保障
3. **可扩展性**：支持token刷新和过期时间管理
4. **易用性**：前端通过localStorage存储，请求拦截器自动附加
5. **灵活性**：支持从请求头或参数获取token

这种方案既保证了安全性，又提供了良好的用户体验，适合现代Web应用的认证需求。