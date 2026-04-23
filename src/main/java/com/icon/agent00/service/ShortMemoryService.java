package com.icon.agent00.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
/**
 * redis List保存短期记忆
 */
public class ShortMemoryService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String SHORT_MEMORY_PREFIX = "llm:short:memory:";
    private static final int TRIGGER_THRESHOLD = 6; // 触发摘要的阈值
    private static final int RETAIN_THRESHOLD = 2;  // 裁剪后保留的最新条数
    private static final long MEMORY_EXPIRE_MINUTES = 300; // 短期记忆保留时间：30分钟


    /**
     * 追加对话记录到 Redis
     * @param sessionId 用户会话ID
     * @param role 角色 (user 或 assistant)
     * @param content 对话内容
     */
    public String addMessage(String sessionId, String role, String content) {

        String key = SHORT_MEMORY_PREFIX + sessionId;
        String formattedMessage = role + ": " + content;

        // 追加到 List 的右侧 (尾部)
        stringRedisTemplate.opsForList().rightPush(key, formattedMessage);

        // 当前 List 长度
        Long currentSize = stringRedisTemplate.opsForList().size(key);
        String evictedMessages = null;

        if (Objects.equals(role, "Assistant") && currentSize > TRIGGER_THRESHOLD) {

            // 计算多出了几条消息
            long excessCount = currentSize - RETAIN_THRESHOLD;

            // 3. 取出即将被淘汰的最老的这几条记录（索引从 0 到 excessCount - 1）
            List<String> oldMessages = stringRedisTemplate.opsForList().range(key, 0, excessCount - 1);

            // 裁剪 List，只保留最新的 MAX_HISTORY_MESSAGES 条，防止 Token 爆炸
            stringRedisTemplate.opsForList().trim(key, -RETAIN_THRESHOLD, -1);

            evictedMessages = (oldMessages != null && !oldMessages.isEmpty())
                    ? String.join("\n", oldMessages)
                    : null;
        }

        // stringRedisTemplate.expire(key, MEMORY_EXPIRE_MINUTES, TimeUnit.MINUTES);

        return evictedMessages;
    }

    /**
     * 获取历史对话上下文拼装的字符串
     * @param sessionId 用户会话ID
     * @return 历史对话文本
     */
    public String getHistoryContext(String sessionId) {
        if (!StringUtils.hasText(sessionId)) return "";

        String key = SHORT_MEMORY_PREFIX + sessionId;
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
        stringRedisTemplate.delete(SHORT_MEMORY_PREFIX + sessionId);
    }
}
