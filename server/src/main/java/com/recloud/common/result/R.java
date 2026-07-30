package com.recloud.common.result;

import lombok.Data;

/**
 * 统一返回格式
 * <p>
 * 所有接口统一返回 R&lt;T&gt;，前端通过 code 判断成功/失败，data 承载业务数据。
 * 私有构造 + 静态工厂方法，防止外部直接 new。
 */
@Data
public class R<T> {

    private int code;
    private String msg;
    private T data;

    private R() {}

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = 200;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> fail(int code, String msg) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
        return r;
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMsg());
    }
}
