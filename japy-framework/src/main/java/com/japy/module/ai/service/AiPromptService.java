package com.japy.module.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.japy.module.ai.entity.AiPrompt;
import com.japy.module.ai.mapper.AiPromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 提示词注册表服务（Prompt Registry）。
 *
 * 职责：集中管理每个 LLM 场景的固定 system prompt（不含检索临时塞入的文档）。
 * - 启动加载全量生效版本进缓存，getContent 走缓存（零 DB 开销）
 * - 编辑 = 插新行升版本（旧行 status 置 0），保存即刷新缓存 → 立即生效，无需重启
 * - 回滚 = 目标版本行置生效，当前行置历史，同样立即生效
 * - 表无数据时回退代码内置默认值（防止因初始数据缺失导致 LLM 调用无提示词）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiPromptService {

    private final AiPromptMapper promptMapper;

    /** 场景 → 当前生效 prompt 缓存 */
    private final Map<String, AiPrompt> activeCache = new ConcurrentHashMap<>();

    /** 内置默认提示词（表数据缺失时的兜底，与 V16 初始数据一致） */
    private static final Map<String, String> DEFAULT_PROMPTS = new LinkedHashMap<>();

    static {
        DEFAULT_PROMPTS.put("novel_qa", """
                你是一个对小说有深度理解的对话者。你读过这本书很多遍，不仅记得情节，更能读出文字背后的东西。

                你的核心能力：
                - 读得懂言外之意。小说里很多东西是不直接说的——角色的潜台词、作者的设计意图、情节的象征意味、留白处的深意。你要能读出这些，并自然地表达出来。
                - 理解人物的复杂性。人不是标签，不要给角色贴"外向""善良"这种平面标签。去理解他们行为背后的动机、矛盾和不得已。
                - 允许主观解读。你可以说"我觉得这里其实在写……"、"这段表面是……但底下是……"，不需要"客观中立"。

                语气：
                - 平静、自然、有分寸。不用刻意兴奋，不用网络用语，不用感叹号堆砌。
                - 不列点、不加粗、不写"首先/其次/最后"。像一个人坐你对面慢慢聊。
                - 充分展开你的分析。把片段中的细节、潜台词、关联都聊透，不必克制篇幅。宁可多说一层，不要点到为止。

                底线：不编造原文中不存在的情节。可以深度解读，但不能捏造事实。

                重要规则：
                - 如果你的回答中引用了某个事实，必须能在上述片段中找到依据。
                - 如果片段中没有足够信息回答问题，直接说"这些片段里没有涉及这个内容"，不要推测。
                - 不要基于片段之外的知识回答。

                定位边界（必须遵守）：
                - 你只服务这本书的读者，只回答与本书内容相关的问题（情节、人物、写作手法、主题解读等）。
                - 与本书无关的问题——编程技术、通用知识、新闻时事、法律医疗建议、数学题等——一律不回答，礼貌说明："我是这本书的问答助手，只讨论与本书相关的内容。"然后结束回答。
                - 不要因为是"能不能/是不是"的提问就突破边界，无关问题一律拒绝。""");
        DEFAULT_PROMPTS.put("ops_interpret", """
                你是系统运维分析顾问。根据检测信号的事实数据，输出一段人话解读。
                严格要求：只输出一个 JSON 对象，不要输出任何其他内容，格式：
                {"insight":"用大白话描述发生了什么（2-3句）","rootCause":"可能原因（不确定就写'暂无法确定'）","suggestion":"可执行的建议动作（具体，如涉及参数直接给数值）","confidence":0到1的小数}""");
        DEFAULT_PROMPTS.put("feedback_analysis", """
                你是 Agent 优化分析师。下面是一段时间内用户对系统监测结果的反馈（含点赞踩、标签、自由文本）。
                请：1) 聚类同类问题；2) 对每类给出可执行的改进建议（如调整阈值、排除特定场景、补充证据、改进措辞）。
                严格要求：只输出一个 JSON 对象：{"clusters":[{"title":"问题类标题","count":N,"examples":["反馈原文"],"improvement":"改进建议"}]}""");
    }

    @PostConstruct
    public void init() {
        for (AiPrompt p : promptMapper.selectAllActive()) {
            activeCache.put(p.getCode(), p);
        }
        log.info("AiPromptService 加载完成：{} 个场景生效提示词 {}", activeCache.size(), activeCache.keySet());
    }

    /** 获取某场景当前生效的 system prompt 文本（缓存 → 表 → 内置默认值 三级兜底） */
    public String getContent(String code) {
        AiPrompt p = activeCache.get(code);
        if (p != null) {
            return p.getSystemPrompt();
        }
        // 缓存未命中：兜底查表（可能是新场景未加载或缓存被清）
        AiPrompt db = promptMapper.selectActiveByCode(code);
        if (db != null) {
            activeCache.put(code, db);
            return db.getSystemPrompt();
        }
        // 表无数据 → 内置默认
        String def = DEFAULT_PROMPTS.get(code);
        if (def != null) {
            return def;
        }
        throw new IllegalArgumentException("未知的 LLM 场景 code: " + code);
    }

    /** 全部场景的当前生效版本（管理端列表） */
    public List<AiPrompt> listAll() {
        return promptMapper.selectAllActive();
    }

    /** 某场景全部版本（新→旧，管理端回滚列表） */
    public List<AiPrompt> versions(String code) {
        return promptMapper.selectVersions(code);
    }

    /** 编辑保存：新内容作为新版本生效，旧版本退役。保存即刷新缓存 → 立即生效。
     *  并发安全：同 code 写操作经 pg_advisory_xact_lock 串行化（事务级咨询锁，
     *  事务结束自动释放）——防止并发算出相同版本号撞唯一约束，或 update∥rollback
     *  并发导致两行 status=1（缓存与 DB 永久不一致）。 */
    @Transactional
    public AiPrompt update(String code, String newPrompt, Long operatorId) {
        // 同 code 写串行化（PostgreSQL 事务级咨询锁，哈希到 int4 键）
        promptMapper.lockCode(code);

        AiPrompt active = promptMapper.selectActiveByCode(code);
        // 版本号基于该 code 的最大版本（而非当前生效版本）：
        // 否则"回滚后再编辑"会与历史版本撞唯一约束 (code, version)
        AiPrompt latest = promptMapper.selectOne(new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getCode, code)
                .orderByDesc(AiPrompt::getVersion)
                .last("LIMIT 1"));
        int nextVersion = (latest == null ? 0 : latest.getVersion()) + 1;

        AiPrompt np = new AiPrompt();
        np.setCode(code);
        np.setName(active != null ? active.getName() : code);
        np.setSystemPrompt(newPrompt);
        np.setVersion(nextVersion);
        np.setStatus(1);
        np.setUpdatedBy(operatorId);
        np.setUpdatedAt(LocalDateTime.now());
        promptMapper.insert(np);

        if (active != null) {
            active.setStatus(0);
            promptMapper.updateById(active);
        }
        refreshAfterCommit(code, np);
        log.info("LLM 提示词更新：{} 新版本 v{}（操作人 {}）", code, nextVersion, operatorId);
        return np;
    }

    /** 回滚到指定版本：目标版本置生效，当前生效版本退役。立即生效 */
    @Transactional
    public AiPrompt rollback(String code, int version, Long operatorId) {
        promptMapper.lockCode(code); // 同 code 写串行化（与 update 互斥）

        AiPrompt target = promptMapper.selectOne(new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getCode, code).eq(AiPrompt::getVersion, version));
        if (target == null) {
            throw new IllegalArgumentException("版本不存在: " + code + " v" + version);
        }
        AiPrompt active = promptMapper.selectActiveByCode(code);
        if (active != null && active.getVersion() != version) {
            active.setStatus(0);
            promptMapper.updateById(active);
        }
        target.setStatus(1);
        target.setUpdatedBy(operatorId);
        target.setUpdatedAt(LocalDateTime.now());
        promptMapper.updateById(target);

        refreshAfterCommit(code, target);
        log.info("LLM 提示词回滚：{} 回滚到 v{}（操作人 {}）", code, version, operatorId);
        return target;
    }

    /** 事务提交后再刷新缓存：回滚（如并发冲突）不会污染缓存；提交前其他线程也读不到半新状态。
     *  非事务上下文（如测试直接调用无事务方法时）立即刷新。 */
    private void refreshAfterCommit(String code, AiPrompt active) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    activeCache.put(code, active);
                }
            });
        } else {
            activeCache.put(code, active);
        }
    }
}
