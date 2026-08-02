package com.japy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.japy.entity.NovelChapter;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NovelChapterMapper extends BaseMapper<NovelChapter> {

    /** 批量插入章节 */
    @Insert("<script>" +
            "INSERT INTO novel_chapter (novel_id, chapter_no, title, chars, paragraph_count, max_para_chars) VALUES " +
            "<foreach collection='list' item='c' separator=','>" +
            "(#{c.novelId}, #{c.chapterNo}, #{c.title}, #{c.chars}, #{c.paragraphCount}, #{c.maxParaChars})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<NovelChapter> list);
}
