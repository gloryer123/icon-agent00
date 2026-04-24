package com.icon.agent00.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icon.agent00.entity.ChatMessageDAO;
import com.icon.agent00.service.LongMemoryService;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisChatMemory implements ChatMemory {

    @Autowired
    private LongMemoryService longMemoryService;

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SHORT_MEMORY_PREFIX = "llm:short:memory:";
    private static final Integer TIME_OUT = 60;

    @Value("${app.chat.memory.retain-threshold}")
    private int retainThreshold;

    public RedisChatMemory(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = SHORT_MEMORY_PREFIX + conversationId;
        List<ChatMessageDAO> listIn = new ArrayList<>();
        ChatMessageDAO ent = new ChatMessageDAO();
        for (Message msg : messages) {
            String[] strs = msg.getContent().split("</think>");
            String text = strs.length == 2 ? strs[1] : strs[0];

            ent.setChatId(conversationId);
            ent.setType(msg.getMessageType().getValue());
            ent.setText(text);
            listIn.add(ent);
        }
        // 把新的消息追加到 Redis 的 List 中
        redisTemplate.opsForList().rightPushAll(key, listIn.toArray());
        redisTemplate.expire(key, TIME_OUT, TimeUnit.MINUTES);

        Long currentSize = redisTemplate.opsForList().size(key);
        Long trigger_threshold = retainThreshold * 2L;

        if (ent.getType().equals("assistant") && currentSize != null && currentSize >= trigger_threshold) {
            // 【关键步骤】在裁剪前，先获取 Index 0 到 9 的数据
            List<Object> rawEvicted = redisTemplate.opsForList().range(key, 0, trigger_threshold - 1);

            // 4. 将原始对象转换为 LLM 能读懂的文本格式
            String evictedMessagesText = longMemoryService.convertToText(rawEvicted);

            // 6. 异步触发压缩任务
            try {
                longMemoryService.handleMemoryCompression(conversationId, evictedMessagesText);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // 7. 【物理裁剪】将这 10 条从 Redis 中永久移除
            // LTRIM key 10 -1 表示保留从索引 10 开始到最后的所有元素
            redisTemplate.opsForList().trim(key, retainThreshold, -1);

        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        String key = SHORT_MEMORY_PREFIX + conversationId;
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) {
            return Collections.emptyList();
        }

        int start = Math.max(0, (int) (size - lastN));
        // 获取最后 N 条消息 (这里的 lastN 就是 Advisor 传进来的 10)
        List<Object> listTmp = redisTemplate.opsForList().range(key, start, -1);
        ObjectMapper objectMapper = new ObjectMapper();
        // 类型转换
        List<Message> listOut = new ArrayList<>();
        for (Object obj : listTmp) {
            ChatMessageDAO chat = objectMapper.convertValue(obj, ChatMessageDAO.class);

            // 使用 switch 表达式进行工厂路由
            Message springMessage = switch (chat.getType().toUpperCase()) {
                case "USER" -> new UserMessage(chat.getText());
                case "ASSISTANT" -> new AssistantMessage(chat.getText());
                case "SYSTEM" -> new SystemMessage(chat.getText());
                default -> throw new IllegalArgumentException("未知的消息类型: " + chat.getType());
            };
            listOut.add(springMessage);
        }
        return listOut;
    }

    @Override
    public void clear(String conversationId) {
        String key = SHORT_MEMORY_PREFIX + conversationId;
        redisTemplate.delete(key);
    }
}
