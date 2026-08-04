package com.japy.module.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 用户-角色-权限 关联查询（纯注解 Mapper） */
@Mapper
public interface SysUserRoleMapper {

    @Select("SELECT DISTINCT p.perm_key FROM sys_permission p " +
            "JOIN sys_role_permission rp ON rp.perm_id = p.id " +
            "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
            "WHERE ur.user_id = #{userId} AND p.perm_key IS NOT NULL AND p.status = 0")
    List<String> selectPermKeys(@Param("userId") Long userId);

    @Select("SELECT r.role_key FROM sys_role r " +
            "JOIN sys_user_role ur ON ur.role_id = r.id " +
            "WHERE ur.user_id = #{userId}")
    List<String> selectRoleKeys(@Param("userId") Long userId);

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);

    /** 按用户查其所有角色绑定的权限 id（DISTINCT，供菜单树组装） */
    @Select("SELECT DISTINCT rp.perm_id FROM sys_role_permission rp " +
            "JOIN sys_user_role ur ON ur.role_id = rp.role_id " +
            "WHERE ur.user_id = #{userId}")
    List<Long> selectPermIdsByUser(@Param("userId") Long userId);

    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIdsByRole(@Param("roleId") Long roleId);

    @Select("SELECT perm_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<Long> selectPermIds(@Param("roleId") Long roleId);

    @org.apache.ibatis.annotations.Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insertUserRole(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    @org.apache.ibatis.annotations.Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deletePermByRoleId(@Param("roleId") Long roleId);

    @org.apache.ibatis.annotations.Insert("<script>" +
            "INSERT INTO sys_role_permission (role_id, perm_id) VALUES " +
            "<foreach collection='permIds' item='pid' separator=','>(#{roleId}, #{pid})</foreach>" +
            "</script>")
    int insertRolePerms(@Param("roleId") Long roleId, @Param("permIds") List<Long> permIds);
}
