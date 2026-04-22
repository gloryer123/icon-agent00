package com.icon.agent00.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
/**
 * redis List保存短期记忆
 */
public class ShortMemoryService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String REDIS_KEY_PREFIX = "llm:chat:memory:";
    private static final int MAX_HISTORY_MESSAGES = 10; // 保留最近的10条对话（5轮）
    private static final long MEMORY_EXPIRE_MINUTES = 30; // 短期记忆保留时间：30分钟

    private static final String HISTORY_PREFIX = "chat:history:";
    private static final String SUMMARY_PREFIX = "chat:summary:";

    /**
     * 获取当前摘要
     */
    public String getSummary(String sessionId) {
        return stringRedisTemplate.opsForValue().get(SUMMARY_PREFIX + sessionId);
    }

    /**
     * 更新摘要（由 Service 调用 LLM 生成后存入）
     */
    public void saveSummary(String sessionId, String newSummary) {
        stringRedisTemplate.opsForValue().set(SUMMARY_PREFIX + sessionId, newSummary, 24, TimeUnit.HOURS);
    }

    /**
     * 追加对话记录到 Redis
     * @param sessionId 用户会话ID
     * @param role 角色 (user 或 assistant)
     * @param content 对话内容
     */
    public void addMessage(String sessionId, String role, String content) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(content)) return;

        String key = REDIS_KEY_PREFIX + sessionId;
        String formattedMessage = role + ": " + content;

        // 追加到 List 的右侧 (尾部)
        stringRedisTemplate.opsForList().rightPush(key, formattedMessage);

        // 裁剪 List，只保留最新的 MAX_HISTORY_MESSAGES 条，防止 Token 爆炸
        stringRedisTemplate.opsForList().trim(key, -MAX_HISTORY_MESSAGES, -1);

        // 每次交互后刷新过期时间
        stringRedisTemplate.expire(key, MEMORY_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * 获取历史对话上下文拼装的字符串
     * @param sessionId 用户会话ID
     * @return 历史对话文本
     */
    public String getHistoryContext(String sessionId) {
        if (!StringUtils.hasText(sessionId)) return "";

        String key = REDIS_KEY_PREFIX + sessionId;
        List<String> history = stringRedisTemplate.opsForList().range(key, 0, -1);

        if (history == null || history.isEmpty()) {
            return "";
        }

        return String.join("\n", history);
    }

    /**
     * 清除记忆 (可选：供用户手动重置对话使用)
     */
    public void clearMemory(String sessionId) {
        stringRedisTemplate.delete(REDIS_KEY_PREFIX + sessionId);
    }
}
