package com.icon.agent00.controller;

import com.icon.agent00.service.VectorizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etl")
@Slf4j
public class EtlController {
    private final VectorizationService vectorizationService;

    public EtlController(VectorizationService vectorizationService) {
        this.vectorizationService = vectorizationService;
    }

    /**
     * 触发全量数据库向量化同步
     * 访问地址: http://localhost:8080/api/etl/sync-schools
     */
    @GetMapping("/sync-schools")
    public String syncSchools() {
        try {
            vectorizationService.syncDatabaseToVectorStore();
            return "ETL 向量化任务执行成功！请检查控制台日志确认写入数量。";
        } catch (Exception e) {
            log.error("ETL 任务执行失败", e);
            return "ETL 任务执行失败：" + e.getMessage();
        }
    }
}
