package com.icon.agent00.service;

import com.icon.agent00.mapper.SchoolMapper;
import com.icon.agent00.entity.SchoolDAO;
import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
public class SchoolLlmService {

    @Autowired
    private SchoolMapper schoolMapper;

    @Autowired
    private LongMemoryService longMemoryService;

    @Autowired
    private LlmApiClientAlibaba llmApiClientAlibaba;

    @Autowired
    private ReActAgent reActAgent;

    /**
     * 根据需求获取推荐
     */
    public String getRecommendation(String sessionId, String userRequirement) throws Exception {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new AppException(ResponseCode.NO_SESSION);
        }

        // 1. 获取并格式化数据库数据Map格式
        String schoolData = getFormattedSchoolData();
        if (schoolData.isEmpty()) {
            throw new AppException(ResponseCode.NO_SCHOOL_DATA);
        }

        log.info("成功获取学校数据，当前数据长度: {} 字符，即将进入 Prompt 构建与模型调用环节...", schoolData.length());

        // 2. 构建 Prompt
        String systemPrompt = buildSystemPrompt(schoolData);
        log.info("已构建 systemPrompt.");

        String llmResult = reActAgent.callLlmApi(sessionId, systemPrompt, userRequirement);

        // 3. 调用大模型并返回结果
        return llmResult;
    }

    /**
     * 从数据库获取数据并转为文本格式
     */
    private String getFormattedSchoolData() {
        List<SchoolDAO> schools = schoolMapper.getAllSchools();
        if (schools == null || schools.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int count = 1;
        for (SchoolDAO s : schools) {
            if (s.getStatus() == 1) {
                sb.append(count).append(". ")
                        .append(s.getName()).append(": ")
                        .append("地点-").append(s.getCountry()).append(" ").append(s.getRegion()).append(", ")
                        .append("类型-").append(s.getIsPublic() == 1 ? "公立" : "私立").append(s.getCategory()).append(", ")
                        .append("学制-").append(s.getDurationStr()).append(", ")
                        .append("学费-约").append(s.getTuitionRmb()).append("人民币/学年, ")
                        .append("特色-").append(s.getFeatures()).append("\n");
                count++;
            }
        }
        return sb.toString();
    }


    private String buildSystemPrompt(String schoolData) {
        StringBuilder systemBuilder = new StringBuilder();

        systemBuilder.append("你是爱康优申集团的一个专业的智能择校助手。请严格基于我提供的【学校数据库】信息，分析【用户需求】，推荐最匹配的一所或多所学校，并用中文分点说明推荐理由。");
        systemBuilder.append("你需要参考【历史对话记录】和【摘要】（如果有）来理解用户的上下文语境。\n\n");
        systemBuilder.append("如果用户使用指代词，必须强制检索前 3 轮对话中的实体（Entity）。如果无法确定，请礼貌追问，禁止猜测。");

        systemBuilder.append("【学校数据库】:\n").append(schoolData).append("\n\n");

        return systemBuilder.toString();
    }

    public String buildPrompt(String data, String requirement,  String historyContext, String summary) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你是爱康优申集团的一个专业的智能择校助手。请严格基于我提供的【学校数据库】信息，分析【用户需求】，推荐最匹配的一所或多所学校，并用中文分点说明推荐理由。");
        promptBuilder.append("你需要参考【历史对话记录】和【摘要】（如果有）来理解用户的上下文语境。\n\n");
        promptBuilder.append("如果用户使用指代词，必须强制检索前 3 轮对话中的实体（Entity）。如果无法确定，请礼貌追问，禁止猜测。");
        promptBuilder.append("【学校数据库】:\n").append(data).append("\n\n");

        if (StringUtils.hasText(historyContext)) {
            promptBuilder.append("【历史对话记录】:\n").append(historyContext).append("\n\n");
        }

        if (StringUtils.hasText(summary)) {
            promptBuilder.append("【摘要】:\n").append(summary).append("\n\n");
        }

        promptBuilder.append("【当前用户输入】:\n").append(requirement);

        return promptBuilder.toString();
    }
}
