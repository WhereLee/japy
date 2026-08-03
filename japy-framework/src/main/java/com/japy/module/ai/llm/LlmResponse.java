package com.japy.module.ai.llm;

import lombok.Data;

/** LLM 调用结果（含 token 计量，用于审计与成本核算） */
@Data
public class LlmResponse {
    private final String content;
    private final int tokenIn;
    private final int tokenOut;
}
