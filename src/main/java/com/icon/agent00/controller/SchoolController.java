package com.icon.agent00.controller;

import com.icon.agent00.entity.dto.RequirementRequest;
import com.icon.agent00.response.Response;
import com.icon.agent00.service.SchoolLlmService;
import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@Tag(name = "智能择校接口", description = "基于大模型的学校分析与推荐服务")
@RestController

@RequestMapping("/api/school")
public class SchoolController {

    @Autowired
    private SchoolLlmService schoolLlmService;

//    @Operation(summary = "通过 URL 参数获取推荐", description = "接收普通的查询字符串参数，返回大模型的择校建议文本")
//    @GetMapping("/recommendURL")
//    public Map<String, Object> recommendSchool(
//            @Parameter(description = "择校需求描述，如：我想找北京的公立理工科学校", required = true)
//            @RequestParam("requirement") String userRequirement) throws Exception {
//        return schoolLlmService.getRecommendation(userRequirement);
//    }

    @Operation(summary = "通过 JSON 对象获取推荐", description = "接收包含需求的 JSON 实体，适用于更复杂或长文本的交互场景")
    @PostMapping("/recommend")
    public Response<Object> recommendSchoolFromJson(@RequestHeader(value = "token", required = false) String sessionId,
                                                       @RequestBody RequirementRequest request) throws Exception {
        try{
            if (sessionId == null || sessionId.trim().isEmpty()) {
                // TODO 增加登录功能，将token传入上下文（例如ThreadLocal），从上下文中获取id
                sessionId = "guest-session-default";
            }

            String userRequirement = request.getRequirement();
            String recommendation = schoolLlmService.getRecommendation(sessionId, userRequirement);
            return Response.builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(recommendation)
                    .build();

        }catch (AppException e) {
            return Response.builder()
                    .code(e.getCode())
                    .info(e.getInfo())
                    .data(null)
                    .build();

        } catch (Exception e) {
            // 兜底处理：处理其他系统级别的报错
            e.printStackTrace();
            return Response.builder()
                    .code("500")
                    .info("系统错误：" + e.getMessage())
                    .data(null)
                    .build();
        }
    }
}
