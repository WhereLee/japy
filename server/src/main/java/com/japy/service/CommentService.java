package com.japy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.entity.Comment;
import com.japy.mapper.CommentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;

    public List<Comment> listByPost(Long postId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .orderByAsc(Comment::getCreatedAt)
        );
    }

    public Comment create(Comment comment) {
        commentMapper.insert(comment);
        return comment;
    }

    public boolean delete(Long id) {
        return commentMapper.deleteById(id) > 0;
    }
}
