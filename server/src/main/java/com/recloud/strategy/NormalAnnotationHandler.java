package com.recloud.strategy;

import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import com.recloud.dto.request.CreateAnnotationRequest;
import com.recloud.entity.Annotation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 普通批注处理器（type=0）
 *
 * 校验规则：
 * - anchorStart < anchorEnd
 * - selectedText 长度 ≤ 200 字符
 * - content 长度 ≤ 1000 字符
 */
@Slf4j
@Component
public class NormalAnnotationHandler implements AnnotationTypeHandler {

    private static final int MAX_SELECTED_TEXT_LENGTH = 200;
    private static final int MAX_CONTENT_LENGTH = 1000;

    @Override
    public int getType() {
        return 0;
    }

    @Override
    public void validate(CreateAnnotationRequest request) {
        // 校验偏移量范围
        if (request.getAnchorStart() != null && request.getAnchorEnd() != null) {
            if (request.getAnchorStart() >= request.getAnchorEnd()) {
                throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                        "起始偏移必须小于结束偏移");
            }
        }

        // 校验 selectedText 长度
        if (request.getSelectedText() != null && request.getSelectedText().length() > MAX_SELECTED_TEXT_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "选中文本不能超过" + MAX_SELECTED_TEXT_LENGTH + "字符");
        }

        // 校验 content 长度
        if (request.getContent() != null && request.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "批注内容不能超过" + MAX_CONTENT_LENGTH + "字符");
        }
    }

    @Override
    public void afterCreate(Annotation annotation) {
        log.debug("普通批注创建完成: id={}, chapterId={}", annotation.getId(), annotation.getChapterId());
    }

    @Override
    public String getDisplayType() {
        return "普通批注";
    }
}
