package com.icon.agent00.types.interceptor;

import com.icon.agent00.types.context.UserContextHolder;
import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 在到达 Controller 之前执行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws AppException {
        // 1. 从请求头获取 token
        String token = request.getHeader("token");

        // 2. 校验 token 是否为空
        if (!StringUtils.hasText(token)) {
            throw new AppException(ResponseCode.NO_TOKEN);
        }

        // 3. 去 Redis 校验 token 是否存在
        String redisKey = "login:token:" + token;
        String userId = stringRedisTemplate.opsForValue().get(redisKey);

        if (!StringUtils.hasText(userId)) {
            throw new AppException(ResponseCode.NO_USER);
        }

        // 4. Token有效，为了防止过期，顺手给它续命 2 小时 (只要用户一直在用，就不会掉线)
        stringRedisTemplate.expire(redisKey, 2, TimeUnit.HOURS);

        // 5. 将取出的 userId 塞入当前线程的上下文中
        UserContextHolder.setUserId(userId);

        // 6. 放行请求，继续走向 Controller
        return true;
    }

    /**
     * 在整个请求结束之后执行 (无论成功还是抛异常都会执行)
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 【极其关键】请求结束后，必须清空 ThreadLocal！
        // 因为 Tomcat 的线程是池化的，如果被复用时不清理，会导致下一个请求串号，并引发内存泄漏。
        UserContextHolder.clear();
    }
}
