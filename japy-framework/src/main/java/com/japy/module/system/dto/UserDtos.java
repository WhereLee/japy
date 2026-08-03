package com.japy.module.system.dto;

import com.japy.aspect.Xss;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 管理端用户相关 DTO（入参校验，不暴露实体） */
public class UserDtos {

    /** 新增用户 */
    @Data
    public static class AddDTO {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 20, message = "用户名长度 3-20")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名仅支持字母数字下划线")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 20, message = "密码长度 6-20")
        private String password;

        @NotBlank(message = "昵称不能为空")
        @Size(max = 20, message = "昵称最长 20 字")
        @Xss
        private String nickname;

        private String email;
        private String phone;
        private Integer sex;
    }

    /** 修改用户 */
    @Data
    public static class EditDTO {
        @NotNull(message = "用户 id 不能为空")
        private Long id;

        @NotBlank(message = "昵称不能为空")
        @Size(max = 20, message = "昵称最长 20 字")
        @Xss
        private String nickname;

        private String email;
        private String phone;
        private Integer sex;
    }

    /** 分配角色 */
    @Data
    public static class AssignRoleDTO {
        private List<Long> roleIds;
    }
}
