package com.recloud.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recloud.entity.Annotation;
import com.recloud.entity.AnnotationFavorite;
import com.recloud.mapper.AnnotationFavoriteMapper;
import com.recloud.mapper.AnnotationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 批注收藏服务
 * <p>
 * 设计思路：
 * - 收藏/取消收藏是 toggle 操作（类似点赞）
 * - 收藏列表按收藏时间倒序（最近收藏的排前面）
 * - 收藏关联批注数据：查询时 JOIN annotation 表获取批注内容
 * - 批量查询收藏状态：用于前端标记"已收藏"图标
 * <p>
 * 与点赞的区别：
 * - 点赞：匿名计数（SCARD），不暴露谁赞了
 * - 收藏：用户维度，只有本人能看到自己的收藏列表
 * - 点赞影响排序（热门排行），收藏不影响
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationFavoriteService {

    private final AnnotationFavoriteMapper favoriteMapper;
    private final AnnotationMapper annotationMapper;

    /**
     * 收藏/取消收藏（toggle）
     *
     * @return { favorited: boolean } — true=收藏成功，false=取消收藏
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggle(Long annotationId, Long userId) {
        // 检查是否已收藏
        Long count = favoriteMapper.selectCount(
                new LambdaQueryWrapper<AnnotationFavorite>()
                        .eq(AnnotationFavorite::getAnnotationId, annotationId)
                        .eq(AnnotationFavorite::getUserId, userId)
        );

        if (count > 0) {
            // 已收藏 → 取消收藏
            favoriteMapper.delete(
                    new LambdaQueryWrapper<AnnotationFavorite>()
                            .eq(AnnotationFavorite::getAnnotationId, annotationId)
                            .eq(AnnotationFavorite::getUserId, userId)
            );
            return false;
        } else {
            // 未收藏 → 添加收藏
            AnnotationFavorite favorite = new AnnotationFavorite();
            favorite.setAnnotationId(annotationId);
            favorite.setUserId(userId);
            favoriteMapper.insert(favorite);
            return true;
        }
    }

    /**
     * 查询用户是否已收藏某批注
     */
    public boolean isFavorited(Long annotationId, Long userId) {
        return favoriteMapper.selectCount(
                new LambdaQueryWrapper<AnnotationFavorite>()
                        .eq(AnnotationFavorite::getAnnotationId, annotationId)
                        .eq(AnnotationFavorite::getUserId, userId)
        ) > 0;
    }

    /**
     * 批量查询当前用户对多条批注的收藏状态
     */
    public Set<Long> batchIsFavorited(List<Long> annotationIds, Long userId) {
        if (annotationIds == null || annotationIds.isEmpty()) {
            return Collections.emptySet();
        }
        return favoriteMapper.selectList(
                new LambdaQueryWrapper<AnnotationFavorite>()
                        .eq(AnnotationFavorite::getUserId, userId)
                        .in(AnnotationFavorite::getAnnotationId, annotationIds)
        ).stream().map(AnnotationFavorite::getAnnotationId).collect(Collectors.toSet());
    }

    /**
     * 查询我的收藏列表（分页，关联批注数据）
     * <p>
     * 按收藏时间倒序，返回批注完整信息
     */
    public IPage<Annotation> listMyFavorites(Long userId, int page, int size) {
        // 先查收藏记录（按收藏时间倒序）
        Page<AnnotationFavorite> favPage = new Page<>(page, size);
        List<AnnotationFavorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<AnnotationFavorite>()
                        .eq(AnnotationFavorite::getUserId, userId)
                        .orderByDesc(AnnotationFavorite::getCreatedAt)
        );

        if (favorites.isEmpty()) {
            return new Page<>(page, size);
        }

        // 获取批注ID列表
        List<Long> annotationIds = favorites.stream()
                .map(AnnotationFavorite::getAnnotationId)
                .collect(Collectors.toList());

        // 批量查询批注数据
        List<Annotation> annotations = annotationMapper.selectList(
                new LambdaQueryWrapper<Annotation>()
                        .in(Annotation::getId, annotationIds)
        );

        // 按收藏顺序排序（保持收藏时间倒序）
        List<Annotation> sorted = annotationIds.stream()
                .map(id -> annotations.stream()
                        .filter(a -> a.getId().equals(id))
                        .findFirst()
                        .orElse(null))
                .filter(a -> a != null)
                .collect(Collectors.toList());

        // 手动分页
        int from = (page - 1) * size;
        int to = Math.min(from + size, sorted.size());
        Page<Annotation> resultPage = new Page<>(page, size, favorites.size());
        resultPage.setRecords(from < sorted.size() ? sorted.subList(from, to) : Collections.emptyList());
        return resultPage;
    }

    /**
     * 查询用户收藏总数
     */
    public long countByUser(Long userId) {
        return favoriteMapper.selectCount(
                new LambdaQueryWrapper<AnnotationFavorite>()
                        .eq(AnnotationFavorite::getUserId, userId)
        );
    }
}
