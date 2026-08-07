package com.japy.module.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** LLM 提示词注册项：每个 LLM 场景(code) 的固定 system prompt，版本化存储 */
@Data
@TableName("ai_prompt")
public class AiPrompt {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;          // 场景标识：novel_qa / ops_interpret / feedback_analysis
    private String name;          // 场景名称
    private String systemPrompt;  // 固定 system prompt（不含检索临时塞入的文档）
    private Integer version;      // 版本号（同 code 递增）
    private Integer status;       // 1=当前生效 0=历史版本
    private Long updatedBy;       // 最后修改人
    private LocalDateTime updatedAt;
}
