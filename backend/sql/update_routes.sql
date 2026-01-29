use travel_match;
-- 京都东山区（清水寺 / 二年坂三年坂 / 祇园花见小路）
UPDATE t_trip_activity SET lng = 135.7809, lat = 34.9949 WHERE id = 1;
UPDATE t_trip_activity SET lng = 135.7809, lat = 34.9949 WHERE id = 2;
UPDATE t_trip_activity SET lng = 135.7809, lat = 34.9949 WHERE id = 3;

-- 京都站附近酒店（下京区）
UPDATE t_trip_activity SET lng = 135.7588, lat = 34.9859 WHERE id = 4;

-- 伏见稻荷大社（伏见区）
UPDATE t_trip_activity SET lng = 135.7727, lat = 34.9671 WHERE id = 5;

-- 金阁寺（北区）
UPDATE t_trip_activity SET lng = 135.7289, lat = 35.0394 WHERE id = 6;

-- 锦市场（中京区）
UPDATE t_trip_activity SET lng = 135.7670, lat = 35.0108 WHERE id = 7;

-- 奈良公园 / 东大寺 / 春日大社（奈良市）
UPDATE t_trip_activity SET lng = 135.8329, lat = 34.6851 WHERE id = 8;
UPDATE t_trip_activity SET lng = 135.8329, lat = 34.6851 WHERE id = 9;
UPDATE t_trip_activity SET lng = 135.8329, lat = 34.6851 WHERE id = 10;

-- 岚山竹林 / 天龙寺 / 豆腐料理（右京区）
UPDATE t_trip_activity SET lng = 135.6780, lat = 35.0094 WHERE id = 11;
UPDATE t_trip_activity SET lng = 135.6780, lat = 35.0094 WHERE id = 12;
UPDATE t_trip_activity SET lng = 135.6780, lat = 35.0094 WHERE id = 13;

-- 银阁寺 / 哲学之道（左京区）
UPDATE t_trip_activity SET lng = 135.7950, lat = 35.0270 WHERE id = 14;
UPDATE t_trip_activity SET lng = 135.7950, lat = 35.0270 WHERE id = 15;

-- 大理古城 / 古城美食街 / 洱海边民宿 / 洱海环湖 / 苍山索道 / 喜洲古镇（大理市）
UPDATE t_trip_activity SET lng = 100.2255, lat = 25.5976 WHERE id = 16;
UPDATE t_trip_activity SET lng = 100.2255, lat = 25.5976 WHERE id = 17;
UPDATE t_trip_activity SET lng = 100.2255, lat = 25.5976 WHERE id = 18;
UPDATE t_trip_activity SET lng = 100.2255, lat = 25.5976 WHERE id = 19;
UPDATE t_trip_activity SET lng = 100.2255, lat = 25.5976 WHERE id = 21;
UPDATE t_trip_activity SET lng = 100.2255, lat = 25.5976 WHERE id = 22;

-- 双廊古镇（洱源县）
UPDATE t_trip_activity SET lng = 100.1794, lat = 26.1108 WHERE id = 20;

-- 丽江古城 / 古城内客栈 / 束河古镇 / 纳西族特色菜（丽江古城区）
UPDATE t_trip_activity SET lng = 100.2340, lat = 26.8724 WHERE id = 23;
UPDATE t_trip_activity SET lng = 100.2340, lat = 26.8724 WHERE id = 24;
UPDATE t_trip_activity SET lng = 100.2340, lat = 26.8724 WHERE id = 27;
UPDATE t_trip_activity SET lng = 100.2340, lat = 26.8724 WHERE id = 28;

-- 玉龙雪山 / 蓝月谷（玉龙纳西族自治县）
UPDATE t_trip_activity SET lng = 100.2590, lat = 27.1030 WHERE id = 25;
UPDATE t_trip_activity SET lng = 100.2590, lat = 27.1030 WHERE id = 26;


-- 鼓浪屿 / 日光岩 / 龙头路小吃街 / 菽庄花园（鼓浪屿）
UPDATE t_trip_activity SET lng = 118.0719, lat = 24.4503 WHERE id = 29;
UPDATE t_trip_activity SET lng = 118.0719, lat = 24.4503 WHERE id = 30;
UPDATE t_trip_activity SET lng = 118.0719, lat = 24.4503 WHERE id = 31;
UPDATE t_trip_activity SET lng = 118.0719, lat = 24.4503 WHERE id = 32;

-- 厦门大学 / 南普陀寺 / 中山路附近酒店 / 沙坡尾美食 / 环岛路 / 八市海鲜市场 / 曾厝垵 / 中山路步行街（思明区）
UPDATE t_trip_activity SET lng = 118.0894, lat = 24.4798 WHERE id = 33;
UPDATE t_trip_activity SET lng = 118.0894, lat = 24.4798 WHERE id = 34;
UPDATE t_trip_activity SET lng = 118.0894, lat = 24.4798 WHERE id = 35;
UPDATE t_trip_activity SET lng = 118.0894, lat = 24.4798 WHERE id = 36;
UPDATE t_trip_activity SET lng = 118.0894, lat = 24.4798 WHERE id = 37;
UPDATE t_trip_activity SET lng = 118.0894, lat = 24.4798 WHERE id = 38;
UPDATE t_trip_activity SET lng = 118.0894, lat = 24.4798 WHERE id = 39;
UPDATE t_trip_activity SET lng = 118.0894, lat = 24.4798 WHERE id = 40;


USE `travel_match`;

INSERT INTO `t_companion_post` (`id`, `creator_id`, `related_plan_id`, `destination`, `start_date`, `end_date`, `min_people`, `max_people`, `budget_min`, `budget_max`, `expected_mate_desc`, `visibility`, `status`, `created_at`) VALUES
                                                                                                                                                                                                                                        (4, 1, NULL, '京都、奈良', '2025-04-01', '2025-04-06', 2, 4, 10000, 15000, '樱花季文化之旅，希望旅友喜欢古迹与美食', 'public', 'open', '2024-01-28 14:00:00'),
                                                                                                                                                                                                                                        (5, 2, NULL, '大理、丽江', '2025-05-10', '2025-05-16', 2, 4, 4000, 8000, '云南休闲游，洱海+古城，喜欢拍照的伙伴优先', 'public', 'open', '2024-01-29 10:00:00'),
                                                                                                                                                                                                                                        (6, 3, NULL, '厦门', '2025-06-01', '2025-06-04', 2, 4, 2000, 4000, '鼓浪屿+美食，短途放松，性格随和即可', 'public', 'open', '2024-01-30 09:00:00');
