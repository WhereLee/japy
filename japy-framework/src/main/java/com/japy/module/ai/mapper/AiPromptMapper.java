package com.japy.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.japy.module.ai.entity.AiPrompt;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface AiPromptMapper extends BaseMapper<AiPrompt> {

    /** 查询某场景当前生效版本 */
    @Select("SELECT * FROM ai_prompt WHERE code = #{code} AND status = 1 LIMIT 1")
    AiPrompt selectActiveByCode(@Param("code") String code);

    /** 查询某场景全部版本（新→旧） */
    @Select("SELECT * FROM ai_prompt WHERE code = #{code} ORDER BY version DESC")
    List<AiPrompt> selectVersions(@Param("code") String code);

    /** 查询全部场景的当前生效版本 */
    @Select("SELECT p.* FROM ai_prompt p JOIN (SELECT code, MAX(version) v FROM ai_prompt WHERE status = 1 GROUP BY code) a " +
            "ON p.code = a.code AND p.version = a.v ORDER BY p.code")
    List<AiPrompt> selectAllActive();
}
