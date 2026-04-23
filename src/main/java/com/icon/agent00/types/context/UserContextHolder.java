package com.icon.agent00.types.context;

public class UserContextHolder {
    // 存放当前线程的用户 ID (也可以存放一个包含更多信息的 UserDTO 对象)
    private static final ThreadLocal<String> USER_ID_THREAD_LOCAL = new ThreadLocal<>();

    public static void setUserId(String userId) {
        USER_ID_THREAD_LOCAL.set(userId);
    }

    public static String getUserId() {
        return USER_ID_THREAD_LOCAL.get();
    }

    // 极其重要：防止内存泄漏和线程池串数据！
    public static void clear() {
        USER_ID_THREAD_LOCAL.remove();
    }
}
