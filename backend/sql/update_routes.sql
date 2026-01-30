USE travel_match;

-- 京都东山区
UPDATE t_trip_activity SET lng = 135.7845, lat = 34.9948 WHERE id = 1; -- 清水寺
UPDATE t_trip_activity SET lng = 135.7783, lat = 34.9976 WHERE id = 2; -- 二年坂三年坂
UPDATE t_trip_activity SET lng = 135.7751, lat = 35.0035 WHERE id = 3; -- 祇园花见小路

-- 京都站附近酒店（下京区）
UPDATE t_trip_activity SET lng = 135.7588, lat = 34.9859 WHERE id = 4;

-- 伏见稻荷大社（伏见区）
UPDATE t_trip_activity SET lng = 135.7727, lat = 34.9671 WHERE id = 5;

-- 金阁寺（北区）
UPDATE t_trip_activity SET lng = 135.7289, lat = 35.0394 WHERE id = 6;

-- 锦市场（中京区）
UPDATE t_trip_activity SET lng = 135.7670, lat = 35.0055 WHERE id = 7;

-- 奈良公园 / 东大寺 / 春日大社（奈良市）
UPDATE t_trip_activity SET lng = 135.8442, lat = 34.6851 WHERE id = 8;  -- 奈良公园
UPDATE t_trip_activity SET lng = 135.8398, lat = 34.6889 WHERE id = 9;  -- 东大寺
UPDATE t_trip_activity SET lng = 135.8484, lat = 34.6814 WHERE id = 10; -- 春日大社

-- 岚山竹林 / 天龙寺 / 豆腐料理（右京区）
UPDATE t_trip_activity SET lng = 135.6725, lat = 35.0128 WHERE id = 11; -- 岚山竹林
UPDATE t_trip_activity SET lng = 135.6735, lat = 35.0158 WHERE id = 12; -- 天龙寺
UPDATE t_trip_activity SET lng = 135.6741, lat = 35.0132 WHERE id = 13; -- 豆腐料理（岚山附近）

-- 银阁寺 / 哲学之道（左京区）
UPDATE t_trip_activity SET lng = 135.7956, lat = 35.0271 WHERE id = 14; -- 银阁寺
UPDATE t_trip_activity SET lng = 135.7920, lat = 35.0265 WHERE id = 15; -- 哲学之道（起点附近）

-- 大理
UPDATE t_trip_activity SET lng = 100.1639, lat = 25.7018 WHERE id = 16; -- 大理古城
UPDATE t_trip_activity SET lng = 100.1607, lat = 25.7015 WHERE id = 17; -- 古城美食街
UPDATE t_trip_activity SET lng = 100.2071, lat = 25.7851 WHERE id = 18; -- 洱海边民宿
UPDATE t_trip_activity SET lng = 100.2000, lat = 25.6000 WHERE id = 19; -- 洱海环湖
UPDATE t_trip_activity SET lng = 100.1500, lat = 25.6500 WHERE id = 21; -- 苍山索道
UPDATE t_trip_activity SET lng = 100.2010, lat = 25.8225 WHERE id = 22; -- 喜洲古镇

-- 双廊古镇（洱源县）
UPDATE t_trip_activity SET lng = 100.1794, lat = 26.1108 WHERE id = 20;

-- 丽江
UPDATE t_trip_activity SET lng = 100.2340, lat = 26.8724 WHERE id = 23; -- 丽江古城
UPDATE t_trip_activity SET lng = 100.2340, lat = 26.8724 WHERE id = 24; -- 古城内客栈
UPDATE t_trip_activity SET lng = 100.2039, lat = 26.8768 WHERE id = 27; -- 束河古镇
UPDATE t_trip_activity SET lng = 100.2340, lat = 26.8724 WHERE id = 28; -- 纳西族特色菜

-- 玉龙雪山 / 蓝月谷
UPDATE t_trip_activity SET lng = 100.2590, lat = 27.1030 WHERE id = 25; -- 玉龙雪山
UPDATE t_trip_activity SET lng = 100.2444, lat = 27.0889 WHERE id = 26; -- 蓝月谷

-- 鼓浪屿
UPDATE t_trip_activity SET lng = 118.0676, lat = 24.4443 WHERE id = 29; -- 鼓浪屿
UPDATE t_trip_activity SET lng = 118.0704, lat = 24.4478 WHERE id = 30; -- 日光岩
UPDATE t_trip_activity SET lng = 118.0685, lat = 24.4452 WHERE id = 31; -- 龙头路小吃街
UPDATE t_trip_activity SET lng = 118.0716, lat = 24.4409 WHERE id = 32; -- 菽庄花园

-- 厦门
UPDATE t_trip_activity SET lng = 118.0894, lat = 24.4449 WHERE id = 33; -- 厦门大学
UPDATE t_trip_activity SET lng = 118.0932, lat = 24.4407 WHERE id = 34; -- 南普陀寺
UPDATE t_trip_activity SET lng = 118.0773, lat = 24.4515 WHERE id = 35; -- 中山路附近酒店
UPDATE t_trip_activity SET lng = 118.0865, lat = 24.4403 WHERE id = 36; -- 沙坡尾美食
UPDATE t_trip_activity SET lng = 118.1200, lat = 24.4300 WHERE id = 37; -- 环岛路（起点附近）
UPDATE t_trip_activity SET lng = 118.0789, lat = 24.4568 WHERE id = 38; -- 八市海鲜市场
UPDATE t_trip_activity SET lng = 118.1098, lat = 24.4358 WHERE id = 39; -- 曾厝垵
UPDATE t_trip_activity SET lng = 118.0773, lat = 24.4555 WHERE id = 40; -- 中山路步行街

USE `travel_match`;

INSERT INTO `t_companion_post` (`id`, `creator_id`, `related_plan_id`, `destination`, `start_date`, `end_date`, `min_people`, `max_people`, `budget_min`, `budget_max`, `expected_mate_desc`, `visibility`, `status`, `created_at`) VALUES
                                                                                                                                                                                                                                        (4, 1, NULL, '京都、奈良', '2025-04-01', '2025-04-06', 2, 4, 10000, 15000, '樱花季文化之旅，希望旅友喜欢古迹与美食', 'public', 'open', '2024-01-28 14:00:00'),
                                                                                                                                                                                                                                        (5, 2, NULL, '大理、丽江', '2025-05-10', '2025-05-16', 2, 4, 4000, 8000, '云南休闲游，洱海+古城，喜欢拍照的伙伴优先', 'public', 'open', '2024-01-29 10:00:00'),
                                                                                                                                                                                                                                        (6, 3, NULL, '厦门', '2025-06-01', '2025-06-04', 2, 4, 2000, 4000, '鼓浪屿+美食，短途放松，性格随和即可', 'public', 'open', '2024-01-30 09:00:00');
