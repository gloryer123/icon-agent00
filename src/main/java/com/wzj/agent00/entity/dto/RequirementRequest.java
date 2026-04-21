package com.wzj.agent00.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "择校需求请求实体")
public class RequirementRequest {

    @Schema(description = "具体的择校需求描述", example = "我想找国内一线城市学费1万以内的理工学校")
    private String requirement;
}
