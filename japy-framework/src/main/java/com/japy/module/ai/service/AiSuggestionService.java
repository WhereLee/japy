package com.japy.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.japy.common.BusinessException;
import com.japy.common.SecurityUtils;
import com.japy.module.ai.entity.AiSuggestion;
import com.japy.module.ai.mapper.AiSuggestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 建议卡（L2 人工审批流）：待审 → 批准/驳回 → 执行。AI 只起草，人决策。 */
@Service
@RequiredArgsConstructor
public class AiSuggestionService {

    private final AiSuggestionMapper suggestionMapper;

    public IPage<AiSuggestion> list(Integer status, int page, int size) {
        LambdaQueryWrapper<AiSuggestion> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(AiSuggestion::getStatus, status);
        }
        qw.orderByDesc(AiSuggestion::getId);
        return suggestionMapper.selectPage(new Page<>(page, size), qw);
    }

    /** 批准（仍由人工执行） */
    public void approve(Long id) {
        AiSuggestion s = require(id);
        s.setStatus(1);
        s.setHandledBy(SecurityUtils.userId());
        s.setHandledAt(LocalDateTime.now());
        suggestionMapper.updateById(s);
    }

    /** 驳回 */
    public void reject(Long id) {
        AiSuggestion s = require(id);
        s.setStatus(2);
        s.setHandledBy(SecurityUtils.userId());
        s.setHandledAt(LocalDateTime.now());
        suggestionMapper.updateById(s);
    }

    /** 标记已执行 */
    public void execute(Long id) {
        AiSuggestion s = require(id);
        s.setStatus(3);
        s.setHandledBy(SecurityUtils.userId());
        s.setHandledAt(LocalDateTime.now());
        suggestionMapper.updateById(s);
    }

    public List<AiSuggestion> pending() {
        return suggestionMapper.selectList(new LambdaQueryWrapper<AiSuggestion>()
                .eq(AiSuggestion::getStatus, 0).orderByDesc(AiSuggestion::getId));
    }

    private AiSuggestion require(Long id) {
        AiSuggestion s = suggestionMapper.selectById(id);
        if (s == null) {
            throw new BusinessException("建议卡不存在");
        }
        return s;
    }
}
