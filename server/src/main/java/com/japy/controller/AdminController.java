package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.*;
import com.japy.entity.*;
import com.japy.mapper.*;
import com.japy.service.NotificationService;
import com.japy.service.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final ReportMapper reportMapper;
    private final SensitiveWordMapper wordMapper;
    private final NotificationMapper notificationMapper;
    private final OperationLogMapper logMapper;
    private final NotificationService notificationService;
    private final PointsService pointsService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private void log(String action, String targetType, Long targetId, String detail) {
        OperationLog l = new OperationLog();
        l.setAdminId(UserContext.getUserId());
        l.setAction(action);
        l.setTargetType(targetType);
        l.setTargetId(targetId);
        l.setDetail(detail);
        logMapper.insert(l);
    }

    // ===== Dashboard =====

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(null));
        data.put("postCount", postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getStatus, 0)));
        data.put("commentCount", commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, 0)));
        data.put("pendingReports", reportMapper.selectCount(new LambdaQueryWrapper<Report>().eq(Report::getStatus, 0)));
        return R.ok(data);
    }

    // ===== 用户管理 =====

    @GetMapping("/users")
    public R<PageResult<User>> users(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(User::getUsername, keyword).or().like(User::getNickname, keyword);
        }
        w.orderByDesc(User::getCreatedAt);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), w);
        result.getRecords().forEach(u -> u.setPassword(null));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @PutMapping("/users/{id}/ban")
    public R<Void> banUser(@PathVariable Long id, @RequestBody Map<String, String> body) {
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id).set(User::getStatus, 1));
        notificationService.send(id, "banned", null, null, "你的账号已被封禁，原因：" + body.getOrDefault("reason", "违规"));
        log("ban_user", "user", id, body.get("reason"));
        return R.ok();
    }

    @PutMapping("/users/{id}/unban")
    public R<Void> unbanUser(@PathVariable Long id) {
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id).set(User::getStatus, 0));
        log("unban_user", "user", id, null);
        return R.ok();
    }

    @PutMapping("/users/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id) {
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id).set(User::getPassword, encoder.encode("123456")));
        log("reset_password", "user", id, "重置为默认密码");
        return R.ok();
    }

    @PutMapping("/users/{id}/nickname")
    public R<Void> forceNickname(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String nick = body.get("nickname");
        if (nick == null || nick.isBlank()) return R.fail("昵称不能为空");
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id).set(User::getNickname, nick));
        log("force_nickname", "user", id, "强制改为: " + nick);
        return R.ok();
    }

    // ===== 内容管理 =====

    @GetMapping("/posts")
    public R<PageResult<Post>> posts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long novelId,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Post> w = new LambdaQueryWrapper<>();
        if (novelId != null) w.eq(Post::getNovelId, novelId);
        if (status != null) w.eq(Post::getStatus, status);
        w.orderByDesc(Post::getCreatedAt);
        Page<Post> result = postMapper.selectPage(new Page<>(page, size), w);
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @PutMapping("/posts/{id}/hide")
    public R<Void> hidePost(@PathVariable Long id) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, id).set(Post::getStatus, 1));
        Post p = postMapper.selectById(id);
        if (p != null && p.getUserId() != null) {
            notificationService.send(p.getUserId(), "hidden", "post", id, "你的帖子已被管理员隐藏");
            pointsService.earn(p.getUserId(), "penalty", -5);
        }
        log("hide_post", "post", id, null);
        return R.ok();
    }

    @PutMapping("/posts/{id}/restore")
    public R<Void> restorePost(@PathVariable Long id) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, id).set(Post::getStatus, 0));
        log("restore_post", "post", id, null);
        return R.ok();
    }

    @DeleteMapping("/posts/{id}")
    public R<Void> deletePost(@PathVariable Long id) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, id).set(Post::getStatus, 2));
        log("delete_post", "post", id, null);
        return R.ok();
    }

    @PutMapping("/posts/{id}/pin")
    public R<Void> pinPost(@PathVariable Long id) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, id).set(Post::getPinned, 1));
        log("pin_post", "post", id, null);
        return R.ok();
    }

    @PutMapping("/posts/{id}/unpin")
    public R<Void> unpinPost(@PathVariable Long id) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, id).set(Post::getPinned, 0));
        log("unpin_post", "post", id, null);
        return R.ok();
    }

    @PutMapping("/posts/{id}/feature")
    public R<Void> featurePost(@PathVariable Long id) {
        postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, id).set(Post::getFeatured, 1));
        Post p = postMapper.selectById(id);
        if (p != null && p.getUserId() != null) {
            pointsService.earn(p.getUserId(), "featured", 10);
        }
        log("feature_post", "post", id, null);
        return R.ok();
    }

    @PutMapping("/comments/{id}/hide")
    public R<Void> hideComment(@PathVariable Long id) {
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>().eq(Comment::getId, id).set(Comment::getStatus, 1));
        log("hide_comment", "comment", id, null);
        return R.ok();
    }

    @PutMapping("/comments/{id}/restore")
    public R<Void> restoreComment(@PathVariable Long id) {
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>().eq(Comment::getId, id).set(Comment::getStatus, 0));
        log("restore_comment", "comment", id, null);
        return R.ok();
    }

    @DeleteMapping("/comments/{id}")
    public R<Void> deleteComment(@PathVariable Long id) {
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>().eq(Comment::getId, id).set(Comment::getStatus, 2));
        log("delete_comment", "comment", id, null);
        return R.ok();
    }

    // ===== 举报管理 =====

    @GetMapping("/reports")
    public R<PageResult<Report>> reports(
            @RequestParam(defaultValue = "0") int status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Report> result = reportMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Report>().eq(Report::getStatus, status).orderByDesc(Report::getCreatedAt));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @PutMapping("/reports/{id}/resolve")
    public R<Void> resolveReport(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Report report = reportMapper.selectById(id);
        if (report == null) return R.fail("举报不存在");
        report.setStatus(1);
        report.setResult(body.getOrDefault("result", "内容已隐藏"));
        reportMapper.updateById(report);
        // 隐藏被举报内容
        if ("post".equals(report.getTargetType())) {
            postMapper.update(null, new LambdaUpdateWrapper<Post>().eq(Post::getId, report.getTargetId()).set(Post::getStatus, 1));
        } else if ("comment".equals(report.getTargetType())) {
            commentMapper.update(null, new LambdaUpdateWrapper<Comment>().eq(Comment::getId, report.getTargetId()).set(Comment::getStatus, 1));
        }
        // 通知举报者
        notificationService.send(report.getReporterId(), "report_result", report.getTargetType(), report.getTargetId(), "你的举报已处理：" + report.getResult());
        log("resolve_report", "report", id, report.getResult());
        return R.ok();
    }

    @PutMapping("/reports/{id}/reject")
    public R<Void> rejectReport(@PathVariable Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) return R.fail("举报不存在");
        report.setStatus(2);
        report.setResult("举报已驳回");
        reportMapper.updateById(report);
        notificationService.send(report.getReporterId(), "report_result", report.getTargetType(), report.getTargetId(), "你的举报已驳回");
        log("reject_report", "report", id, null);
        return R.ok();
    }

    // ===== 敏感词管理 =====

    @GetMapping("/sensitive-words")
    public R<List<SensitiveWord>> words() {
        return R.ok(wordMapper.selectList(null));
    }

    @PostMapping("/sensitive-words")
    public R<Void> addWord(@RequestBody Map<String, String> body) {
        String word = body.get("word");
        if (word == null || word.isBlank()) return R.fail("敏感词不能为空");
        SensitiveWord w = new SensitiveWord();
        w.setWord(word.trim());
        wordMapper.insert(w);
        log("add_word", "sensitive_word", null, word);
        return R.ok();
    }

    @DeleteMapping("/sensitive-words/{id}")
    public R<Void> deleteWord(@PathVariable Long id) {
        wordMapper.deleteById(id);
        log("delete_word", "sensitive_word", id, null);
        return R.ok();
    }

    // ===== 公告管理 =====

    @PostMapping("/announcements")
    public R<Void> announce(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) return R.fail("公告内容不能为空");
        // 广播给所有用户
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getStatus, 0));
        for (User u : users) {
            notificationService.send(u.getId(), "announcement", null, null, content);
        }
        log("announcement", null, null, content);
        return R.ok();
    }

    // ===== 操作日志 =====

    @GetMapping("/logs")
    public R<PageResult<OperationLog>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action) {
        LambdaQueryWrapper<OperationLog> w = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) w.eq(OperationLog::getAction, action);
        w.orderByDesc(OperationLog::getCreatedAt);
        Page<OperationLog> result = logMapper.selectPage(new Page<>(page, size), w);
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }
}
