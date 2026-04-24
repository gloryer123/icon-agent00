package com.icon.agent00.service;
import com.alibaba.cloud.ai.memory.redis.RedissonRedisChatMemoryRepository;
import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;


@Service
@Slf4j
public class LlmApiClientAlibaba {

    private final ChatClient chatClient;
    private final MessageWindowChatMemory messageWindowChatMemory;

    public LlmApiClientAlibaba(ChatClient.Builder builder,
                               RedissonRedisChatMemoryRepository redisChatMemoryRepository,
                               @Value("${app.chat.memory.retain-threshold:10}") int retainThreshold) {
        this.messageWindowChatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository)
                .maxMessages(retainThreshold)
                .build();

        this.chatClient = builder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(messageWindowChatMemory)
                                .build()
                )
                .build();
    }

    /**
     * @param sessionId 用户ID，用于隔离不同人的聊天记录
     * @param systemPrompt 包含规则、数据库、长期画像的系统指令
     * @param userRequest 用户当前的提问
     */
    public String callLlmApi(String sessionId, String systemPrompt, String userRequest) throws AppException {
        try {
            // 3. 发起流式动态调用
            String response = chatClient.prompt()
                    .system(systemPrompt) // 每次请求动态覆盖 System Prompt
                    .user(userRequest)
                    .advisors(
                            // 动态绑定当前请求的 conversationId (即 sessionId)
                            advisor -> advisor.param(CONVERSATION_ID, sessionId)
                    )
                    .call()
                    .content();

            // 4. 提取内容并处理 DeepSeek R1 的 <think> 标签逻辑
            int thinkEndIndex = response.indexOf("</think>");
            if (thinkEndIndex != -1) {
                // "</think>" 长度为 8
                response = response.substring(thinkEndIndex + 8).trim();
            }

            log.info("【大模型最终输出】: {}", response);
            return response;

        } catch (Exception e) {
            log.error("本地大模型调用失败", e);
            throw new AppException(ResponseCode.LLM_API_ERROR);
        }
    }

}
