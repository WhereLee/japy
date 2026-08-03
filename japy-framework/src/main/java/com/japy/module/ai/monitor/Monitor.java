package com.japy.module.ai.monitor;

import java.util.List;

/**
 * 检测器接口：规则层（L0）确定性检测，不调用 LLM。
 * 新增检测器 = 新增一个实现类，框架零改动（插件化）。
 */
public interface Monitor {

    /** 唯一 code（如 login_brute_force） */
    String code();

    /** 中文名（如 登录爆破检测） */
    String name();

    /** 执行一次检测，返回命中事件（未命中返回空列表） */
    List<MonitorEvent> check();
}
