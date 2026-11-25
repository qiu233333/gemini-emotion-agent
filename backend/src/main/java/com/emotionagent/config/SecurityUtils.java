package com.emotionagent.config;

import com.emotionagent.common.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {
    private SecurityUtils() {}

    public static SecurityUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityUser user)) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return user;
    }

    public static Long currentUserId() {
        return currentUser().getUserId();
    }

    public static String currentRole() {
        return currentUser().getRole();
    }
}
