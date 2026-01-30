-- 小队分享表：队长分享小队给指定用户，被分享人可查看该行程
USE `travel_match`;

DROP TABLE IF EXISTS `t_team_share`;
CREATE TABLE `t_team_share` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '分享记录ID',
    `team_id`    BIGINT   NOT NULL COMMENT '小队ID',
    `to_user_id` BIGINT   NOT NULL COMMENT '被分享用户ID',
    `shared_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分享时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_team_share` (`team_id`,`to_user_id`),
    KEY `idx_team_share_to_user` (`to_user_id`),
    CONSTRAINT `fk_team_share_team` FOREIGN KEY (`team_id`) REFERENCES `t_companion_team` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_team_share_user` FOREIGN KEY (`to_user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小队分享记录(被分享人可查看该行程)';
