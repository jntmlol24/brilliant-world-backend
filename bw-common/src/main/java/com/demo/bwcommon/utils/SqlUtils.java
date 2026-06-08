package com.demo.bwcommon.utils;


import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SQL 工具
 *
 */
public class SqlUtils {

    private static final Pattern SQL_KEYWORD_PATTERN = Pattern.compile(
            "\\b(select|insert|update|delete|drop|truncate|alter|union|sleep|benchmark|or|and)\\b");

    /**
     * 校验排序字段是否合法（防止 SQL 注入）
     *
     * @param sortField
     * @return
     */
    public static boolean validSortField(String sortField) {
        if (StringUtils.isBlank(sortField)) {
            return false;
        }
        return !StringUtils.containsAny(sortField, "=", "(", ")", " ");
    }

    /**
     * Check whether input contains obvious SQL injection risks.
     *
     * @param input user input
     * @return true if risky
     */
    public static boolean hasSqlInjectionRisk(String input) {
        if (StringUtils.isBlank(input)) {
            return false;
        }
        String normalized = input.toLowerCase(Locale.ROOT);
        if (StringUtils.containsAny(normalized, "'", "\"", ";", "\\", "\u0000")) {
            return true;
        }
        if (normalized.contains("--") || normalized.contains("/*") || normalized.contains("*/")) {
            return true;
        }
        return SQL_KEYWORD_PATTERN.matcher(normalized).find();
    }
}
