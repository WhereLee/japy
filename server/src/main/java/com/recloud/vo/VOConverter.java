package com.recloud.vo;

import com.recloud.entity.Annotation;
import com.recloud.entity.Chapter;
import com.recloud.entity.Comment;
import com.recloud.entity.Novel;
import com.recloud.entity.User;

import java.util.List;
import java.util.stream.Collectors;

/**
 * VO 转换工具 —— Entity → VO
 * <p>
 * 手写 Converter，避免引入 MapStruct 依赖。
 * 后续可替换为 MapStruct 或 BeanUtils。
 */
public class VOConverter {

    private VOConverter() {}

    // ==================== User ====================

    public static UserVO toVO(User user) {
        if (user == null) return null;
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    public static List<UserVO> toUserVOList(List<User> users) {
        return users.stream().map(VOConverter::toVO).collect(Collectors.toList());
    }

    // ==================== Novel ====================

    public static NovelVO toVO(Novel novel) {
        if (novel == null) return null;
        NovelVO vo = new NovelVO();
        vo.setId(novel.getId());
        vo.setTitle(novel.getTitle());
        vo.setAuthor(novel.getAuthor());
        vo.setDescription(novel.getDescription());
        vo.setFileName(novel.getFileName());
        vo.setCreatedAt(novel.getCreatedAt());
        return vo;
    }

    public static List<NovelVO> toNovelVOList(List<Novel> novels) {
        return novels.stream().map(VOConverter::toVO).collect(Collectors.toList());
    }

    // ==================== Annotation ====================

    public static AnnotationVO toVO(Annotation annotation) {
        if (annotation == null) return null;
        AnnotationVO vo = new AnnotationVO();
        vo.setId(annotation.getId());
        vo.setChapterId(annotation.getChapterId());
        vo.setUserId(annotation.getUserId());
        vo.setAnchorStart(annotation.getAnchorStart());
        vo.setAnchorEnd(annotation.getAnchorEnd());
        vo.setSelectedText(annotation.getSelectedText());
        vo.setContent(annotation.getContent());
        vo.setType(annotation.getType());
        vo.setLikeCount(annotation.getLikeCount());
        vo.setCommentCount(annotation.getCommentCount());
        vo.setCreatedAt(annotation.getCreatedAt());
        return vo;
    }

    public static List<AnnotationVO> toAnnotationVOList(List<Annotation> annotations) {
        return annotations.stream().map(VOConverter::toVO).collect(Collectors.toList());
    }

    // ==================== Comment ====================

    public static CommentVO toVO(Comment comment) {
        if (comment == null) return null;
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setAnnotationId(comment.getAnnotationId());
        vo.setUserId(comment.getUserId());
        vo.setReplyToId(comment.getReplyToId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        return vo;
    }

    public static List<CommentVO> toCommentVOList(List<Comment> comments) {
        return comments.stream().map(VOConverter::toVO).collect(Collectors.toList());
    }

    // ==================== Chapter ====================

    public static ChapterVO toVO(Chapter chapter) {
        if (chapter == null) return null;
        ChapterVO vo = new ChapterVO();
        vo.setId(chapter.getId());
        vo.setNovelId(chapter.getNovelId());
        vo.setTitle(chapter.getTitle());
        vo.setContent(chapter.getContent());
        vo.setChapterOrder(chapter.getChapterOrder());
        vo.setCreatedAt(chapter.getCreatedAt());
        return vo;
    }

    public static List<ChapterVO> toChapterVOList(List<Chapter> chapters) {
        return chapters.stream().map(VOConverter::toVO).collect(Collectors.toList());
    }
}
