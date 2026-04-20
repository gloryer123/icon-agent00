package com.wzj.agent00.controller;

import com.wzj.agent00.service.SchoolLlmService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/school")
public class SchoolController {
    @Autowired
    private SchoolLlmService schoolLlmService;

    /**
     * 接收用户需求并返回大模型推荐结果
     * 访问示例: GET http://localhost:8080/api/school/recommend?requirement=我想找国外的理工科学校
     */
    @GetMapping("/recommend")
    public String recommendSchool(@RequestParam("requirement") String userRequirement) {
        try {
            // 调用 Service 层处理业务
            String recommendation = schoolLlmService.getRecommendation(userRequirement);
            return recommendation;
        } catch (Exception e) {
            e.printStackTrace();
            return "处理推荐请求时发生异常：" + e.getMessage();
        }
    }
}
