package com.japy.audit;

import com.japy.module.audit.service.AhoCorasick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** AC 多模式匹配引擎单测 */
class AhoCorasickTest {

    private final AhoCorasick ac = new AhoCorasick();

    @BeforeEach
    void setUp() {
        ac.build(
                List.of("赌博", "博彩", "约炮", "裸聊", "代开发票", "加微信领红包"),
                Map.of("赌博", "政治", "博彩", "政治", "约炮", "色情",
                        "裸聊", "色情", "代开发票", "广告", "加微信领红包", "广告"));
    }

    @Test
    void 命中多个敏感词() {
        var hits = ac.scan("这本小说提到了赌博和博彩，还有约炮内容");
        Map<String, Integer> map = new java.util.HashMap<>();
        hits.forEach(h -> map.put(h.word(), h.count()));
        assertEquals(3, map.size(), "应命中 3 个词");
        assertEquals(1, map.get("赌博"));
        assertEquals(1, map.get("博彩"));
        assertEquals(1, map.get("约炮"));
    }

    @Test
    void 重复命中计数() {
        var hits = ac.scan("赌博赌博赌博再赌博");
        assertEquals(1, hits.size());
        assertEquals(4, hits.get(0).count(), "赌博出现 4 次");
    }

    @Test
    void 无命中返回空() {
        var hits = ac.scan("这是一个完全正常的内容没有任何敏感信息");
        assertTrue(hits.isEmpty());
    }

    @Test
    void 重叠模式匹配() {
        // "微信" 与 "加微信领红包" 同时存在于文本
        ac.build(List.of("微信", "加微信领红包"), Map.of("微信", "广告", "加微信领红包", "广告"));
        var hits = ac.scan("请加微信领红包");
        assertEquals(2, hits.size(), "长词与短词都应命中（重叠）");
    }

    @Test
    void 空文本安全() {
        assertTrue(ac.scan(null).isEmpty());
        assertTrue(ac.scan("").isEmpty());
    }
}
