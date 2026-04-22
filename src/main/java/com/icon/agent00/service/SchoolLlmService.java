package com.icon.agent00.service;

import com.icon.agent00.mapper.SchoolMapper;
import com.icon.agent00.entity.SchoolDAO;
import com.icon.agent00.types.enums.ResponseCode;
import com.icon.agent00.types.exeption.AppException;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SchoolLlmService {
    @Value("${llm.api-url}")
    private String apiUrl;

    @Value("${llm.model-name}")
    private String modelName;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SchoolMapper schoolMapper;

    @Autowired
    private ShortMemoryService shortMemoryService;

    /**
     * 根据需求获取推荐
     */
    public String getRecommendation(String sessionId, String userRequirement) throws Exception {
        if (sessionId == null || sessionId.isEmpty()) {
            throw new AppException(ResponseCode.NO_SESSION);
        }
//        Map<String, Object> resultMap = new HashMap<>();

        // 1. 获取并格式化数据库数据Map格式
        String schoolData = getFormattedSchoolData();
        if (schoolData.isEmpty()) {
            throw new AppException(ResponseCode.NO_SCHOOL_DATA);
        }

//        // 1. 获取并格式化数据库数据String格式
//        String schoolData = getFormattedSchoolData();
//        if (schoolData.isEmpty()) {
//            return "系统内部错误：暂无学校数据";
//        }
        log.info("成功获取学校数据，当前数据长度: {} 字符，即将进入 Prompt 构建与模型调用环节...", schoolData.length());

        String historyContext = shortMemoryService.getHistoryContext(sessionId);

        // 2. 构建 Prompt
        String prompt = buildPrompt(schoolData, userRequirement, historyContext);
        log.info("已构建Prompt: \n {}", prompt);

        String llmResult = callLlmApi(prompt);

        // 5. 将当前这一轮的用户输入和模型输出存入 Redis 记忆中
        shortMemoryService.addMessage(sessionId, "User", userRequirement);
        shortMemoryService.addMessage(sessionId, "Assistant", llmResult);

//        // 包装为规范的 JSON 结构
//        resultMap.put("code", 200);
//        resultMap.put("msg", "success");
//        resultMap.put("data", llmResult);

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

    private String buildPrompt(String data, String requirement,  String historyContext) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("你是爱康优申集团的一个专业的智能择校助手。请严格基于我提供的【学校数据库】信息，分析【用户需求】，推荐最匹配的一所或多所学校，并用中文分点说明推荐理由。");
        promptBuilder.append("你需要参考【历史对话记录】（如果有）来理解用户的上下文语境。\n\n");

        promptBuilder.append("【学校数据库】:\n").append(data).append("\n\n");

        if (StringUtils.hasText(historyContext)) {
            promptBuilder.append("【历史对话记录】:\n").append(historyContext).append("\n\n");
        }

        promptBuilder.append("【当前用户输入】:\n").append(requirement);

        return promptBuilder.toString();

//        return String.format(
//                "你是爱康优申集团的一个专业的智能择校助手。请严格基于我提供的【学校数据库】信息，分析【用户需求】，推荐最匹配的一所或多所学校，并用中文分点说明推荐理由。\n\n" +
//                        "【学校数据库】:\n%s\n" +
//                        "【用户需求】:\n%s",
//                data, requirement
//        );
    }

    private String callLlmApi(String prompt) throws Exception {
        // 使用 Map 构建请求体，Jackson 会自动处理所有特殊字符（换行、双引号等）的转义
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", modelName);
        requestMap.put("prompt", prompt);
        requestMap.put("stream", false);

        // 增加底层配置，防止模型陷入无限死循环
        // Map<String, Object> optionsMap = new HashMap<>();
        // optionsMap.put("num_predict", 800); // 强制要求模型最多生成 800 个 Token (足够输出 2-3 个学校了)
        // optionsMap.put("temperature", 0.1); // 降低随机性，让它变老实，减少胡思乱想
        // requestMap.put("options", optionsMap);

        // 将 Map 序列化为 JSON 字符串
        String requestBody = objectMapper.writeValueAsString(requestMap);

        // 配置 HTTP 客户端与请求
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return extractResponseFromJson(response.body());
        } else {
            log.error("API 调用失败，状态码: {}, 响应内容: {}", response.statusCode(), response.body());
            throw new AppException(ResponseCode.LLM_API_ERROR);
        }
    }

    private String extractResponseFromJson(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) {
            return "解析异常：模型返回的数据为空";
        }

        try {
            StringBuilder fullResponse = new StringBuilder();

            // 将大模型返回的文本按换行符拆分（兼容单行 JSON 和多行 NDJSON）
            String[] lines = json.split("\n");

            for (String line : lines) {
                // 跳过空行
                if (line.trim().isEmpty()) {
                    continue;
                }

                // 逐行解析 JSON
                JsonNode rootNode = objectMapper.readTree(line);

                // 提取 response 字段并追加到总结果中
                if (rootNode.has("response")) {
                    fullResponse.append(rootNode.get("response").asString());
                }
            }

            String finalRes = fullResponse.toString().trim();
            log.info("【大模型原始完整输出 (包含 Think 和 Response)】:\n{}", finalRes);


            if (!finalRes.isEmpty()) {
                // 策略 1：寻找模型的思维结束标签 </think>
                int thinkEndIndex = finalRes.indexOf("</think>");

                if (thinkEndIndex != -1) {
                    // 如果找到了 </think>，直接把这个标签及其之前的所有“草稿”全部丢弃
                    return finalRes.substring(thinkEndIndex + "</think>".length()).trim();
                }

                else {
                    // 如果没找到 "你好"，为了防止丢数据，返回全部内容，并记录一条警告
                    log.warn("在模型的 response 中未找到 'think' 作为起始标识，返回完整 response 内容。");
                    return finalRes;
                }
            } else {
                log.error("模型返回的数据中未找到任何有效的 'response' 内容: \n{}", json);
                return "解析异常：无法获取模型回答";
            }

        } catch (Exception e) {
            log.error("解析模型响应 JSON 时发生错误: {}", e.getMessage(), e);
            // 打印出引发报错的原始字符串，方便排查
            log.error("导致报错的原始返回数据: \n{}", json);
            throw new AppException(ResponseCode.LLM_PARSE_ERROR);
        }
    }
}
