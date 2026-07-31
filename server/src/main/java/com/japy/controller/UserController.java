package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.*;
import com.japy.entity.Comment;
import com.japy.entity.Post;
import com.japy.entity.User;
import com.japy.mapper.CommentMapper;
import com.japy.mapper.PostMapper;
import com.japy.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /** 修改个人资料（昵称/头像/简介），重签 token */
    @PutMapping("/me")
    public R<Map<String, Object>> updateProfile(@RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) return R.fail("用户不存在");

        if (body.containsKey("nickname") && !body.get("nickname").isBlank()) {
            user.setNickname(body.get("nickname"));
        }
        if (body.containsKey("avatar")) {
            user.setAvatar(body.get("avatar"));
        }
        if (body.containsKey("bio")) {
            user.setBio(body.get("bio"));
        }
        userMapper.updateById(user);

        // 重签 token（昵称可能变了）
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

    /** 公开主页 */
    @GetMapping("/{id}")
    public R<Map<String, Object>> profile(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null || user.getStatus() == 2) return R.fail("用户不存在");

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("nickname", user.getNickname());
        info.put("avatar", user.getAvatar());
        info.put("bio", user.getBio());
        info.put("createdAt", user.getCreatedAt());

        // 该用户的公开帖子（前10条）
        Page<Post> posts = postMapper.selectPage(new Page<>(1, 10),
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, id)
                        .eq(Post::getStatus, 0)
                        .orderByDesc(Post::getCreatedAt));
        info.put("posts", posts.getRecords());
        return R.ok(info);
    }

    /** 我的帖子（分页） */
    @GetMapping("/me/posts")
    public R<PageResult<Post>> myPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        Page<Post> result = postMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getUserId, userId)
                        .in(Post::getStatus, 0, 1)  // 自己能看到被隐藏的
                        .orderByDesc(Post::getCreatedAt));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    /** 我的评论（分页） */
    @GetMapping("/me/comments")
    public R<PageResult<Comment>> myComments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        Page<Comment> result = commentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getUserId, userId)
                        .in(Comment::getStatus, 0, 1)
                        .orderByDesc(Comment::getCreatedAt));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }
}
