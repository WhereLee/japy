package com.japy.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 自然段切分：从 pyser/src/splitter.py 的 split_paragraphs 移植。
 *
 * 段落定义：
 * - \t 开头的行 或 全角双空格（\u3000\u3000）开头的行 = 新段落
 * - 空行也作为段落分隔
 * - 其余行拼接进当前段落
 */
public class ParagraphSplitter {

    private ParagraphSplitter() {}

    /**
     * 将章节文本按自然段切分，返回段落列表（去空）。
     */
    public static List<String> split(String text) {
        List<String> paragraphs = new ArrayList<>();
        if (text == null || text.isBlank()) return paragraphs;

        String[] lines = text.split("\n", -1);
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.isEmpty()) {
                if (!current.isEmpty()) {
                    paragraphs.add(current.toString());
                    current.setLength(0);
                }
            } else if (line.startsWith("\t") || line.startsWith("\u3000\u3000")) {
                if (!current.isEmpty()) {
                    paragraphs.add(current.toString());
                    current.setLength(0);
                }
                current.append(stripped);
            } else {
                current.append(stripped);
            }
        }
        if (!current.isEmpty()) {
            paragraphs.add(current.toString());
        }

        List<String> result = new ArrayList<>();
        for (String p : paragraphs) {
            if (!p.isBlank()) result.add(p);
        }
        return result;
    }
}
