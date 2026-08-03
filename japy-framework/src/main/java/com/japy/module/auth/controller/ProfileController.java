package com.japy.module.auth.controller;

import com.japy.common.AvatarUtil;
import com.japy.common.BusinessException;
import com.japy.common.R;
import com.japy.module.auth.vo.UserInfoVO;
import com.japy.module.user.entity.SysUser;
import com.japy.module.user.mapper.SysUserMapper;
import com.japy.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 个人中心：个人信息查看/修改、修改密码、头像换色（简单生成）。
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    private SysUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser lu)) {
            throw new BusinessException("未登录");
        }
        return lu.getUser();
    }

    @GetMapping
    public R<UserInfoVO> info() {
        SysUser u = currentUser();
        UserInfoVO vo = new UserInfoVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setAvatar(u.getAvatar());
        vo.setEmail(u.getEmail());
        vo.setPhone(u.getPhone());
        vo.setSex(u.getSex());
        vo.setCreateTime(u.getCreateTime());
        return R.ok(vo);
    }

    /** 修改昵称/简介等（头像换色通过 avatar 传新 SVG） */
    @PutMapping
    public R<UserInfoVO> update(@RequestBody Map<String, String> body) {
        SysUser u = currentUser();
        if (body.containsKey("nickname")) {
            String nick = body.get("nickname");
            if (nick == null || nick.isBlank() || nick.length() > 20) throw new BusinessException("昵称 1-20 字");
            u.setNickname(nick.trim());
        }
        if (body.containsKey("avatar")) u.setAvatar(body.get("avatar"));
        if (body.containsKey("email")) u.setEmail(body.get("email"));
        if (body.containsKey("phone")) u.setPhone(body.get("phone"));
        userMapper.updateById(u);
        return info();
    }

    /** 修改密码 */
    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        SysUser u = currentUser();
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (oldPwd == null || newPwd == null) throw new BusinessException("参数不完整");
        if (!passwordEncoder.matches(oldPwd, u.getPassword())) throw new BusinessException("旧密码错误");
        if (newPwd.length() < 6 || newPwd.length() > 20) throw new BusinessException("新密码长度 6-20");
        u.setPassword(passwordEncoder.encode(newPwd));
        userMapper.updateById(u);
        return R.ok();
    }

    /** 头像换一个：按昵称+随机色生成新 SVG */
    @PostMapping("/avatar/random")
    public R<Map<String, String>> randomAvatar(@RequestBody(required = false) Map<String, String> body) {
        SysUser u = currentUser();
        String seed = body != null && body.get("seed") != null ? body.get("seed") : u.getNickname() + System.currentTimeMillis();
        return R.ok(Map.of("avatar", AvatarUtil.svgDataUri(u.getNickname(), AvatarUtil.randomColor(seed))));
    }
}
