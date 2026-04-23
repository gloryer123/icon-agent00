package com.icon.agent00.controller;

import com.icon.agent00.response.Response;
import com.icon.agent00.types.enums.ResponseCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @PostMapping("/login")
    public Response<Object> login(@RequestParam String username, @RequestParam String password) {
        // 1. 这里假装你去数据库校验了账号密码
        if (!"admin".equals(username) || !"123456".equals(password)) {
            return Response.builder()
                    .code(ResponseCode.NO_SESSION.getCode())
                    .info(ResponseCode.NO_SESSION.getInfo())
                    .data(null)
                    .build();
        }

        // 2. 校验成功，模拟从数据库拿到了这个用户的真实 ID
        String userId = "user-8888"; // 假装这是从数据库查出来的用户 ID

        // 3. 生成一个随机的 Token
        String token = UUID.randomUUID().toString().replace("-", "");

        // 4. 将 Token 存入 Redis，设置 2 小时过期
        // Key: login:token:{token} , Value: userId
        stringRedisTemplate.opsForValue().set("login:token:" + token, userId, 2, TimeUnit.HOURS);

        // 5. 将 Token 返回给前端
        return Response.builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(token)
                .build();
    }
}
