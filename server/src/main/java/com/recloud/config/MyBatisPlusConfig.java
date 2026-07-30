package com.recloud.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置
 * <p>
 * 插件顺序很重要：乐观锁 → 防全表更新 → 分页
 * <p>
 * - 分页插件：自动识别数据库类型，生成分页 SQL
 * - 乐观锁插件：@Version 注解字段自动 CAS 更新
 * - 防全表更新插件：防止误写 update().set(...).update() 全表更新
 */
@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁插件（必须在分页插件之前）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 防全表更新插件
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
