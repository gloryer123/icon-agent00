package com.icon.agent00.service;

import com.icon.agent00.entity.SchoolDAO;
import com.icon.agent00.mapper.SchoolMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VectorizationService {
    private final SchoolMapper schoolMapper;
    private final VectorStore vectorStore;

    public VectorizationService(SchoolMapper schoolMapper, VectorStore vectorStore) {
        this.schoolMapper = schoolMapper;
        this.vectorStore = vectorStore;
    }

    /**
     * 将全量学校数据向量化并写入 Redis 向量数据库。
     * 可通过 Controller 提供一个接口供管理员手动触发，或使用 @Scheduled 定时执行。
     */
    public void syncDatabaseToVectorStore() {
        log.info("开始执行 ETL 向量化任务...");
        List<SchoolDAO> schools = schoolMapper.getAllSchools();

        if (schools == null || schools.isEmpty()) {
            log.warn("未从数据库中读取到任何学校数据。");
            return;
        }

        List<Document> documents = schools.stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1) // 过滤出上架/有效的学校
                .map(s -> {
                    // 1. 组装大模型进行语义检索所需的纯文本内容
                    String content = String.format("学校名称：%s\n地点：%s %s\n类型：%s%s\n学制：%s\n学费：约%s人民币/学年\n特色：%s",
                            s.getName(),
                            s.getCountry() != null ? s.getCountry() : "",
                            s.getRegion() != null ? s.getRegion() : "",
                            (s.getIsPublic() != null && s.getIsPublic() == 1) ? "公立" : "私立",
                            s.getCategory() != null ? s.getCategory() : "",
                            s.getDurationStr() != null ? s.getDurationStr() : "",
                            s.getTuitionRmb() != null ? s.getTuitionRmb() : "",
                            s.getFeatures() != null ? s.getFeatures() : ""
                    );

                    // 2. 附加元数据 (Metadata)
                    // 这里的 key 和数据类型必须和 VectorStoreConfig 中注册的 metadataFields 保持一致
                    Map<String, Object> metadata = Map.of(
                            "school_id", s.getId(),
                            "country", s.getCountry() != null ? s.getCountry() : "未知",
                            "is_public", s.getIsPublic() != null ? s.getIsPublic() : 0,
                            "tuition", s.getTuitionRmb() != null ? s.getTuitionRmb() : 99999999, // 建议数据库里存一个纯数字的学费字段
                             "region", s.getRegion() != null ? s.getRegion() : "未知"
                    );

                    return new Document(content, metadata);
                })
                .collect(Collectors.toList());

        // 3. 批量写入 Redis 向量库（底层会自动调用 EmbeddingModel 将 content 转化为向量）
        vectorStore.add(documents);
        log.info("ETL 任务完成，成功向 Redis 写入 {} 条学校向量数据。", documents.size());
    }
}
