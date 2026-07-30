package com.recloud.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 慢 SQL 拦截器注册配置
 *
 * 在所有 SqlSessionFactory 初始化完成后，注册 SlowSqlInterceptor。
 * 使用 @PostConstruct 避免循环依赖。
 */
@Slf4j
@Configuration
public class SlowSqlInterceptorConfig {

    @Autowired
    private List<SqlSessionFactory> sqlSessionFactoryList;

    @Autowired
    private SlowSqlInterceptor slowSqlInterceptor;

    @PostConstruct
    public void registerInterceptor() {
        for (SqlSessionFactory factory : sqlSessionFactoryList) {
            factory.getConfiguration().addInterceptor(slowSqlInterceptor);
        }
        log.info("[ReCloud] 慢 SQL 拦截器已注册到 {} 个 SqlSessionFactory", sqlSessionFactoryList.size());
    }
}
