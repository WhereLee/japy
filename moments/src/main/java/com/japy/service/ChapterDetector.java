package com.japy.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 章节检测：模式匹配 → 验证过滤 → 降级兜底。
 * 从 pyser/src/chapter.py 移植（7 组正则 + 目录页检测/密度异常/最小间距 + 固定字数降级）。
 *
 * 设计原则（与 Python 端一致）：
 * - 宽进严出：第一层尽量多收集候选，后续验证过滤
 * - 容错降级：检测失败不阻断流程，整本按固定字数分章
 * - 可扩展：ALL_PATTERNS 列表可随时追加新模式
 */
public class ChapterDetector {

    // ============ 模式库 ============

    /** 第一组：标准"第X章/节/回/卷..." */
    private static final Pattern P_STANDARD = Pattern.compile(
            "^[\\s\\u3000]*"
            + "(?:正文[\\s\\u3000·]*)?"
            + "第[零〇一二三四五六七八九十百千万\\d]+"
            + "[章节幕回卷集部篇折出]"
            + "[\\s\\u3000:：.．、\\-—·]*"
            + ".{0,40}$");

    /** 第二组：特殊标记词 */
    private static final Pattern P_SPECIAL = Pattern.compile(
            "^[\\s\\u3000]*"
            + "(?:序幕|序章|序曲|序言|楔子|引子|尾声|后记|终章|终曲|番外|结语|补记|跋)"
            + "[\\s\\u3000:：.．、\\-—·]*"
            + ".{0,40}$");

    /** 第三组：卷/部/篇级别 */
    private static final Pattern P_JUAN_BU = Pattern.compile(
            "^[\\s\\u3000]*"
            + "(?:"
            + "第[零〇一二三四五六七八九十百千万\\d]+[卷部篇]"
            + "|[零〇一二三四五六七八九十]+卷"
            + "|[上中下][卷部篇]"
            + ")"
            + "[\\s\\u3000:：.．、\\-—·]*"
            + ".{0,40}$");

    /** 第四组：纯数字编号（"1. 标题" "01、标题"），分隔符后不能紧跟数字（排除 18:50 时间戳） */
    private static final Pattern P_NUMERIC = Pattern.compile(
            "^[\\s\\u3000]*"
            + "\\d{1,4}"
            + "[\\s.、．:：\\-—]+"
            + "[^\\d\\s].{0,39}$");

    /** 第五组：无"第"字的中文数字+量词（"一章 xxx"） */
    private static final Pattern P_NO_DI = Pattern.compile(
            "^[\\s\\u3000]*"
            + "[零〇一二三四五六七八九十百千]+[章节回]"
            + "[\\s\\u3000:：.．、\\-—·]+"
            + ".{1,40}$");

    /** 第六组：英文/混合格式 */
    private static final Pattern P_ENGLISH = Pattern.compile(
            "^(?:Chapter|CHAPTER|Part|PART|Section|Prologue|Epilogue|Interlude)"
            + "[\\s\\d:：.]*"
            + ".{0,40}$",
            Pattern.CASE_INSENSITIVE);

    /** 第七组：括号格式 【第一章】「第一章」[第一章] */
    private static final Pattern P_BRACKET = Pattern.compile(
            "^[\\s\\u3000]*"
            + "[【「\\[]"
            + "(?:第[零〇一二三四五六七八九十百千万\\d]+[章节幕回卷集部篇折出]"
            + "|(?:序幕|楔子|尾声|后记|终章|番外))"
            + ".{0,30}"
            + "[】」\\]]"
            + ".{0,20}$");

    private record PatternEntry(String name, Pattern pattern) {}

    private static final List<PatternEntry> ALL_PATTERNS = List.of(
            new PatternEntry("standard", P_STANDARD),
            new PatternEntry("special", P_SPECIAL),
            new PatternEntry("juan_bu", P_JUAN_BU),
            new PatternEntry("numeric", P_NUMERIC),
            new PatternEntry("no_di", P_NO_DI),
            new PatternEntry("english", P_ENGLISH),
            new PatternEntry("bracket", P_BRACKET));

    /** 可靠模式（密度异常时保留） */
    private static final List<String> RELIABLE = List.of("standard", "special", "bracket", "juan_bu");

    // ============ 章节结构 ============

    public static class Chapter {
        public final int index;
        public final String title;
        public final String content;
        public final int chars;

        public Chapter(int index, String title, String content) {
            this.index = index;
            this.title = title;
            this.content = content;
            this.chars = content.length();
        }
    }

    private record Candidate(int lineNo, String title, String patternName) {}

    // ============ 判定 ============

