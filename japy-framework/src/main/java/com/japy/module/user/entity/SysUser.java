package com.japy.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /** 密码哈希：禁止序列化外泄（可离线爆破），仅用于数据库读写 */
    @JsonIgnore
    private String password;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer sex;
    private Integer status;          // 0正常 1停用
    @TableLogic
    private Integer delFlag;         // 逻辑删除 0存在 1删除
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
