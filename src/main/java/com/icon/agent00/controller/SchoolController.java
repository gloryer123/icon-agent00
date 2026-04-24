package com.icon.agent00.controller;

import com.icon.agent00.entity.dto.RequirementRequest;
import com.icon.agent00.response.Response;
import com.icon.agent00.service.LlmApiClientAlibaba;
import com.icon.agent00.service.ReActAgent;
import com.icon.agent00.service.SchoolLlmService;
import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "智能择校接口", description = "基于大模型的学校分析与推荐服务")
@RestController

@RequestMapping("/api/school")
public class SchoolController {

    @Autowired
    private SchoolLlmService schoolLlmService;

    @Autowired
    private ReActAgent reActAgent;

    @Operation(summary = "通过 JSON 对象获取推荐", description = "接收包含需求的 JSON 实体，适用于更复杂或长文本的交互场景")
    @PostMapping("/recommend")
    public Response<Object> recommendSchoolFromJson(@RequestHeader(value = "token", defaultValue = "test_user") String sessionId,
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

    /**
     * 获取指定会话的历史记录
     * 访问示例: GET http://localhost:8080/api/chat/history?sessionId=user-123
     */
    @GetMapping("/history")
    public List<Map<String, String>> getHistory(@RequestParam("sessionId") String sessionId) {
        // 1. 从 Agent 获取底层状态快照中的原生消息列表
        List<Message> rawMessages = reActAgent.getChatHistory(sessionId);

        // 2. 转换为前端友好的结构 (DTO / Map)
        List<Map<String, String>> responseList = new ArrayList<>();

        for (Message msg : rawMessages) {
            Map<String, String> messageData = new HashMap<>();

            // 获取角色类型 (通常为 user, assistant, system)
            messageData.put("role", msg.getMessageType().getValue());
            // 获取具体文本内容
            messageData.put("content", msg.getText());

            responseList.add(messageData);
        }

        return responseList;
    }
}
