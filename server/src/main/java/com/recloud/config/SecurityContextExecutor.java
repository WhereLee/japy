package com.recloud.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 安全上下文感知的线程池包装器
 * <p>
 * 解决问题：
 * Spring Security 的 SecurityContext 默认绑定到当前线程（ThreadLocal）。
 * 当任务提交到异步线程池时，子线程无法获取 SecurityContext，
 * 导致 SecurityUtils.getCurrentUserId() 返回 null。
 * <p>
 * 解决方案：
 * 在提交任务时捕获父线程的 SecurityContext，在子线程执行前恢复。
 * <p>
 * 使用方式：
 * 1. 注入此 Bean 替代原始 Executor
 * 2. 或包装任意 Executor：new SecurityContextExecutor(myExecutor)
 * <p>
 * 对比 TransmittableThreadLocal（TTL）：
 * - TTL 需要引入阿里 TTL 库，侵入性较强
 * - 本方案仅传递 SecurityContext，轻量且无外部依赖
 * - 面试中可对比讨论 ThreadLocal 的线程池传递问题
 */
@Slf4j
@Component
public class SecurityContextExecutor implements Executor {

    private final Executor delegate;

    public SecurityContextExecutor() {
        // 默认包装 logExecutor（操作日志专用线程池）
        this.delegate = null; // 运行时通过 wrap() 方法指定
    }

    /**
     * 包装任意 Executor，使其自动传递 SecurityContext
     */
    public SecurityContextExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable task) {
        // 在提交线程（父线程）捕获 SecurityContext
        SecurityContext context = SecurityContextHolder.getContext();

        Runnable wrappedTask = () -> {
            // 在执行线程（子线程）恢复 SecurityContext
            SecurityContext previousContext = SecurityContextHolder.getContext();
            try {
                if (context != null) {
                    SecurityContextHolder.setContext(context);
                }
                task.run();
            } finally {
                // 恢复子线程原始上下文（防止线程复用时上下文泄漏）
                if (previousContext != null) {
                    SecurityContextHolder.setContext(previousContext);
                } else {
                    SecurityContextHolder.clearContext();
                }
            }
        };

        if (delegate != null) {
            delegate.execute(wrappedTask);
        } else {
            wrappedTask.run();
        }
    }

    /**
     * 包装 Runnable，使其携带 SecurityContext（用于 CompletableFuture 场景）
     * <p>
     * 使用示例：
     * CompletableFuture.supplyAsync(() -> {
     *     // 这里可以正常使用 SecurityUtils.getCurrentUserId()
     *     return someService.doSomething();
     * }, SecurityContextExecutor.wrap(dashboardExecutor));
     */
    public static Runnable wrap(Runnable task) {
        SecurityContext context = SecurityContextHolder.getContext();
        return () -> {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            try {
                if (context != null) {
                    SecurityContextHolder.setContext(context);
                }
                task.run();
            } finally {
                if (previousContext != null) {
                    SecurityContextHolder.setContext(previousContext);
                } else {
                    SecurityContextHolder.clearContext();
                }
            }
        };
    }

    /**
     * 包装 Supplier（用于 CompletableFuture.supplyAsync）
     */
    public static <T> java.util.function.Supplier<T> wrapSupplier(java.util.function.Supplier<T> supplier) {
        SecurityContext context = SecurityContextHolder.getContext();
        return () -> {
            SecurityContext previousContext = SecurityContextHolder.getContext();
            try {
                if (context != null) {
                    SecurityContextHolder.setContext(context);
                }
                return supplier.get();
            } finally {
                if (previousContext != null) {
                    SecurityContextHolder.setContext(previousContext);
                } else {
                    SecurityContextHolder.clearContext();
                }
            }
        };
    }
}
