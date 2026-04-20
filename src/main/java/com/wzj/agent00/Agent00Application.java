package com.wzj.agent00;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
// 告诉 Spring Boot 去哪个包下扫描 MyBatis 的 Mapper 接口
@MapperScan("com.wzj.agent00.mapper")
public class Agent00Application {

    public static void main(String[] args) {
        SpringApplication.run(Agent00Application.class, args);
        System.out.println("====== Spring Boot 智能择校服务已启动 ======");
        System.out.println("请在浏览器访问测试接口: http://localhost:8080/api/school/recommend?requirement=我想找国内的理工科学校，预算在1万以内");
    }
}
