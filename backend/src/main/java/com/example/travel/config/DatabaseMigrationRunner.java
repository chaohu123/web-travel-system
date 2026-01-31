package com.example.travel.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时执行数据库迁移，确保 t_private_message.content 为 MEDIUMTEXT，
 * 以支持 Base64 图片及长文本（解决 "Data too long for column 'content'" 错误）。
 */
@Component
@Order(1)
public class DatabaseMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final JdbcTemplate jdbc_template;

    public DatabaseMigrationRunner(JdbcTemplate jdbc_template) {
        this.jdbc_template = jdbc_template;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String sql = "ALTER TABLE t_private_message MODIFY COLUMN content MEDIUMTEXT NOT NULL COMMENT '消息内容'";
            jdbc_template.execute(sql);
            log.info("Migration: t_private_message.content -> MEDIUMTEXT OK");
        } catch (Exception e) {
            log.warn("Migration t_private_message.content skipped or failed: {}", e.getMessage());
        }
    }
}
