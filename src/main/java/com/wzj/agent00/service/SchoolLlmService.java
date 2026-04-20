package com.wzj.agent00.service;

import com.wzj.agent00.mapper.SchoolMapper;
import com.wzj.agent00.entity.SchoolDAO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
    private static final String API_URL = "http://192.168.111.201:11434/api/generate";
    private static final String MODEL_NAME = "qwen3.5:9b";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SchoolMapper schoolMapper;

    /**
     * 根据需求获取推荐
     */
    public String getRecommendation(String userRequirement) throws Exception {
        // 1. 获取并格式化数据库数据
        String schoolData = getFormattedSchoolData();
        if (schoolData.isEmpty()) {
            return "系统内部错误：暂无学校数据";
        }
        log.info("成功获取学校数据，当前数据长度: {} 字符，即将进入 Prompt 构建与模型调用环节...", schoolData.length());

        // 2. 构建 Prompt
        String prompt = buildPrompt(schoolData, userRequirement);
        log.info("已构建Prompt: \n {}", prompt);

        // 3. 调用大模型并返回结果
        return callLlmApi(prompt);
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
            sb.append(count).append(". ")
                    .append(s.getName()).append(": ")
                    .append("地点-").append(s.getCountry()).append(" ").append(s.getRegion()).append(", ")
                    .append("类型-").append(s.getIsPublic() == 1 ? "公立" : "私立").append(s.getCategory()).append(", ")
                    .append("学制-").append(s.getDurationStr()).append(", ")
                    .append("学费-约").append(s.getTuitionRmb()).append("人民币/学年, ")
                    .append("特色-").append(s.getFeatures()).append("\n");
            count++;
        }
        return sb.toString();
    }

    private String buildPrompt(String data, String requirement) {
        return String.format(
                "你是爱康优申集团的一个专业的智能择校助手。请严格基于我提供的【学校数据库】信息，分析【用户需求】，推荐最匹配的一所或多所学校，并用中文分点说明推荐理由。\n\n" +
                        "【学校数据库】:\n%s\n" +
                        "【用户需求】:\n%s",
                data, requirement
        );
    }

    private String callLlmApi(String prompt) throws Exception {
        // 使用 Map 构建请求体，Jackson 会自动处理所有特殊字符（换行、双引号等）的转义
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", MODEL_NAME);
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
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(5))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return extractResponseFromJson(response.body());
        } else {
            log.error("API 调用失败，状态码: {}, 响应内容: {}", response.statusCode(), response.body());
            throw new RuntimeException("HTTP 状态码异常: " + response.statusCode());
        }
    }

    private String extractResponseFromJson(String json) {
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

            if (!fullResponse.isEmpty()) {
                return fullResponse.toString();
            } else {
                log.error("模型返回的数据中未找到任何 'response' 字段: {}", json);
                return "解析异常：无法获取模型回答";
            }

        } catch (Exception e) {
            log.error("解析模型响应 JSON 时发生错误: {}", e.getMessage(), e);
            // 打印出引发报错的原始字符串，方便排查
            log.error("导致报错的原始返回数据: \n{}", json);
            return "解析异常：JSON 格式错误";
        }
    }
}
