package com.icon.agent00.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class ReActAgent {
    private final ChatModel chatModel;
    private final RedisSaver redisSaver;

    /**
     * 构造函数注入
     * 注意：ReactAgent 使用的是 RedissonClient 配合 RedisSaver，
     * 而不是 ChatClient 架构下的 RedissonRedisChatMemoryRepository。
     */
    public ReActAgent(ChatModel chatModel, RedissonClient redissonClient) {
        this.chatModel = chatModel;
        // 1. 初始化基于 Redis 的持久化状态保存器 (全局单例即可)
        this.redisSaver = RedisSaver.builder()
                .redisson(redissonClient)
                .build();
    }

    /**
     * @param sessionId 用户ID，用于隔离不同人的聊天记录
     * @param systemPrompt 包含规则、数据库、长期画像的系统指令
     * @param userRequest 用户当前的提问
     */
    public String callLlmApi(String sessionId, String systemPrompt, String userRequest) throws AppException {
        try {
            // 2. 配置消息压缩 Hook
            // 当历史上下文超过 4000 tokens 时触发总结，总结后保留最近的 5 条消息
            SummarizationHook summarizationHook = SummarizationHook.builder()
                    .model(chatModel)
                    .maxTokensBeforeSummary(500)
                    .messagesToKeep(5)
                    .build();

            // 3. 动态构建 ReactAgent
            ReactAgent agent = ReactAgent.builder()
                    .name("school_consultant_agent")
                    .model(chatModel)
                    .systemPrompt(systemPrompt)
                    .saver(redisSaver)
                    .hooks(summarizationHook, new CleanThinkTagHook()) // 挂载清理 Hook
                    .build();

            // 4. 配置会话隔离标识 (threadId)
            RunnableConfig config = RunnableConfig.builder()
                    .threadId(sessionId)
                    .build();

            // 5. 发起调用
            AssistantMessage responseMessage = agent.call(userRequest, config);

            if (responseMessage == null || responseMessage.getText() == null) {
                throw new AppException(ResponseCode.LLM_PARSE_ERROR);
            }

            String response = responseMessage.getText();

            log.info("【大模型最终输出】: {}", response);
            return response;

        } catch (Exception e) {
            log.error("本地大模型调用失败", e);
            throw new AppException(ResponseCode.LLM_API_ERROR);
        }
    }

    /**
     * 获取指定用户的完整聊天历史记录
     * @param sessionId 用户ID (threadId)
     * @return 历史消息列表，如果没有则返回空列表
     */
    public List<Message> getChatHistory(String sessionId) {
        // 1. 构造检索快照用的配置
        RunnableConfig config = RunnableConfig.builder()
                .threadId(sessionId)
                .build();

        // 2. 临时构建一个查询 Agent
        ReactAgent queryAgent = ReactAgent.builder()
                .name("school_consultant_agent")
                .model(chatModel)
                .saver(redisSaver)
                .build();

        // 3. 【核心修复】先获取底层编译图，再获取状态快照
        StateSnapshot snapshot = queryAgent.getAndCompileGraph().getState(config);

        if (snapshot == null || snapshot.state() == null) {
            return Collections.emptyList(); // 该用户没有图状态存档
        }

        // 4. 解析底层状态字典 (兼容阿里的 OverAllState)
        Object stateObj = snapshot.state();
        Map<String, Object> channelValues = null;

        if (stateObj instanceof Map) {
            channelValues = (Map<String, Object>) stateObj;
        } else {
            try {
                // 优先尝试最新版源码的 getData() 方法
                channelValues = (Map<String, Object>) stateObj.getClass().getMethod("getData").invoke(stateObj);
            } catch (Exception e1) {
                try {
                    // 兼容旧版源码的 data() 方法
                    channelValues = (Map<String, Object>) stateObj.getClass().getMethod("data").invoke(stateObj);
                } catch (Exception e2) {
                    log.warn("无法解析图计算引擎的底层状态结构", e2);
                }
            }
        }

        // 5. 提取标准的 messages 列表
        if (channelValues != null && channelValues.containsKey("messages")) {
            Object messagesObj = channelValues.get("messages");
            if (messagesObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Message> messages = (List<Message>) messagesObj;
                return messages;
            }
        }

        return Collections.emptyList();
    }
}
