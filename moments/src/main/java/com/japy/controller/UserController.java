package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.common.*;
import com.japy.entity.Comment;
import com.japy.entity.Moment;
import com.japy.entity.User;
import com.japy.mapper.CommentMapper;
import com.japy.mapper.UserMapper;
import com.japy.service.MomentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final MomentService momentService;
    private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();

    /** 公开主页：用户信息 + TA的动态（分页） */
    @GetMapping("/{id}")
    public R<Map<String, Object>> profile(@PathVariable Long id,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        User user = userMapper.selectById(id);
        if (user == null || user.getStatus() == 2) return R.fail("用户不存在");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("nickname", user.getNickname());
        info.put("avatar", user.getAvatar());
        info.put("bio", user.getBio());
        info.put("createdAt", user.getCreatedAt());
        info.put("moments", momentService.listByUser(id, PageParams.page(page), PageParams.size(size), UserContext.getUserId()));
        return R.ok(info);
    }

    /** 我的动态 */
    @GetMapping("/me/moments")
    public R<PageResult<Moment>> myMoments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(momentService.listMine(PageParams.page(page), PageParams.size(size), UserContext.getUserId()));
    }

    /** 我的评论（分页） */
    @GetMapping("/me/comments")
    public R<PageResult<Comment>> myComments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Comment> result =
                commentMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(PageParams.page(page), PageParams.size(size)),
                        new LambdaQueryWrapper<Comment>()
                                .eq(Comment::getUserId, userId)
                                .in(Comment::getStatus, 0, 1)
                                .orderByDesc(Comment::getCreatedAt));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    /** 修改个人资料（昵称/头像/简介），昵称变更后重签 token */
    @PutMapping("/me")
    public R<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) return R.fail("用户不存在");

        if (body.containsKey("nickname") && body.get("nickname") != null && !body.get("nickname").isBlank()) {
            if (body.get("nickname").length() > 50) return R.fail("昵称过长");
            user.setNickname(body.get("nickname"));
        }
        if (body.containsKey("avatar")) {
            user.setAvatar(body.get("avatar"));
        }
        if (body.containsKey("bio")) {
            if (body.get("bio") != null && body.get("bio").length() > 200) return R.fail("简介过长（最多200字）");
            user.setBio(body.get("bio"));
        }
        userMapper.updateById(user);

        String token = JwtUtil.generate(user.getId(), user.getUsername(), user.getNickname());
        return R.ok(Map.of("token", token, "nickname", user.getNickname()));
    }

    /** 修改密码 */
    @PutMapping("/me/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) return R.fail("用户不存在");

        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        if (oldPwd == null || newPwd == null) return R.fail("参数不完整");
        if (newPwd.length() < 6) return R.fail("新密码至少6位");
        if (!encoder.matches(oldPwd, user.getPassword())) return R.fail("旧密码错误");

        user.setPassword(encoder.encode(newPwd));
        userMapper.updateById(user);
        return R.ok();
    }
}
