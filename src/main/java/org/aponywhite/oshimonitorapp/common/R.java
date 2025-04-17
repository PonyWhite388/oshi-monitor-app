package org.aponywhite.oshimonitorapp.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 通用响应结构体 ✅
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class R<T> {
    @JsonProperty("code")
    private Integer code;
    @JsonProperty("msg")
    private String msg;
    @JsonProperty("data")
    private T data;

    // ===== 构造方法 =====
    public R(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public R(Integer code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ===== 静态工厂方法 =====

    // 成功响应，无数据
    public static <T> R<T> ok() {
        return new R<>(200, "success");
    }

    // 成功响应，附带数据
    public static <T> R<T> ok(T data) {
        return new R<>(200, "success", data);
    }

    // 错误响应，无数据
    public static <T> R<T> error() {
        return new R<>(500, "error");
    }

    // 错误响应，附带数据
    public static <T> R<T> error(T data) {
        return new R<>(500, "error", data);
    }

    // 自定义错误
    public static <T> R<T> error(Integer code, String msg) {
        return new R<>(code, msg);
    }
}
