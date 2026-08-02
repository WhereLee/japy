package com.japy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.japy.entity.Novel;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NovelMapper extends BaseMapper<Novel> {
}
