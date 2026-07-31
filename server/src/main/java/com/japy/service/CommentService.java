package com.japy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.PageResult;
import com.japy.entity.Comment;
import com.japy.mapper.CommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;

    public PageResult<Comment> listByPost(Long postId, int page, int size) {
        Page<Comment> p = new Page<>(page, size);
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .eq(Comment::getStatus, 0)
                .orderByAsc(Comment::getCreatedAt)
                .orderByAsc(Comment::getId);
        Page<Comment> result = commentMapper.selectPage(p, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    public Comment create(Comment comment) {
        comment.setStatus(0);
        commentMapper.insert(comment);
        return comment;
    }

    /** 软删除 */
    public boolean softDelete(Long id) {
        return commentMapper.update(null,
                new LambdaUpdateWrapper<Comment>()
                        .eq(Comment::getId, id)
                        .set(Comment::getStatus, 2)) > 0;
    }
}
