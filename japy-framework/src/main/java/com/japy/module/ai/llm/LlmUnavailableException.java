package com.japy.module.ai.llm;

/** LLM 不可用/调用失败（调用方应降级，不中断主链路） */
public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message) {
        super(message);
    }
}
