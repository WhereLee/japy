package com.recloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.recloud.entity.Annotation;
import com.recloud.vo.AdminAnnotationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AnnotationMapper extends BaseMapper<Annotation> {

    /**
     * 管理端批注分页查询（XML 实现）
     * <p>
     * 一次 LEFT JOIN user/chapter/novel 直接查出含用户昵称、章节标题、小说标题的 VO，
     * 消除原控制器中“逐条查用户/章节/小说”的 N+1 问题。
     * 分页由 MyBatis-Plus 分页拦截器自动接管（第一个参数为 IPage）。
     */
    IPage<AdminAnnotationVO> selectAdminAnnotationPage(
            IPage<AdminAnnotationVO> page,
            @Param("keyword") String keyword,
            @Param("type") Integer type);

    /**
     * 原子更新点赞数（like_count = like_count + delta）
     * 避免先查后改的并发问题
     */
    @Update("UPDATE annotation SET like_count = like_count + #{delta} WHERE id = #{id}")
    int updateLikeCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 原子更新评论数（comment_count = comment_count + delta）
     */
    @Update("UPDATE annotation SET comment_count = comment_count + #{delta} WHERE id = #{id}")
    int updateCommentCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 直接设置点赞数（用于 Redis 回写 DB）
     * 与 updateLikeCount 的区别：这是 SET 而非 INCREMENT
     */
    @Update("UPDATE annotation SET like_count = #{count} WHERE id = #{id}")
    int updateLikeCountDirect(@Param("id") Long id, @Param("count") int count);
}
