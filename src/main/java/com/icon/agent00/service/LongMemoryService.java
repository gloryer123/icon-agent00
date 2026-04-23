package com.icon.agent00.service;

import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
/**
 * 数据库保存长期记忆
 * 将被淘汰的短期记忆概括为一段摘要，注入Prompt
 */
public class LongMemoryService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private LlmApiClient llmApiClient;

    private static final String SUMMARY_MEMORY_PREFIX = "llm:summary:memory:";

    private static final String JSON_TEMPLATE = "{\n" +
            "  \"user_profile\": {\"education_bg\": \"\", \"gpa\": \"\", \"language_score\": \"\", \"budget\": \"\"},\n" +
            "  \"preferences\": {\"target_country\": [], \"target_major\": [], \"dealbreakers\": []},\n" +
            "  \"interaction_status\": {\"liked_schools\": [], \"rejected_schools\": [], \"current_intent\": \"\"}\n" +
            "}";

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
     * 异步执行记忆压缩
     */
    @Async
    public void handleMemoryCompression(String sessionId, String evictedMessages, String oldSummary) throws Exception {
        if (!StringUtils.hasText(evictedMessages)) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            log.info("【异步任务】开始为用户 {} 进行记忆压缩...", sessionId);

//            // 构建压缩 Prompt
//            String summaryPrompt = String.format(
//                    "请将以下【新废弃的对话】合并到【现有摘要】中，生成一段新的连贯上下文摘要，字数控制在200字以内。\n现有摘要：%s\n新废弃的对话：%s",
//                    oldSummary != null ? oldSummary : "无", evictedMessages
//            );

            String summaryPrompt = String.format(
                    "你是一个专业的【择校咨询记忆管理专家】。你的任务是从用户的对话中提取关键信息，并更新用户的【长期记忆 JSON 画像】。\n\n" +
//                            "【标准画像结构】:\n%s\n\n" +
                            "【现有画像】:\n%s\n\n" +
                            "【近期对话】:\n%s\n\n" +
                            "【更新规则】:\n" +
                            "1. 严格过滤废话：忽略打招呼、口语词和无意义的闲聊。\n" +
                            "2. 信息提取与覆盖：提取新信息并更新到现有画像中。改变主意时，用新信息覆盖旧信息。\n" +
//                            "3. 保持结构：必须严格遵循【标准画像结构】的字段名，如果没有对应信息请保持为空字符串或空数组。\n" +
                            "请输出更新后的 JSON：",
//                    JSON_TEMPLATE,
                    oldSummary != null ? oldSummary : "{}",
                    evictedMessages
            );

            // 调用大模型生成新摘要
            String newSummary = llmApiClient.callLlmForJson(summaryPrompt);

            // 存回 Redis
            saveSummary(sessionId, newSummary);
            log.info("【异步任务】记忆压缩完成，耗时: {}ms，新摘要已保存: {}", (System.currentTimeMillis() - startTime), newSummary);
        } catch (Exception e) {
            log.error("【异步任务】记忆压缩发生异常", e);
            throw new AppException(ResponseCode.ERROR_IN_SUMMARY);
        }
    }


}
