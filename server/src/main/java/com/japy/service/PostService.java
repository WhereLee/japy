package com.japy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.entity.Post;
import com.japy.entity.PostLike;
import com.japy.mapper.PostLikeMapper;
import com.japy.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final PostLikeMapper likeMapper;

    public List<Post> listByNovel(Long novelId) {
        return postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getNovelId, novelId)
                        .orderByDesc(Post::getCreatedAt)
                        .orderByDesc(Post::getId)
        );
    }

    public Post create(Post post) {
        post.setLikeCount(0);
        post.setCommentCount(0);
        postMapper.insert(post);
        return post;
    }

    public boolean delete(Long id) {
        return postMapper.deleteById(id) > 0;
    }

    @Transactional
    public boolean toggleLike(Long postId, String nickname) {
        PostLike existing = likeMapper.selectOne(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getNickname, nickname)
        );

        Post post = postMapper.selectById(postId);
        if (post == null) return false;

        if (existing != null) {
            likeMapper.deleteById(existing.getId());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            postMapper.updateById(post);
            return false; // 取消点赞
        } else {
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setNickname(nickname);
            likeMapper.insert(like);
            post.setLikeCount(post.getLikeCount() + 1);
            postMapper.updateById(post);
            return true; // 点赞成功
        }
    }

    public void incrementCommentCount(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post != null) {
            post.setCommentCount(post.getCommentCount() + 1);
            postMapper.updateById(post);
        }
    }
}
