package com.wzj.agent00.controller;

import com.wzj.agent00.entity.dto.RequirementRequest;
import com.wzj.agent00.service.SchoolLlmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;


@Tag(name = "智能择校接口", description = "基于大模型的学校分析与推荐服务")
@RestController
@RequestMapping("/api/school")
public class SchoolController {

    @Autowired
    private SchoolLlmService schoolLlmService;

    @Operation(summary = "通过 URL 参数获取推荐", description = "接收普通的查询字符串参数，返回大模型的择校建议文本")
    @GetMapping("/recommend")
    public Map<String, Object> recommendSchool(
            @Parameter(description = "择校需求描述，如：我想找北京的公立理工科学校", required = true)
            @RequestParam("requirement") String userRequirement) throws Exception {
        return schoolLlmService.getRecommendation(userRequirement);
    }

    @Operation(summary = "通过 JSON 对象获取推荐", description = "接收包含需求的 JSON 实体，适用于更复杂或长文本的交互场景")
    @PostMapping("/recommendjson")
    public Map<String, Object> recommendSchoolFromJson(@RequestBody RequirementRequest request) {
        try {
            String userRequirement = request.getRequirement();
            Map<String, Object> recommendation = schoolLlmService.getRecommendation(userRequirement);
            return recommendation;
        } catch (Exception e) {
            e.printStackTrace();
            // 如果代码出错，我们也手动构建一个规范的 JSON 返回回去
            Map<String, Object> errorMap = new HashMap<>();
            errorMap.put("code", 500);
            errorMap.put("msg", "处理推荐请求时发生异常：" + e.getMessage());
            errorMap.put("data", null);
            return errorMap;
        }
    }
}
