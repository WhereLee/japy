package com.japy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.japy.entity.NovelParagraph;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NovelParagraphMapper extends BaseMapper<NovelParagraph> {

    /**
     * 批量插入段落（单条 SQL 多条 VALUES，避免 6000+ 次单条插入的网络往返）。
     * 调用方需自行分批（每批上限由 PG 参数个数 65535 约束，500 条 × 5 参数安全）。
     */
    @Insert("<script>" +
            "INSERT INTO novel_paragraph (novel_id, chapter_no, para_seq, content, chars) VALUES " +
            "<foreach collection='list' item='p' separator=','>" +
            "(#{p.novelId}, #{p.chapterNo}, #{p.paraSeq}, #{p.content}, #{p.chars})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<NovelParagraph> list);
}
