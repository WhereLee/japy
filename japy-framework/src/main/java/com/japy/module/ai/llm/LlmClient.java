package com.japy.module.ai.llm;

/**
 * LLM 客户端抽象：L1 层唯一入口。
 * 当前实现为 DeepSeek（OpenAI 兼容）；将来若接入 langgraph/Python 服务，
 * 只需新增实现类，检测器/审批流/反馈层零改动。
 */
public interface LlmClient {

    /** 是否可用（API key 是否已配置）；不可用时整个 L1 层优雅降级为纯规则 */
    boolean available();

    /** 同步调用：system 提示 + user 内容，返回文本与 token 计量 */
    LlmResponse chat(String system, String user);
}
