package com.recloud.strategy;

import com.recloud.common.exception.BizException;
import com.recloud.common.result.ResultCode;
import com.recloud.dto.request.CreateAnnotationRequest;
import com.recloud.entity.Annotation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据校验批注处理器（type=1）
 *
 * 校验规则：
 * - anchorStart < anchorEnd
 * - selectedText 必填且 ≤ 500 字符
 * - content 长度 ≤ 2000 字符
 * - 创建后标记需要审核
 */
@Slf4j
@Component
public class ValidateAnnotationHandler implements AnnotationTypeHandler {

    private static final int MAX_SELECTED_TEXT_LENGTH = 500;
    private static final int MAX_CONTENT_LENGTH = 2000;

    @Override
    public int getType() {
        return 1;
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

        // 数据校验批注：selectedText 必填
        if (request.getSelectedText() == null || request.getSelectedText().isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "数据校验批注必须包含选中文本");
        }

        // 校验 selectedText 长度
        if (request.getSelectedText().length() > MAX_SELECTED_TEXT_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "数据校验批注的选中文本不能超过" + MAX_SELECTED_TEXT_LENGTH + "字符");
        }

        // 校验 content 长度
        if (request.getContent() != null && request.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(),
                    "批注内容不能超过" + MAX_CONTENT_LENGTH + "字符");
        }
    }

    @Override
    public void afterCreate(Annotation annotation) {
        // 数据校验批注创建后，标记需要审核
        log.info("数据校验批注创建，标记需要审核: id={}, chapterId={}", annotation.getId(), annotation.getChapterId());
        // 未来可在此处发送审核消息到 MQ
    }

    @Override
    public String getDisplayType() {
        return "数据校验批注";
    }
}
