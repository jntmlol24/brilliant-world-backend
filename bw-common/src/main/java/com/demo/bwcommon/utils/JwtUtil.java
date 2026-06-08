package com.demo.bwcommon.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT工具类
 */
@Slf4j
public class JwtUtil {

    /**
     * JWT密钥（生产环境应从配置文件中读取）
     */
    private static final String SECRET_KEY = "brillian-world-jwt-secret-key-2025-spring-boot-dubbo-integration";

    /**
     * JWT过期时间（7天）
     */
    private static final long EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000L;

    /**
     * JWT发行者
     */
    private static final String ISSUER = "brillian-world";

    /**
     * 生成SecretKey
     */
    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }

    /**
     * 生成JWT token
     *
     * @param userId 用户ID
     * @return JWT token
     */
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

    /**
     * 从token中提取用户ID
     *
     * @param token JWT token
     * @return 用户ID
     */
    public static String extractUserId(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (Exception e) {
            log.error("Failed to extract user ID from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证token是否有效
     *
     * @param token JWT token
     * @return 是否有效
     */
    public static boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token expired: {}", e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
            return false;
        } catch (JwtException e) {
            log.warn("JWT token validation failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Token validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取token过期时间
     *
     * @param token JWT token
     * @return 过期时间
     */
    public static Date getExpirationDate(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getExpiration();
        } catch (Exception e) {
            log.error("Failed to get expiration date from token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查token是否即将过期（在指定时间内过期）
     *
     * @param token JWT token
     * @param minutes 过期前分钟数
     * @return 是否即将过期
     */
    public static boolean isTokenExpiringSoon(String token, int minutes) {
        try {
            Date expiration = getExpirationDate(token);
            if (expiration == null) {
                return true;
            }
            
            long timeRemaining = expiration.getTime() - System.currentTimeMillis();
            return timeRemaining < minutes * 60 * 1000L;
        } catch (Exception e) {
            log.error("Failed to check if token is expiring soon: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 刷新token（生成新的token）
     *
     * @param token 原token
     * @return 新token
     */
    public static String refreshToken(String token) {
        String userId = extractUserId(token);
        if (userId == null) {
            return null;
        }
        return generateToken(userId);
    }
}