package com.recloud.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置
 * <p>
 * 自定义 RedisTemplate 序列化策略：
 * - Key 使用 StringRedisSerializer（可读性好）
 * - Value 使用 Jackson2JsonRedisSerializer（安全序列化，不写入 @class 类型信息）
 * <p>
 * 安全说明：
 * 不使用 GenericJackson2JsonRedisSerializer，因为它会在 JSON 中写入 @class 类型信息，
 * 攻击者可构造恶意类名（如 TemplatesImpl）触发反序列化远程代码执行。
 * 改用 Jackson2JsonRedisSerializer 指定具体类型，或使用 StringRedisSerializer + 手动 JSON。
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key 序列化
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 序列化（安全的 Jackson 序列化，不写入 @class）
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 不启用 DefaultTyping，避免 @class 反序列化漏洞
        // 反序列化时需要指定目标类型，各处手动调用 objectMapper.readValue(json, TypeRef)

        // 使用 StringRedisSerializer 存储 JSON 字符串（最安全方案）
        // 各处代码使用 objectMapper 手动序列化/反序列化
        template.setValueSerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
