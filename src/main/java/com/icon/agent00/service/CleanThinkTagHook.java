package com.icon.agent00.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

@HookPositions({HookPosition.AFTER_MODEL})
public class CleanThinkTagHook extends MessagesModelHook {

    @Override
    public String getName() {
        return "clean_think_tag";
    }

    @Override
    public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
        // 如果消息列表为空，直接放行
        if (previousMessages == null || previousMessages.isEmpty()) {
            return new AgentCommand(previousMessages);
        }

        // 获取列表最后一条消息（即大模型刚刚生成的包含 think 的回复）
        Message lastMessage = previousMessages.getLast();
        String content = lastMessage.getText();

        // 检查是否包含 </think> 结束标签
        if (content != null && content.contains("</think>")) {
            int lastThinkEndIndex = content.lastIndexOf("</think>");

            // 截取标签之后的核心内容
            String cleanContent = content.substring(lastThinkEndIndex + 8).trim();

            // 移除旧的脏消息，准备写入干净消息
            List<Message> filtered = new ArrayList<>(
                    previousMessages.subList(0, previousMessages.size() - 1)
            );

            // 保持原有的消息类型并追加到列表末尾
            if (lastMessage instanceof AssistantMessage) {
                filtered.add(new AssistantMessage(cleanContent));
            } else if (lastMessage instanceof SystemMessage) {
                filtered.add(new SystemMessage(cleanContent));
            } else {
                return new AgentCommand(previousMessages);
            }

            // 【核心】使用 UpdatePolicy.REPLACE 覆盖图计算底层的状态记忆
            return new AgentCommand(filtered, UpdatePolicy.REPLACE);
        }

        // 如果没有匹配到标签，不干预状态，直接返回
        return new AgentCommand(previousMessages);
    }
}
