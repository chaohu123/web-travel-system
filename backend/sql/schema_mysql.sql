-- 建议先创建数据库
DROP DATABASE IF EXISTS `travel_match`;
CREATE DATABASE `travel_match` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `travel_match`;

------------------------------------------------------------
-- 用户与信誉相关表
------------------------------------------------------------

DROP TABLE IF EXISTS `t_user`;
CREATE TABLE `t_user` (
                          `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                          `email`        VARCHAR(64)     DEFAULT NULL        COMMENT '邮箱账号(唯一，可为空)',
                          `phone`        VARCHAR(20)     DEFAULT NULL        COMMENT '手机号(唯一，可为空)',
                          `password`     VARCHAR(255) NOT NULL               COMMENT '加密后的登录密码',
                          `auth_provider` VARCHAR(32)    DEFAULT 'local'     COMMENT '登录来源: local/wechat/weibo/google',
                          `status`       VARCHAR(16)    DEFAULT 'active'     COMMENT '账号状态: active/disabled 等',
                          `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_user_email` (`email`),
                          UNIQUE KEY `uk_user_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户账号表';

DROP TABLE IF EXISTS `t_user_profile`;
CREATE TABLE `t_user_profile` (
                                  `id`       BIGINT       NOT NULL COMMENT '用户ID(与t_user共用主键)',
                                  `nickname` VARCHAR(255)     DEFAULT NULL COMMENT '昵称',
                                  `avatar`   VARCHAR(512)     DEFAULT NULL COMMENT '头像URL',
                                  `gender`   VARCHAR(16)      DEFAULT NULL COMMENT '性别: male/female/other',
                                  `age`      INT              DEFAULT NULL COMMENT '年龄',
                                  `city`     VARCHAR(255)     DEFAULT NULL COMMENT '常驻城市',
                                  `intro`    VARCHAR(512)     DEFAULT NULL COMMENT '个人简介',
                                  `slogan`   VARCHAR(256)     DEFAULT NULL COMMENT '旅行宣言',
                                  PRIMARY KEY (`id`),
                                  CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基础资料表';

DROP TABLE IF EXISTS `t_user_preference`;
CREATE TABLE `t_user_preference` (
                                     `id`                    BIGINT       NOT NULL COMMENT '用户ID(与t_user共用主键)',
                                     `travel_style`          VARCHAR(255)     DEFAULT NULL COMMENT '旅行风格(背包客/度假等)',
                                     `budget_min`            INT              DEFAULT NULL COMMENT '常规预算下限(元)',
                                     `budget_max`            INT              DEFAULT NULL COMMENT '常规预算上限(元)',
                                     `traffic_preference`    VARCHAR(255)     DEFAULT NULL COMMENT '交通偏好(飞机/火车/自驾等)',
                                     `tags`                  VARCHAR(512)     DEFAULT NULL COMMENT '偏好标签(逗号分隔)',
                                     `interest_weights_json` VARCHAR(1024)    DEFAULT NULL COMMENT '兴趣权重JSON(自然/文化/美食等)',
                                     PRIMARY KEY (`id`),
                                     CONSTRAINT `fk_user_pref_user` FOREIGN KEY (`id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户旅行偏好设置表';

DROP TABLE IF EXISTS `t_user_reputation`;
CREATE TABLE `t_user_reputation` (
                                     `id`             BIGINT NOT NULL COMMENT '用户ID(与t_user共用主键)',
                                     `score`          INT        DEFAULT 0 COMMENT '信誉积分总分',
                                     `level`          INT        DEFAULT 1 COMMENT '信誉等级',
                                     `total_trips`    INT        DEFAULT 0 COMMENT '完成行程/结伴次数',
                                     `positive_count` INT        DEFAULT 0 COMMENT '好评次数',
                                     PRIMARY KEY (`id`),
                                     CONSTRAINT `fk_user_rep_user` FOREIGN KEY (`id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信誉积分信息表';

------------------------------------------------------------
-- 行程规划相关表
------------------------------------------------------------

DROP TABLE IF EXISTS `t_trip_plan`;
CREATE TABLE `t_trip_plan` (
                               `id`                      BIGINT      NOT NULL AUTO_INCREMENT COMMENT '行程ID',
                               `user_id`                 BIGINT          DEFAULT NULL COMMENT '创建人用户ID',
                               `title`                   VARCHAR(255)    DEFAULT NULL COMMENT '行程标题',
                               `destination`             VARCHAR(255)    DEFAULT NULL COMMENT '目的地城市/国家',
                               `start_date`              DATE            DEFAULT NULL COMMENT '出发日期',
                               `end_date`                DATE            DEFAULT NULL COMMENT '结束日期',
                               `budget`                  INT             DEFAULT NULL COMMENT '总预算(元)',
                               `people_count`            INT             DEFAULT 1 COMMENT '同行人数',
                               `pace`                    VARCHAR(32)     DEFAULT 'normal' COMMENT '行程节奏: rush/normal/relax',
                               `preference_weights_json` VARCHAR(1024)   DEFAULT NULL COMMENT '兴趣权重JSON(自然/文化/美食等)',
                               `created_at`              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updated_at`              DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               KEY `idx_trip_plan_user` (`user_id`),
                               CONSTRAINT `fk_trip_plan_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程计划主表';

DROP TABLE IF EXISTS `t_trip_day`;
CREATE TABLE `t_trip_day` (
                              `id`        BIGINT NOT NULL AUTO_INCREMENT COMMENT '行程天ID',
                              `plan_id`   BIGINT NOT NULL COMMENT '所属行程ID',
                              `day_index` INT        DEFAULT NULL COMMENT '第几天(从1开始)',
                              `date`      DATE       DEFAULT NULL COMMENT '对应日期',
                              PRIMARY KEY (`id`),
                              KEY `idx_trip_day_plan` (`plan_id`),
                              CONSTRAINT `fk_trip_day_plan` FOREIGN KEY (`plan_id`) REFERENCES `t_trip_plan` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程天表';

DROP TABLE IF EXISTS `t_trip_activity`;
CREATE TABLE `t_trip_activity` (
                                   `id`             BIGINT NOT NULL AUTO_INCREMENT COMMENT '活动ID',
                                   `day_id`         BIGINT NOT NULL COMMENT '所属行程天ID',
                                   `type`           VARCHAR(32)     DEFAULT NULL COMMENT '活动类型: sight/food/hotel/other',
                                   `name`           VARCHAR(255)    DEFAULT NULL COMMENT '活动名称(景点/餐厅等)',
                                   `location`       VARCHAR(255)    DEFAULT NULL COMMENT '地点描述',
                                   `start_time`     VARCHAR(32)     DEFAULT NULL COMMENT '预计开始时间(字符串)',
                                   `end_time`       VARCHAR(32)     DEFAULT NULL COMMENT '预计结束时间(字符串)',
                                   `transport`      VARCHAR(64)     DEFAULT NULL COMMENT '交通方式(步行/地铁/出租车等)',
                                   `estimated_cost` INT             DEFAULT NULL COMMENT '预估花费(元)',
                                   PRIMARY KEY (`id`),
                                   KEY `idx_activity_day` (`day_id`),
                                   CONSTRAINT `fk_trip_activity_day` FOREIGN KEY (`day_id`) REFERENCES `t_trip_day` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程每日活动表';

------------------------------------------------------------
-- 结伴与小队相关表
------------------------------------------------------------

DROP TABLE IF EXISTS `t_companion_post`;
CREATE TABLE `t_companion_post` (
                                    `id`                 BIGINT NOT NULL AUTO_INCREMENT COMMENT '结伴帖子ID',
                                    `creator_id`         BIGINT      DEFAULT NULL COMMENT '发起人用户ID',
                                    `related_plan_id`    BIGINT      DEFAULT NULL COMMENT '关联行程ID(可为空)',
                                    `destination`        VARCHAR(255) DEFAULT NULL COMMENT '目的地',
                                    `start_date`         DATE         DEFAULT NULL COMMENT '计划出发日期',
                                    `end_date`           DATE         DEFAULT NULL COMMENT '计划结束日期',
                                    `min_people`         INT          DEFAULT NULL COMMENT '期望最少人数',
                                    `max_people`         INT          DEFAULT NULL COMMENT '期望最多人数',
                                    `budget_min`         INT          DEFAULT NULL COMMENT '预算下限(元)',
                                    `budget_max`         INT          DEFAULT NULL COMMENT '预算上限(元)',
                                    `expected_mate_desc` VARCHAR(512) DEFAULT NULL COMMENT '对旅友的期待(性格/偏好等)',
                                    `visibility`         VARCHAR(16)  DEFAULT 'public' COMMENT '可见性: public/friends/private',
                                    `status`             VARCHAR(16)  DEFAULT 'open' COMMENT '状态: open/locked/closed',
                                    `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_companion_post_creator` (`creator_id`),
                                    KEY `idx_companion_post_dest_date` (`destination`,`start_date`,`end_date`),
                                    CONSTRAINT `fk_companion_post_creator` FOREIGN KEY (`creator_id`) REFERENCES `t_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结伴需求发布表';

DROP TABLE IF EXISTS `t_companion_team`;
CREATE TABLE `t_companion_team` (
                                    `id`           BIGINT NOT NULL AUTO_INCREMENT COMMENT '小队ID',
                                    `post_id`      BIGINT      DEFAULT NULL COMMENT '来源结伴帖子ID',
                                    `name`         VARCHAR(255) DEFAULT NULL COMMENT '小队名称',
                                    `final_plan_id` BIGINT      DEFAULT NULL COMMENT '最终确认行程ID(可为空)',
                                    `status`       VARCHAR(16)  DEFAULT 'forming' COMMENT '小队状态: forming/confirmed/finished',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_companion_team_post` (`post_id`),
                                    CONSTRAINT `fk_companion_team_post` FOREIGN KEY (`post_id`) REFERENCES `t_companion_post` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结伴小队表';

DROP TABLE IF EXISTS `t_team_member`;
CREATE TABLE `t_team_member` (
                                 `id`        BIGINT NOT NULL AUTO_INCREMENT COMMENT '小队成员关系ID',
                                 `team_id`   BIGINT NOT NULL COMMENT '小队ID',
                                 `user_id`   BIGINT NOT NULL COMMENT '成员用户ID',
                                 `role`      VARCHAR(16) DEFAULT 'member' COMMENT '角色: leader/member',
                                 `state`     VARCHAR(16) DEFAULT 'joined' COMMENT '状态: joined/pending/left',
                                 `joined_at` DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_team_member_team` (`team_id`),
                                 KEY `idx_team_member_user` (`user_id`),
                                 CONSTRAINT `fk_team_member_team` FOREIGN KEY (`team_id`) REFERENCES `t_companion_team` (`id`) ON DELETE CASCADE,
                                 CONSTRAINT `fk_team_member_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小队成员关系表';

------------------------------------------------------------
-- 社交内容：游记、动态、评论
------------------------------------------------------------

DROP TABLE IF EXISTS `t_travel_note`;
CREATE TABLE `t_travel_note` (
                                 `id`              BIGINT NOT NULL AUTO_INCREMENT COMMENT '游记ID',
                                 `author_id`       BIGINT      DEFAULT NULL COMMENT '作者用户ID',
                                 `title`           VARCHAR(255) NOT NULL COMMENT '游记标题',
                                 `content`         TEXT         NOT NULL COMMENT '游记正文内容',
                                 `cover_image`     VARCHAR(512)     DEFAULT NULL COMMENT '封面图片URL',
                                 `related_plan_id` BIGINT          DEFAULT NULL COMMENT '关联行程ID(可为空)',
                                 `destination`     VARCHAR(255)    DEFAULT NULL COMMENT '主要目的地',
                                 `created_at`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_travel_note_author` (`author_id`),
                                 KEY `idx_travel_note_dest` (`destination`),
                                 CONSTRAINT `fk_travel_note_author` FOREIGN KEY (`author_id`) REFERENCES `t_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游记/攻略表';

DROP TABLE IF EXISTS `t_feed_post`;
CREATE TABLE `t_feed_post` (
                               `id`              BIGINT NOT NULL AUTO_INCREMENT COMMENT '动态ID',
                               `user_id`         BIGINT      DEFAULT NULL COMMENT '发布者用户ID',
                               `content`         VARCHAR(512) NOT NULL COMMENT '动态文本内容',
                               `image_urls_json` VARCHAR(1024)    DEFAULT NULL COMMENT '图片URL数组(JSON字符串)',
                               `created_at`      DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               PRIMARY KEY (`id`),
                               KEY `idx_feed_post_user` (`user_id`),
                               CONSTRAINT `fk_feed_post_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区动态表';

DROP TABLE IF EXISTS `t_comment`;
CREATE TABLE `t_comment` (
                             `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论ID',
                             `user_id`     BIGINT      DEFAULT NULL COMMENT '评论用户ID',
                             `target_type` VARCHAR(32) NOT NULL COMMENT '评论目标类型: note/route/companion_team 等',
                             `target_id`   BIGINT      NOT NULL COMMENT '评论目标主键ID',
                             `content`     VARCHAR(512) NOT NULL COMMENT '评论内容',
                             `score`       INT              DEFAULT NULL COMMENT '可选评分(1-5)',
                             `created_at`  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             PRIMARY KEY (`id`),
                             KEY `idx_comment_target` (`target_type`,`target_id`),
                             KEY `idx_comment_user` (`user_id`),
                             CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用评论/评分表';
