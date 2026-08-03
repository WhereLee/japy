package com.japy.module.system.dto;

import com.japy.aspect.Xss;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 管理端角色/公告/字典/参数 DTO */
public class AdminDtos {

    /** 角色 */
    @Data
    public static class RoleDTO {
        private Long id;
        @NotBlank(message = "角色名称不能为空")
        @Size(max = 50, message = "角色名称最长 50 字")
        @Xss
        private String roleName;
        @NotBlank(message = "角色标识不能为空")
        @Size(max = 50, message = "角色标识最长 50 字")
        @Xss
        private String roleKey;
        private Integer sort;
        private Integer status;
        @Xss
        private String remark;
    }

    /** 分配权限 */
    @Data
    public static class AssignPermDTO {
        private List<Long> permIds;
    }

    /** 公告 */
    @Data
    public static class NoticeDTO {
        private Long id;
        @NotBlank(message = "公告标题不能为空")
        @Size(max = 100, message = "公告标题最长 100 字")
        @Xss
        private String noticeTitle;
        @NotNull(message = "公告类型不能为空")
        private Integer noticeType;
        @Xss
        private String noticeContent;
        private Integer status;
    }

    /** 字典类型 */
    @Data
    public static class DictTypeDTO {
        private Long id;
        @NotBlank(message = "字典名称不能为空")
        @Size(max = 100, message = "字典名称最长 100 字")
        @Xss
        private String dictName;
        @NotBlank(message = "字典类型不能为空")
        @Size(max = 100, message = "字典类型最长 100 字")
        @Xss
        private String dictType;
        private Integer status;
        @Xss
        private String remark;
    }

    /** 字典数据 */
    @Data
    public static class DictDataDTO {
        private Long id;
        @NotBlank(message = "字典类型不能为空")
        @Xss
        private String dictType;
        @NotBlank(message = "字典标签不能为空")
        @Xss
        private String dictLabel;
        @NotBlank(message = "字典键值不能为空")
        @Xss
        private String dictValue;
        private Integer sort;
        private Integer status;
        @Xss
        private String remark;
    }

    /** 参数 */
    @Data
    public static class ConfigDTO {
        private Long id;
        @NotBlank(message = "参数名称不能为空")
        @Xss
        private String configName;
        @NotBlank(message = "参数键不能为空")
        @Xss
        private String configKey;
        @Xss
        private String configValue;
        @Xss
        private String remark;
    }
}
