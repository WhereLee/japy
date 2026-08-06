package com.japy.module.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.japy.module.audit.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}
