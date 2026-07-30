package com.recloud.strategy;

import com.recloud.dto.request.CreateAnnotationRequest;
import com.recloud.entity.Annotation;

/**
 * 批注类型处理策略接口
 * <p>
 * 不同批注类型（普通/数据校验）有不同的校验规则和创建后处理逻辑。
 * 使用策略模式替代 if-else，符合开闭原则。
 */
public interface AnnotationTypeHandler {

    /**
     * 获取处理的批注类型
     */
    int getType();

    /**
     * 校验批注请求参数（不同类型有不同校验规则）
     */
    void validate(CreateAnnotationRequest request);

    /**
     * 批注创建后的处理（如更新缓存、标记审核等）
     */
    void afterCreate(Annotation annotation);

    /**
     * 显示类型名称
     */
    String getDisplayType();
}
