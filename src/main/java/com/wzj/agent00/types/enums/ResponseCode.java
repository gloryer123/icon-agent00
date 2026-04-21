package com.wzj.agent00.types.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public enum ResponseCode {
    SUCCESS("0000", "成功"),
    UN_ERROR("0001", "未知失败:"),

    NO_SESSION("S0001", "未登录"),
    NO_SCHOOL_DATA("S0002", "系统内部错误：暂无学校数据"),
    LLM_API_ERROR("S0003", "调用大模型接口失败"),
    LLM_PARSE_ERROR("S0004", "大模型返回数据解析异常");

    private String code;
    private String info;
}
