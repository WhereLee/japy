package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.common.JwtUtil;
import com.japy.common.R;
import com.japy.entity.User;
import com.japy.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @PostMapping("/register")
    public R<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String nickname = body.get("nickname");

        if (username == null || username.isBlank()) return R.fail("用户名不能为空");
        if (username.length() > 50) return R.fail("用户名过长");
        if (password == null || password.length() < 6) return R.fail("密码至少6位");
        if (nickname == null || nickname.isBlank()) return R.fail("昵称不能为空");
        if (nickname.length() > 50) return R.fail("昵称过长");

        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) return R.fail("用户名已存在");

        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setNickname(nickname);
        user.setRole("user");
        user.setStatus(0);
        userMapper.insert(user);

        String token = JwtUtil.generate(user.getId(), username, nickname);
        return R.ok(Map.of("token", token, "nickname", nickname, "userId", user.getId()));
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) return R.fail("用户名和密码不能为空");

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) return R.fail("用户不存在");
        if (!encoder.matches(password, user.getPassword())) return R.fail("密码错误");
        if (user.getStatus() != null && user.getStatus() == 1) return R.fail("账号已被封禁，请联系管理员");

        String token = JwtUtil.generate(user.getId(), user.getUsername(), user.getNickname());
        return R.ok(Map.of("token", token, "nickname", user.getNickname(), "userId", user.getId()));
    }
}
