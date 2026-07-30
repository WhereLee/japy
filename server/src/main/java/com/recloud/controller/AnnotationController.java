package com.recloud.controller;

import com.recloud.common.annotation.Log;
import com.recloud.common.annotation.RateLimiter;
import com.recloud.common.result.R;
import com.recloud.dto.request.CreateAnnotationRequest;
import com.recloud.entity.Annotation;
import com.recloud.security.SecurityUtils;
import com.recloud.service.AnnotationLikeService;
import com.recloud.service.AnnotationService;
import com.recloud.service.AnnotationFavoriteService;
import com.recloud.service.HotAnnotationService;
import com.recloud.vo.AnnotationVO;
import com.recloud.vo.VOConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "批注管理", description = "批注CRUD/点赞/点赞状态")
@RestController
@RequestMapping("/api/annotations")
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationService annotationService;
    private final AnnotationLikeService likeService;
    private final AnnotationFavoriteService favoriteService;
    private final HotAnnotationService hotAnnotationService;

    @Operation(summary = "创建批注")
    @PostMapping
    @Log(module = "批注", operation = "创建批注")
    @RateLimiter(limit = 10, time = 60, key = "create_annotation", strict = true)
    public R<AnnotationVO> create(@Valid @RequestBody CreateAnnotationRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Annotation annotation = annotationService.create(
                request.getChapterId(), userId,
                request.getAnchorStart(), request.getAnchorEnd(),
                request.getSelectedText(), request.getContent(),
                request.getType()
        );
        return R.ok(VOConverter.toVO(annotation));
    }

    @Operation(summary = "查询我的批注列表")
    @GetMapping("/mine")
    public R<List<AnnotationVO>> listMyAnnotations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<Annotation> list = annotationService.listByUser(userId, page, size);
        return R.ok(VOConverter.toAnnotationVOList(list));
    }

    @Operation(summary = "按章节查询批注列表（分页）")
    @GetMapping
    public R<List<AnnotationVO>> listByChapter(
            @RequestParam Long chapterId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<AnnotationVO> voList = VOConverter.toAnnotationVOList(
                annotationService.listByChapter(chapterId, page, size));
        // 批量填充当前用户的点赞状态（1次查询替代N次）
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            java.util.List<Long> annIds = voList.stream()
                    .map(AnnotationVO::getId)
                    .collect(java.util.stream.Collectors.toList());
            java.util.Set<Long> likedIds = likeService.batchIsLiked(annIds, userId);
            for (AnnotationVO vo : voList) {
                vo.setLikedByCurrentUser(likedIds.contains(vo.getId()));
            }
        } catch (Exception e) {
            // 未登录时 likedByCurrentUser 保持 null
        }
        return R.ok(voList);
    }

    @Operation(summary = "删除批注")
    @DeleteMapping("/{id}")
    @Log(module = "批注", operation = "删除批注")
    public R<Map<String, Object>> delete(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean deleted = annotationService.deleteAnnotation(id, userId);
        return R.ok(Map.of("success", deleted));
    }

    @Operation(summary = "点赞/取消点赞")
    @PostMapping("/{id}/like")
    @Log(module = "批注", operation = "点赞")
    public R<Map<String, Object>> toggleLike(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(likeService.toggle(id, userId));
    }

    @Operation(summary = "查询点赞状态")
    @GetMapping("/{id}/like-status")
    public R<Map<String, Object>> likeStatus(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean liked = likeService.isLiked(id, userId);
        long likeCount = likeService.countByAnnotation(id);
        return R.ok(Map.of("liked", liked, "likeCount", likeCount));
    }

    @Operation(summary = "热门批注排行（最近7天，按评分排序）")
    @GetMapping("/hot")
    public R<List<Map<String, Object>>> hotAnnotations(
            @RequestParam(defaultValue = "20") int topN) {
        return R.ok(hotAnnotationService.getHotAnnotations(topN));
    }

    @Operation(summary = "收藏/取消收藏批注")
    @PostMapping("/{id}/favorite")
    @Log(module = "用户", operation = "收藏批注")
    public R<Map<String, Object>> toggleFavorite(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean favorited = favoriteService.toggle(id, userId);
        return R.ok(Map.of("favorited", favorited));
    }

    @Operation(summary = "查询我的收藏列表")
    @GetMapping("/favorites")
    public R<?> listMyFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        return R.ok(favoriteService.listMyFavorites(userId, page, size));
    }
}
