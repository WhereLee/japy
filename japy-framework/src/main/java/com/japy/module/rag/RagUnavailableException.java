package com.japy.module.rag;

/** RAG 服务不可用（Python 未启动/超时）——业务层转友好提示 */
public class RagUnavailableException extends RuntimeException {
    public RagUnavailableException(String msg) {
        super(msg);
    }
}
