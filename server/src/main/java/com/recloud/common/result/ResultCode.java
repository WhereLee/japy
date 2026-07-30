package com.recloud.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 * <p>
 * 设计原则：业务错误码分段，便于快速定位问题域。
 * 通用: 200/400/401/403/404/429/500
 * 用户: 1xxx
 * 批注: 2xxx
 * 评论: 3xxx
 * 小说: 4xxx
 * 举报: 6xxx
 * 系统/降级: 5xxx
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    RATE_LIMIT(429, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // ========== 用户错误码 1xxx ==========
    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_ERROR(1002, "密码错误"),
    USERNAME_EXISTS(1003, "用户名已存在"),
    USER_DISABLED(1004, "账号已被禁用"),
    PASSWORD_WEAK(1005, "密码至少8位，需包含大小写字母和数字"),
    LOGIN_FAIL(1006, "用户名或密码错误"),
    ACCOUNT_LOCKED(1007, "账号已锁定，请15分钟后再试"),

    // ========== 批注错误码 2xxx ==========
    ANNOTATION_NOT_FOUND(2001, "批注不存在"),
    ANNOTATION_NO_PERMISSION(2002, "无权操作此批注"),
    ANNOTATION_DUPLICATE(2003, "请勿重复提交"),

    // ========== 评论错误码 3xxx ==========
    COMMENT_NOT_FOUND(3001, "评论不存在"),
    COMMENT_NO_PERMISSION(3002, "无权操作此评论"),

    // ========== 小说错误码 4xxx ==========
    NOVEL_NOT_FOUND(4001, "小说不存在"),
    NOVEL_IMPORT_FAIL(4002, "小说导入失败"),
    CHAPTER_NOT_FOUND(4003, "章节不存在"),

    // ========== 举报错误码 6xxx ==========
    REPORT_NOT_FOUND(6001, "举报记录不存在"),

    // ========== 系统/降级错误码 5xxx ==========
    SYSTEM_BUSY(5001, "系统繁忙，请稍后重试"),
    SERVICE_DEGRADED(5002, "服务已降级，请稍后重试"),
    IDEMPOTENT_UNAVAILABLE(5003, "幂等性校验不可用，请稍后重试"),
    CACHE_INCONSISTENCY(5004, "缓存数据不一致，已触发自动修复");

    private final int code;
    private final String msg;
}