    /** 判断一行是否为章节标题，返回命中的模式名或 null */
    private static String isChapterTitle(String line) {
        String stripped = line.strip();
        if (stripped.isEmpty() || stripped.length() > 50) return null;
        for (PatternEntry entry : ALL_PATTERNS) {
            if (entry.pattern().matcher(stripped).matches()) {
                return entry.name();
            }
        }
        return null;
    }

    // ============ 主入口 ============

    /**
     * 将全文切分为章节列表。
     */
    public static List<Chapter> split(String text) {
        if (text == null || text.isBlank()) return List.of();
        // 统一换行符
        text = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = text.split("\n", -1);

        // 1. 收集候选
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String name = isChapterTitle(lines[i]);
            if (name != null) {
                candidates.add(new Candidate(i, lines[i].strip(), name));
            }
        }

        // 2. 验证过滤
        candidates = validateCandidates(candidates, lines);

        // 3. 无候选 → 降级
        if (candidates.isEmpty()) {
            return fallbackSplit(text);
        }

        // 4. 按候选切分内容
        List<Chapter> chapters = new ArrayList<>();
        for (int idx = 0; idx < candidates.size(); idx++) {
            int start = candidates.get(idx).lineNo() + 1;
            int end = idx + 1 < candidates.size() ? candidates.get(idx + 1).lineNo() : lines.length;
            String content = String.join("\n", java.util.Arrays.copyOfRange(lines, start, end)).strip();
            if (!content.isEmpty()) {
                chapters.add(new Chapter(chapters.size(), candidates.get(idx).title(), content));
            }
        }

        // 5. 第一个标题之前的内容（书名/简介）超过 200 字 → 作为"前言"
        if (candidates.get(0).lineNo() > 0) {
            String preContent = String.join("\n", java.util.Arrays.copyOfRange(lines, 0, candidates.get(0).lineNo())).strip();
            if (!preContent.isEmpty() && preContent.length() > 200) {
                chapters.add(0, new Chapter(0, "前言", preContent));
                for (int i = 0; i < chapters.size(); i++) {
                    chapters.set(i, new Chapter(i, chapters.get(i).title, chapters.get(i).content));
                }
            }
        }

        return chapters.isEmpty() ? fallbackSplit(text) : chapters;
    }

    // ============ 验证层 ============

    /**
     * 二次验证：
     * 1. 目录页检测：前20行内超过5个命中 → 跳过这些（是目录不是正文）
     * 2. 最小间距：两个章节之间至少 200 字正文
     * 3. 密度异常：章节数 > 总行数/5 → 只保留可靠模式
     */
    private static List<Candidate> validateCandidates(List<Candidate> candidates, String[] lines) {
        if (candidates.isEmpty()) return candidates;
        int totalLines = lines.length;

        // 目录页检测
        long earlyHits = candidates.stream().filter(c -> c.lineNo() < 20).count();
        if (earlyHits >= 5) {
            candidates = candidates.stream().filter(c -> c.lineNo() >= 20).toList();
            if (candidates.isEmpty()) return candidates;
        }

        // 密度异常检测
        if (candidates.size() > totalLines / 5.0) {
            List<Candidate> reliable = candidates.stream()
                    .filter(c -> RELIABLE.contains(c.patternName()))
                    .sorted((a, b) -> Integer.compare(a.lineNo(), b.lineNo()))
                    .toList();
            if (reliable.size() >= 3) {
                candidates = reliable;
            }
        }

        // 最小间距过滤：两章之间至少 200 字
        List<Candidate> filtered = new ArrayList<>();
        filtered.add(candidates.get(0));
        for (int i = 1; i < candidates.size(); i++) {
            Candidate prev = filtered.get(filtered.size() - 1);
            Candidate curr = candidates.get(i);
            String between = String.join("\n", java.util.Arrays.copyOfRange(lines, prev.lineNo() + 1, curr.lineNo())).strip();
            if (between.length() >= 200) {
                filtered.add(curr);
            }
        }
        return filtered;
    }

    // ============ 降级策略 ============

    /** 降级：按固定字数切分（在段落边界断开） */
    private static List<Chapter> fallbackSplit(String text, int charsPerChapter) {
        List<Chapter> chapters = new ArrayList<>();
        String[] paragraphs = text.split("\n", -1);
        StringBuilder current = new StringBuilder();

        for (String para : paragraphs) {
            current.append(para).append('\n');
            if (current.length() >= charsPerChapter) {
                chapters.add(new Chapter(chapters.size(), "第" + (chapters.size() + 1) + "部分", current.toString().strip()));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            chapters.add(new Chapter(chapters.size(), "第" + (chapters.size() + 1) + "部分", current.toString().strip()));
        }
        return chapters;
    }

    private static List<Chapter> fallbackSplit(String text) {
        return fallbackSplit(text, 20000);
    }
}
