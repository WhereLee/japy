package com.japy.module.auth.controller;

import com.japy.common.AvatarUtil;
import com.japy.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公共接口（无需登录）：头像生成预览 / 健康检查等。
 */
@RestController
@RequestMapping("/common")
public class CommonController {

    /** 按文本生成头像 SVG data URI（首字 + 配色），前端可预览后保存 */
    @GetMapping("/avatar")
    public R<Map<String, String>> avatar(@RequestParam String text,
                                         @RequestParam(required = false) String bg) {
        return R.ok(Map.of("avatar", AvatarUtil.svgDataUri(text, bg)));
    }
}
