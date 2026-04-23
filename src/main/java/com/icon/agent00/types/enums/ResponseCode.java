package com.icon.agent00.types.enums;

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
    LLM_PARSE_ERROR("S0004", "大模型返回数据解析异常"),
    NO_USER("S0004", "用户不存在"),
    NO_TOKEN("S0005", "token为空"),
    ERROR_IN_SUMMARY("S0006", "记忆压缩发生异常");

    private String code;
    private String info;
}
