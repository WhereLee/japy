package com.japy.module.novel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.japy.module.novel.entity.NovelChapter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NovelChapterMapper extends BaseMapper<NovelChapter> {

    /** 上一章：取比当前章节号小且最大的章节（按 novel 内排序） */
    @Select("SELECT * FROM jf_novel_chapter WHERE novel_id = #{novelId} AND chapter_no < #{chapterNo} " +
            "ORDER BY chapter_no DESC LIMIT 1")
    NovelChapter selectPrev(@Param("novelId") Long novelId, @Param("chapterNo") Integer chapterNo);

    /** 下一章：取比当前章节号大且最小的章节 */
    @Select("SELECT * FROM jf_novel_chapter WHERE novel_id = #{novelId} AND chapter_no > #{chapterNo} " +
            "ORDER BY chapter_no ASC LIMIT 1")
    NovelChapter selectNext(@Param("novelId") Long novelId, @Param("chapterNo") Integer chapterNo);
}
