package com.icon.agent00.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icon.agent00.entity.ChatMessageDAO;
import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
/**
 * 数据库保存长期记忆
 * 将被淘汰的短期记忆概括为一段摘要，注入Prompt
 */
public class LongMemoryService {


    private static final String SUMMARY_MEMORY_PREFIX = "llm:summary:memory:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final ChatClient chatClient;

    public LongMemoryService(ChatClient.Builder chatClientBuilder) {
        // 构建一个干净的客户端，不挂载任何 Advisor
        this.chatClient = chatClientBuilder.build();
    }


    /**
     * 获取当前摘要
     */
    public String getSummary(String sessionId) {
        return stringRedisTemplate.opsForValue().get(SUMMARY_MEMORY_PREFIX + sessionId);
    }

    /**
     * 更新摘要（由 Service 调用 LLM 生成后存入）
     */
    public void saveSummary(String sessionId, String newSummary) {
        stringRedisTemplate.opsForValue().set(SUMMARY_MEMORY_PREFIX + sessionId, newSummary, 24, TimeUnit.HOURS);
    }

    /**
     * 记忆压缩
     */
    public void handleMemoryCompression(String sessionId, String evictedMessages) throws Exception {

        try {
            String oldSummary = getSummary(sessionId);

            long startTime = System.currentTimeMillis();
            log.info("开始为用户 {} 进行记忆压缩...", sessionId);

//            // 构建压缩 Prompt
//            String summaryPrompt = String.format(
//                    "请将以下【新废弃的对话】合并到【现有摘要】中，生成一段新的连贯上下文摘要，字数控制在200字以内。\n现有摘要：%s\n新废弃的对话：%s",
//                    oldSummary != null ? oldSummary : "无", evictedMessages
//            );

            String summaryPrompt = String.format(
                    "你是一个专业的【择校咨询记忆管理专家】。你的任务是从用户的对话中提取关键信息，并更新用户的【长期记忆 JSON 画像】。\n\n" +
                            "【现有画像】:\n%s\n\n" +
                            "【近期对话】:\n%s\n\n" +
                            "【更新规则】:\n" +
                            "1. 严格过滤废话：忽略打招呼、口语词和无意义的闲聊。\n" +
                            "2. 信息提取与覆盖：提取新信息并更新到现有画像中。改变主意时，用新信息覆盖旧信息。\n" +
                            "请输出更新后的 JSON：",
                    oldSummary != null ? oldSummary : "{}",
                    evictedMessages
            );

            // 调用大模型生成新摘要
            String newSummary = this.chatClient.prompt()
                    .system(summaryPrompt)
                    .options(OpenAiChatOptions.builder()
                            .responseFormat(new ResponseFormat())
                            .temperature(0.1)
                            .build())
                    .call()
                    .content();

            // 存回 Redis
            saveSummary(sessionId, newSummary);
            log.info("记忆压缩完成，耗时: {}ms，新摘要已保存: {}", (System.currentTimeMillis() - startTime), newSummary);
        } catch (Exception e) {
            log.error("记忆压缩发生异常", e);
            throw new AppException(ResponseCode.ERROR_IN_SUMMARY);
        }
    }

    public String convertToText(List<Object> rawMessages) {
        StringBuilder sb = new StringBuilder();
        ObjectMapper mapper = new ObjectMapper();

        for (Object obj : rawMessages) {
            // 使用 ObjectMapper 将 LinkedHashMap 还原为 DAO
            ChatMessageDAO dao = mapper.convertValue(obj, ChatMessageDAO.class);

            // 格式化为：[USER]: 内容 或 [ASSISTANT]: 内容
            sb.append("[").append(dao.getType().toUpperCase()).append("]: ")
                    .append(dao.getText()).append("\n");
        }
        return sb.toString();
    }

}
