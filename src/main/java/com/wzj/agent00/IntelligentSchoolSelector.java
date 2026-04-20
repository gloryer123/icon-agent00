package com.wzj.agent00;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class IntelligentSchoolSelector {
    // Ollama 默认的 generate API 端点
    private static final String API_URL = "http://192.168.111.201:11434/api/generate";
    // 指定使用的模型名称
    private static final String MODEL_NAME = "qwen3.5:9b";

    public static void main(String[] args) {
        // 1. 学校数据
        String schoolData = """
        1. 清华大学: 地点-中国北京, 学制-4年, 学费-约5000人民币/学年, 特色-国内顶尖学府，理工科极强
        2. 深圳大学: 地点-中国深圳, 学制-4年, 学费-约6000人民币/学年, 特色-地处核心经济区，创新创业氛围浓厚，就业率高
        3. 麻省理工学院 (MIT): 地点-美国剑桥市, 学制-4年, 学费-约450000人民币/学年, 特色-全球顶尖理工及计算机学府，学费高昂
        4. 新加坡国立大学 (NUS): 地点-新加坡, 学制-3至4年, 学费-约170000人民币/学年, 特色-亚洲顶尖，国际化程度高，跨学科研究领先
        """;

        // 2. 模拟用户输入的实际需求
        String userRequirement = "我希望找一个在国外的学校，学费不要太贵，最好偏向理工科。";

        // 3. 将数据与需求拼接成发给大模型的 Prompt
        String prompt = buildPrompt(schoolData, userRequirement);

        System.out.println("正在请求本地模型 " + MODEL_NAME + " 进行分析，请稍候...\n");

        // 4. 调用 API 并获取结果
        try {
            String recommendation = getModelRecommendation(prompt);
            System.out.println("====== 大模型推荐结果 ======");
            System.out.println(recommendation);
            System.out.println("============================");
        } catch (Exception e) {
            System.err.println("API 调用失败，请检查 Ollama 服务是否在本地 11434 端口运行。错误信息: " + e.getMessage());
        }
    }

    /**
     * 构建发送给大模型的系统提示词与上下文
     */
    private static String buildPrompt(String data, String requirement) {
        return String.format(
                "你是一个专业的智能择校助手。请严格基于我提供的【学校数据库】信息，分析【用户需求】，推荐最匹配的一所或多所学校，并分点说明推荐理由。\n\n" +
                        "【学校数据库】:\n%s\n" +
                        "【用户需求】:\n%s",
                data, requirement
        );
    }

    /**
     * 发送 HTTP POST 请求到本地大模型 API
     */
    private static String getModelRecommendation(String prompt) throws Exception {
        // 对 prompt 中的特殊字符进行基础转义，以符合 JSON 格式要求
        String safePrompt = prompt.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");

        // 构造 Ollama 请求 JSON，stream 设置为 false 表示一次性返回完整结果
        String requestBody = String.format(
                "{\"model\": \"%s\", \"prompt\": \"%s\", \"stream\": false}",
                MODEL_NAME, safePrompt
        );

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMinutes(2)) // 大模型推理时间较长，设置较长的超时时间
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            return extractResponseFromJson(response.body());
        } else {
            throw new RuntimeException("HTTP 状态码异常: " + response.statusCode() + "\n响应内容: " + response.body());
        }
    }

    /**
     * 简易 JSON 解析方法（提取 response 字段）
     * 生产环境中建议替换为 Jackson 或 Gson 库
     */
    private static String extractResponseFromJson(String json) {
        String targetKey = "\"response\":\"";
        int startIndex = json.indexOf(targetKey);
        if (startIndex == -1) {
            return "无法解析返回格式: " + json;
        }
        startIndex += targetKey.length();

        // 寻找非转义的双引号作为结束位置
        int endIndex = startIndex;
        while (endIndex < json.length()) {
            if (json.charAt(endIndex) == '\"' && json.charAt(endIndex - 1) != '\\') {
                break;
            }
            endIndex++;
        }

        String rawResponse = json.substring(startIndex, endIndex);

        // 还原转义字符
        return rawResponse.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
