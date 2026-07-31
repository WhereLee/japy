package com.japy.service;

import com.japy.entity.SensitiveWord;
import com.japy.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private final SensitiveWordMapper wordMapper;

    /** 检查文本是否包含敏感词，返回命中的词或 null */
    public String check(String text) {
        if (text == null || text.isBlank()) return null;
        List<SensitiveWord> words = wordMapper.selectList(null);
        String lower = text.toLowerCase();
        for (SensitiveWord w : words) {
            if (lower.contains(w.getWord().toLowerCase())) {
                return w.getWord();
            }
        }
        return null;
    }
}
