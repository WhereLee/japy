package com.recloud.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.recloud.entity.Comment;
import com.recloud.vo.AdminCommentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 管理端评论分页查询（XML 实现）
     * <p>
     * LEFT JOIN user/annotation 关联评论者昵称与所评批注原文，直接产出 AdminCommentVO，消除 N+1。
     * 分页由 MyBatis-Plus 分页拦截器自动接管（第一个参数为 IPage）。
     */
    IPage<AdminCommentVO> selectAdminCommentPage(
            IPage<AdminCommentVO> page,
            @Param("keyword") String keyword);
}
