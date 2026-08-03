package com.japy.common;

import java.security.MessageDigest;
import java.util.List;

/**
 * 头像简单生成：昵称首字 + 哈希选色 → SVG（data URI），无需上传图片。
 * 调用方可用任意文本请求不同配色，实现"换一个"。
 */
public final class AvatarUtil {

    private static final List<String> COLORS = List.of(
            "#3b6ef6", "#16a34a", "#d97706", "#dc2626", "#7c5cf0",
            "#0d9488", "#db2777", "#65a30d", "#ea580c", "#4f46e5");

    private AvatarUtil() {}

    public static String randomColor(String seed) {
        if (seed == null || seed.isEmpty()) return COLORS.get(0);
        int hash = Math.abs(seed.hashCode());
        return COLORS.get(hash % COLORS.size());
    }

    public static String charOf(String text) {
        if (text == null || text.isBlank()) return "?";
        return text.trim().substring(0, 1);
    }

    /** 生成 SVG data URI（可直接存 avatar 字段 / 前端 img src 使用） */
    public static String svgDataUri(String text, String bgColor) {
        String c = charOf(text);
        String bg = bgColor == null || bgColor.isBlank() ? randomColor(text) : bgColor;
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='100' height='100'>"
                + "<rect width='100' height='100' rx='50' fill='" + bg + "'/>"
                + "<text x='50' y='68' font-size='48' text-anchor='middle' fill='#fff' font-family='sans-serif'>"
                + c + "</text></svg>";
        return "data:image/svg+xml;utf8," + svg.replace("<", "%3C").replace(">", "%3E").replace("#", "%23");
    }
}
