package com.power.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * 双数据源：PostgreSQL（pgvector）走 Boot 自动配置，MySQL（power_device 台账）在这里单独开。
 *
 * <p>不在容器里注册第二个 DataSource bean：pgvector 自动配置带 @ConditionalOnSingleCandidate，
 * 多一个 bean 就整体退让。所以在方法内 new，代价是没有连接池——台账查询低频，够用。</p>
 */
@Configuration
public class DeviceDbConfig {

    /** pgvector 主库。有两个 JdbcTemplate，必须标 @Primary，否则按类型注入会 NoUniqueBean。 */
    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /** 台账 MySQL 连接，注入时用 @Qualifier("deviceJdbcTemplate")。 */
    @Bean
    public JdbcTemplate deviceJdbcTemplate(
            @Value("${power.rag.device-db.url}") String url,
            @Value("${power.rag.device-db.username}") String username,
            @Value("${power.rag.device-db.password}") String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource(url, username, password);
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        return new JdbcTemplate(ds);
    }
}
