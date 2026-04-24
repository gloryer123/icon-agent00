package com.icon.agent00.service;

import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class LlmApiClient {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    @Value("${app.chat.memory.retain-threshold}")
    private int retainThreshold;

    // 使用构造器注入 ChatClient.Builder
    public LlmApiClient(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemory = chatMemory;

    }

    /**
     * @param sessionId 用户ID，用于隔离不同人的聊天记录
     * @param systemPrompt 包含规则、数据库、长期画像的系统指令
     * @param userRequest 用户当前的提问
     */
    public String callLlmApi(String sessionId, String systemPrompt, String userRequest) throws AppException {
        try {

            ChatResponse response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userRequest)
                    .advisors(new MessageChatMemoryAdvisor(chatMemory, sessionId, retainThreshold))
                    .call()
                    .chatResponse();

            if (response == null || response.getResult() == null) {
                throw new AppException(ResponseCode.LLM_PARSE_ERROR);
            }

            // 3. 提取内容
            String fullContent = response.getResult().getOutput().getContent();
            String answer = fullContent;

            int thinkEndIndex = fullContent.indexOf("</think>");
            if (thinkEndIndex != -1) {
                answer = fullContent.substring(thinkEndIndex + 8).trim();
            }

            log.info("【大模型最终输出】: {}", answer);
            return answer;

        } catch (Exception e) {
                log.error("本地大模型调用失败", e);
            throw new AppException(ResponseCode.LLM_API_ERROR);
        }
    }

}
