package com.recloud.dto.request;

import com.recloud.common.annotation.StrongPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "注册请求")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度 3-20 位")
    @Schema(description = "用户名", example = "zhangsan")
    private String username;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称最长 50 位")
    @Schema(description = "昵称", example = "张三")
    private String nickname;

    @NotBlank(message = "密码不能为空")
    @StrongPassword
    @Schema(description = "密码（至少8位，含大小写和数字）", example = "Abc12345")
    private String password;
}
