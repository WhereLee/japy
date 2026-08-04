package com.japy.module.auth.vo;

import lombok.Data;

import java.util.List;

/**
 * 前端路由节点（若依 RouterVo 简化版）：由后端下发，前端动态 addRoute。
 */
@Data
public class RouterVo {
    private String name;            // 路由名（唯一）
    private String path;            // 路由地址
    private String component;       // 组件路径：Layout / 或 views 相对路径
    private String redirect;        // 重定向（目录节点用）
    private Meta meta;
    private List<RouterVo> children;

    @Data
    public static class Meta {
        private String title;       // 菜单名
        private String icon;        // 图标
        private boolean hidden;     // 是否隐藏

        public Meta(String title, String icon, boolean hidden) {
            this.title = title;
            this.icon = icon;
            this.hidden = hidden;
        }
    }
}
