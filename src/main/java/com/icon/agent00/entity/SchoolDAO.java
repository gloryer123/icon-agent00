package com.icon.agent00.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchoolDAO {

    private Integer id;
    private String name;
    private String country;
    private String region;
    private String category;
    private Integer isPublic; // 注意：MyBatis 会将 is_public 映射为 isPublic
    private String durationStr;
    private Double durationYears;
    private Integer tuitionRmb;
    private String features;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}