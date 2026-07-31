package com.japy.common;

/**
 * 当前登录用户上下文（ThreadLocal）
 */
public class UserContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> NICKNAME = new ThreadLocal<>();

    public static void set(Long userId, String nickname) {
        USER_ID.set(userId);
        NICKNAME.set(nickname);
    }

    public static Long getUserId() { return USER_ID.get(); }
    public static String getNickname() { return NICKNAME.get(); }

    public static void clear() {
        USER_ID.remove();
        NICKNAME.remove();
    }
}
