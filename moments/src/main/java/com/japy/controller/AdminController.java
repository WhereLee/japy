package com.japy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.*;import com.japy.entity.*;
import com.japy.mapper.*;
import com.japy.service.NotificationService;
import com.japy.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserMapper userMapper;
    private final MomentMapper momentMapper;
    private final CommentMapper commentMapper;
    private final ReportMapper reportMapper;
    private final SensitiveWordMapper wordMapper;
    private final OperationLogMapper logMapper;
    private final NotificationService notificationService;
    private final SensitiveWordService sensitiveWordService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // ===== Dashboard =====

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(null));
        data.put("momentCount", momentMapper.selectCount(new LambdaQueryWrapper<Moment>().eq(Moment::getStatus, 0)));
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
            w.and(q -> q.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        }
        w.orderByDesc(User::getCreatedAt);
        Page<User> result = userMapper.selectPage(new Page<>(PageParams.page(page), PageParams.size(size)), w);
        result.getRecords().forEach(u -> u.setPassword(null));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @PutMapping("/users/{id}/ban")
    @AdminLog(action = "ban_user")
    public R<Void> banUser(@PathVariable Long id, @RequestBody Map<String, String> body) {
        if (id.equals(UserContext.getUserId())) return R.fail("不能封禁自己");
        User user = userMapper.selectById(id);
        if (user == null) return R.fail("用户不存在");
        if (user.getStatus() != null && user.getStatus() == 1) return R.fail("用户已被封禁");
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id).set(User::getStatus, 1));
        notificationService.send(id, "banned", null, null, "你的账号已被封禁，原因：" + body.getOrDefault("reason", "违规"));
        return R.ok();
    }

    @PutMapping("/users/{id}/unban")
    @AdminLog(action = "unban_user")
    public R<Void> unbanUser(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) return R.fail("用户不存在");
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id).set(User::getStatus, 0));
        return R.ok();
    }

    @PutMapping("/users/{id}/reset-password")
    @AdminLog(action = "reset_password")
    public R<Void> resetPassword(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) return R.fail("用户不存在");
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id)
                .set(User::getPassword, encoder.encode("123456")));
        return R.ok();
    }

    @PutMapping("/users/{id}/nickname")
    @AdminLog(action = "force_nickname")
    public R<Void> forceNickname(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String nick = body.get("nickname");
        if (nick == null || nick.isBlank()) return R.fail("昵称不能为空");
        User user = userMapper.selectById(id);
        if (user == null) return R.fail("用户不存在");
        userMapper.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id).set(User::getNickname, nick));
        return R.ok();
    }

    // ===== 动态管理 =====

    @GetMapping("/moments")
    public R<PageResult<Moment>> moments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status) {
        LambdaQueryWrapper<Moment> w = new LambdaQueryWrapper<>();
        if (status != null) w.eq(Moment::getStatus, status);
        w.orderByDesc(Moment::getCreatedAt);
        Page<Moment> result = momentMapper.selectPage(new Page<>(PageParams.page(page), PageParams.size(size)), w);
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @PutMapping("/moments/{id}/hide")
    @AdminLog(action = "hide_moment")
    public R<Void> hideMoment(@PathVariable Long id) {
        Moment m = momentMapper.selectById(id);
        if (m == null) return R.fail("动态不存在");
        if (m.getStatus() == 1) return R.fail("动态已被隐藏");
        if (m.getStatus() == 2) return R.fail("动态已被删除，无法隐藏");
        momentMapper.update(null, new LambdaUpdateWrapper<Moment>().eq(Moment::getId, id).set(Moment::getStatus, 1));
        notificationService.send(m.getUserId(), "hidden", "moment", id, "你的动态已被管理员隐藏");
        return R.ok();
    }

    @PutMapping("/moments/{id}/restore")
    @AdminLog(action = "restore_moment")
    public R<Void> restoreMoment(@PathVariable Long id) {
        Moment m = momentMapper.selectById(id);
        if (m == null) return R.fail("动态不存在");
        if (m.getStatus() == 0) return R.fail("动态状态正常，无需恢复");
        momentMapper.update(null, new LambdaUpdateWrapper<Moment>().eq(Moment::getId, id).set(Moment::getStatus, 0));
        return R.ok();
    }

    @DeleteMapping("/moments/{id}")
    @AdminLog(action = "delete_moment")
    public R<Void> deleteMoment(@PathVariable Long id) {
        Moment m = momentMapper.selectById(id);
        if (m == null) return R.fail("动态不存在");
        if (m.getStatus() == 2) return R.fail("动态已被删除");
        momentMapper.update(null, new LambdaUpdateWrapper<Moment>().eq(Moment::getId, id).set(Moment::getStatus, 2));
        return R.ok();
    }

    @PutMapping("/moments/{id}/pin")
    @AdminLog(action = "pin_moment")
    public R<Void> pinMoment(@PathVariable Long id) {
        Moment m = momentMapper.selectById(id);
        if (m == null) return R.fail("动态不存在");
        if (m.getStatus() != 0) return R.fail("只能置顶正常状态的动态");
        momentMapper.update(null, new LambdaUpdateWrapper<Moment>().eq(Moment::getId, id).set(Moment::getPinned, 1));
        return R.ok();
    }

    @PutMapping("/moments/{id}/unpin")
    @AdminLog(action = "unpin_moment")
    public R<Void> unpinMoment(@PathVariable Long id) {
        Moment m = momentMapper.selectById(id);
        if (m == null) return R.fail("动态不存在");
        momentMapper.update(null, new LambdaUpdateWrapper<Moment>().eq(Moment::getId, id).set(Moment::getPinned, 0));
        return R.ok();
    }

    // ===== 评论管理 =====

    @GetMapping("/comments")
    public R<PageResult<Comment>> comments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Comment> p = commentMapper.selectPage(new Page<>(PageParams.page(page), PageParams.size(size)),
                new LambdaQueryWrapper<Comment>().orderByDesc(Comment::getId));
        return R.ok(PageResult.of(p.getRecords(), p.getTotal(), page, size));
    }

    @PutMapping("/comments/{id}/hide")
    @AdminLog(action = "hide_comment")
    public R<Void> hideComment(@PathVariable Long id) {
        Comment c = commentMapper.selectById(id);
        if (c == null) return R.fail("评论不存在");
        if (c.getStatus() == 1) return R.fail("评论已被隐藏");
        if (c.getStatus() == 2) return R.fail("评论已被删除");
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>().eq(Comment::getId, id).set(Comment::getStatus, 1));
        // 计数与可见评论保持一致（隐藏则减一）
        momentMapper.update(null, new LambdaUpdateWrapper<Moment>()
                .eq(Moment::getId, c.getMomentId())
                .setSql("comment_count = GREATEST(0, comment_count - 1)"));
        return R.ok();
    }

    @PutMapping("/comments/{id}/restore")
    @AdminLog(action = "restore_comment")
    public R<Void> restoreComment(@PathVariable Long id) {
        Comment c = commentMapper.selectById(id);
        if (c == null) return R.fail("评论不存在");
        if (c.getStatus() == 0) return R.fail("评论状态正常，无需恢复");
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>().eq(Comment::getId, id).set(Comment::getStatus, 0));
        // 计数与可见评论保持一致（恢复则加一）
        momentMapper.update(null, new LambdaUpdateWrapper<Moment>()
                .eq(Moment::getId, c.getMomentId())
                .setSql("comment_count = comment_count + 1"));
        return R.ok();
    }

    @DeleteMapping("/comments/{id}")
    @AdminLog(action = "delete_comment")
    public R<Void> deleteComment(@PathVariable Long id) {
        Comment c = commentMapper.selectById(id);
        if (c == null) return R.fail("评论不存在");
        if (c.getStatus() == 2) return R.fail("评论已被删除");
        boolean wasVisible = c.getStatus() == 0;
        commentMapper.update(null, new LambdaUpdateWrapper<Comment>().eq(Comment::getId, id).set(Comment::getStatus, 2));
        // 仅当原为可见状态时递减计数
        if (wasVisible) {
            momentMapper.update(null, new LambdaUpdateWrapper<Moment>()
                    .eq(Moment::getId, c.getMomentId())
                    .setSql("comment_count = GREATEST(0, comment_count - 1)"));
        }
        return R.ok();
    }

    // ===== 举报管理 =====

    @GetMapping("/reports")
    public R<PageResult<Report>> reports(
            @RequestParam(defaultValue = "0") int status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Report> result = reportMapper.selectPage(new Page<>(PageParams.page(page), PageParams.size(size)),
                new LambdaQueryWrapper<Report>().eq(Report::getStatus, status).orderByDesc(Report::getCreatedAt));
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @PutMapping("/reports/{id}/resolve")
    @AdminLog(action = "resolve_report")
    public R<Void> resolveReport(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Report report = reportMapper.selectById(id);
        if (report == null) return R.fail("举报不存在");
        if (report.getStatus() != 0) return R.fail("举报已处理，无法重复操作");
        report.setStatus(1);
        report.setResult(body.getOrDefault("result", "内容已隐藏"));
        reportMapper.updateById(report);
        // 隐藏被举报内容（仅当内容存在且状态正常时）
        if ("moment".equals(report.getTargetType())) {
            Moment m = momentMapper.selectById(report.getTargetId());
            if (m != null && m.getStatus() == 0) {
                momentMapper.update(null, new LambdaUpdateWrapper<Moment>()
                        .eq(Moment::getId, report.getTargetId()).set(Moment::getStatus, 1));
            }
        } else if ("comment".equals(report.getTargetType())) {
            Comment c = commentMapper.selectById(report.getTargetId());
            if (c != null && c.getStatus() == 0) {
                commentMapper.update(null, new LambdaUpdateWrapper<Comment>()
                        .eq(Comment::getId, report.getTargetId()).set(Comment::getStatus, 1));
            }
        }
        notificationService.send(report.getReporterId(), "report_result", report.getTargetType(),
                report.getTargetId(), "你的举报已处理：" + report.getResult());
        return R.ok();
    }

    @PutMapping("/reports/{id}/reject")
    @AdminLog(action = "reject_report")
    public R<Void> rejectReport(@PathVariable Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) return R.fail("举报不存在");
        if (report.getStatus() != 0) return R.fail("举报已处理，无法重复操作");
        report.setStatus(2);
        report.setResult("举报已驳回");
        reportMapper.updateById(report);
        notificationService.send(report.getReporterId(), "report_result", report.getTargetType(),
                report.getTargetId(), "你的举报已驳回");
        return R.ok();
    }

    // ===== 敏感词管理 =====

    @GetMapping("/sensitive-words")
    public R<List<SensitiveWord>> words() {
        return R.ok(wordMapper.selectList(null));
    }

    @PostMapping("/sensitive-words")
    @AdminLog(action = "add_word")
    public R<Void> addWord(@RequestBody Map<String, String> body) {
        String word = body.get("word");
        if (word == null || word.isBlank()) return R.fail("敏感词不能为空");
        Long exists = wordMapper.selectCount(new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getWord, word.trim()));
        if (exists > 0) return R.fail("敏感词已存在");
        SensitiveWord w = new SensitiveWord();
        w.setWord(word.trim());
        wordMapper.insert(w);
        sensitiveWordService.invalidateCache();
        return R.ok();
    }

    @DeleteMapping("/sensitive-words/{id}")
    @AdminLog(action = "delete_word")
    public R<Void> deleteWord(@PathVariable Long id) {
        wordMapper.deleteById(id);
        sensitiveWordService.invalidateCache();
        return R.ok();
    }

    // ===== 公告 =====

    @PostMapping("/announcements")
    @AdminLog(action = "announcement")
    public R<Void> announce(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) return R.fail("公告内容不能为空");
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getStatus, 0));
        for (User u : users) {
            notificationService.send(u.getId(), "announcement", null, null, content);
        }
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
        Page<OperationLog> result = logMapper.selectPage(new Page<>(PageParams.page(page), PageParams.size(size)), w);
        return R.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }
}
