package com.recloud.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 业务指标计数器
 *
 * 用 Micrometer Counter 记录核心业务操作次数，
 * 可通过 /actuator/metrics 查看。
 *
 * 指标列表：
 * recloud.annotation.created   — 批注创建次数
 * recloud.annotation.deleted   — 批注删除次数
 * recloud.like.toggled         — 点赞操作次数
 * recloud.comment.created      — 评论创建次数
 * recloud.user.registered      — 用户注册次数
 * recloud.novel.imported       — 小说导入次数
 */
@Component
public class BusinessMetrics {

    private final Counter annotationCreated;
    private final Counter annotationDeleted;
    private final Counter likeToggled;
    private final Counter commentCreated;
    private final Counter userRegistered;
    private final Counter novelImported;

    public BusinessMetrics(MeterRegistry registry) {
        this.annotationCreated = Counter.builder("recloud.annotation.created")
                .description("批注创建次数")
                .register(registry);
        this.annotationDeleted = Counter.builder("recloud.annotation.deleted")
                .description("批注删除次数")
                .register(registry);
        this.likeToggled = Counter.builder("recloud.like.toggled")
                .description("点赞操作次数")
                .register(registry);
        this.commentCreated = Counter.builder("recloud.comment.created")
                .description("评论创建次数")
                .register(registry);
        this.userRegistered = Counter.builder("recloud.user.registered")
                .description("用户注册次数")
                .register(registry);
        this.novelImported = Counter.builder("recloud.novel.imported")
                .description("小说导入次数")
                .register(registry);
    }

    public void incrementAnnotationCreated() { annotationCreated.increment(); }
    public void incrementAnnotationDeleted() { annotationDeleted.increment(); }
    public void incrementLikeToggled() { likeToggled.increment(); }
    public void incrementCommentCreated() { commentCreated.increment(); }
    public void incrementUserRegistered() { userRegistered.increment(); }
    public void incrementNovelImported() { novelImported.increment(); }
}
