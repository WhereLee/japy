package com.japy.controller;

import com.japy.common.PageParams;
import com.japy.common.PageResult;
import com.japy.common.R;
import com.japy.common.UserContext;
import com.japy.entity.Moment;
import com.japy.entity.MomentLike;
import com.japy.mapper.MomentMapper;
import com.japy.mapper.NovelMapper;
import com.japy.service.MomentService;
import com.japy.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/moments")
@RequiredArgsConstructor
public class MomentController {

    private final MomentService momentService;
    private final MomentMapper momentMapper;
    private final NovelMapper novelMapper;
    private final SensitiveWordService sensitiveWordService;

    /**
     * 全局时间线（公开）。
     * 兼容两种分页：page/size 传统分页；cursor 游标分页（滚动加载，不重复不遗漏）。
     * cursor 格式：上一页最后一条的 "createdAt_id"，如 "2026-08-02T23:22:15.736229_123"
     */
    @GetMapping
    public R<PageResult<Moment>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) {
        if (cursor != null && !cursor.isBlank()) {
            return R.ok(momentService.listByCursor(cursor, PageParams.size(size), UserContext.getUserId()));
        }
        return R.ok(momentService.list(PageParams.page(page), PageParams.size(size), UserContext.getUserId()));
    }

    /** 发动态（可关联小说：novelId 可选） */
    @PostMapping
    public R<Moment> create(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) return R.fail("内容不能为空");
        if (content.length() > 2000) return R.fail("内容过长（最多2000字）");
        String hit = sensitiveWordService.check(content);
        if (hit != null) return R.fail("内容包含敏感词：" + hit);
        // 关联小说（可选）：校验存在
        Long novelId = null;
        if (body.get("novelId") != null && !body.get("novelId").isBlank()) {
            try {
                novelId = Long.valueOf(body.get("novelId"));
            } catch (NumberFormatException e) {
                return R.fail("novelId 格式错误");
            }
            if (novelMapper.selectById(novelId) == null) {
                return R.fail("关联的小说不存在");
            }
        }
        Moment moment = momentService.create(UserContext.getUserId(), UserContext.getNickname(), content, novelId);
        return R.ok(moment);
    }

    /** 删除自己的动态 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        if (!momentService.softDelete(id, UserContext.getUserId())) {
            return R.fail("动态不存在或无权删除");
        }
        return R.ok();
    }

    /** 点赞/取消点赞。动态不存在或非正常状态时明确返回 400（语义不再模糊） */
    @PostMapping("/{id}/like")
    public R<Map<String, Object>> like(@PathVariable Long id) {
        Moment moment = momentMapper.selectById(id);
        if (moment == null || moment.getStatus() != 0) {
            return R.fail("动态不存在");
        }
        boolean liked = momentService.toggleLike(id, UserContext.getUserId(), UserContext.getNickname());
        return R.ok(Map.of("liked", liked));
    }

    /** 赞列表（谁赞了） */
    @GetMapping("/{id}/likes")
    public R<PageResult<MomentLike>> likes(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        Moment moment = momentMapper.selectById(id);
        if (moment == null || moment.getStatus() == 2) {
            return R.fail("动态不存在");
        }
        return R.ok(momentService.likes(id, PageParams.page(page), PageParams.size(size)));
    }
}
