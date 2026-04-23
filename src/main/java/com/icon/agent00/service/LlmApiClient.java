package com.icon.agent00.service;

import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class LlmApiClient {
    private final ChatClient chatClient;

    // 使用构造器注入 ChatClient.Builder
    public LlmApiClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String callLlmApi(String prompt) throws Exception {

        try {
            String responseContent = this.chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("【大模型原始完整输出】:\n{}", responseContent);

            // 2. 清理模型的 <think> 思考过程标签
            return cleanThinkTags(responseContent);

        } catch (Exception e) {
            // 如果遇到网络不通、503、连接拒绝等问题，Spring AI 会自动抛出异常被这里捕获
            log.error("Spring AI 调用大模型失败", e);
            throw new AppException(ResponseCode.LLM_API_ERROR);
        }
    }

    public String callLlmForJson(String prompt) {
        try {
            return this.chatClient.prompt()
                    .user(prompt)
                    // 强制底层 API 只允许输出 JSON
                    .options(OpenAiChatOptions.builder()
                            .withResponseFormat(new ChatCompletionRequest.ResponseFormat(ChatCompletionRequest.ResponseFormat.Type.JSON_OBJECT))
                            .withTemperature(0.1)
                            .build())
                    .call()
                    .content();

        } catch (Exception e) {
            log.error("大模型生成 JSON 失败", e);
            return null;
        }
    }

    /**
     * 提取出你之前写的过滤 <think> 标签的逻辑
     */
    private String cleanThinkTags(String finalRes) throws AppException {
        if (!StringUtils.hasText(finalRes)) {
            throw new AppException(ResponseCode.LLM_PARSE_ERROR.getCode(), "解析异常：模型返回的数据为空");
        }

        int thinkEndIndex = finalRes.indexOf("</think>");
        if (thinkEndIndex != -1) {
            // 找到了 </think>，直接把这个标签及其之前的所有“草稿”全部丢弃
            return finalRes.substring(thinkEndIndex + "</think>".length()).trim();
        } else {
            // 没找到 think 标签，直接返回原文
            return finalRes.trim();
        }
    }
}
