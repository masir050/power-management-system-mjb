package com.power.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/**
 * JSON 序列化工具
 *
 * <p>缓存、消息队列里存的是字符串，存取需要与 HTTP 接口保持同一套序列化规则
 * （日期格式、Java 8 时间类型）。这里统一持有一个 ObjectMapper，
 * 避免各处 new 出配置不一致的实例导致"接口返回 2026-01-01 00:00:00，
 * 缓存里却是 [2026,1,1,0,0]"。注意本类只用于内部缓存/消息，
 * HTTP 层的序列化仍由 Spring 的 ObjectMapper 负责并经 application.yml 配置。</p>
 */
public final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            // 注册 Java 8 时间模块，否则 LocalDateTime 无法序列化
            .registerModule(new JavaTimeModule())
            // 时间序列化为 ISO-8601 字符串而不是时间戳数组
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // 反序列化时忽略未知字段，避免实体加字段后旧缓存全部解析失败
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private JsonSupport() {
    }

    /** 对象转 JSON 字符串 */
    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 序列化失败: " + value.getClass().getName(), e);
        }
    }

    /** JSON 字符串转 Map（保留数字/字符串原始类型） */
    public static Map<String, Object> fromJson(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败", e);
        }
    }

    /** JSON 字符串转指定类型 */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 反序列化失败: " + clazz.getName(), e);
        }
    }

    /** 暴露底层 Mapper，供需要复杂泛型的场景使用 */
    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
