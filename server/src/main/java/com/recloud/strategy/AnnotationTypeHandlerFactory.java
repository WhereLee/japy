package com.recloud.strategy;

import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 批注类型处理器工厂
 * <p>
 * 利用 Spring 自动注入所有 AnnotationTypeHandler 实现，
 * 构建 type → Handler 映射，运行时按 type 获取对应处理器。
 * <p>
 * 设计优势：
 * - 新增批注类型只需新增 Handler 实现类，无需修改工厂代码
 * - 符合开闭原则（OCP）
 */
@Component
public class AnnotationTypeHandlerFactory {

    private final Map<Integer, AnnotationTypeHandler> handlerMap;

    /**
     * Spring 自动注入所有 AnnotationTypeHandler 实现
     */
    public AnnotationTypeHandlerFactory(List<AnnotationTypeHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(AnnotationTypeHandler::getType, Function.identity()));
    }

    /**
     * 根据批注类型获取对应处理器
     *
     * @param type 批注类型（0=普通, 1=数据校验）
     * @return 对应的处理器
     * @throws BizException 如果类型不存在
     */
    public AnnotationTypeHandler getHandler(int type) {
        AnnotationTypeHandler handler = handlerMap.get(type);
        if (handler == null) {
            // 默认返回普通批注处理器
            handler = handlerMap.get(0);
            if (handler == null) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(), "不支持的批注类型: " + type);
            }
        }
        return handler;
    }
}
