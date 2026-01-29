# 数据库示例数据说明

## 文件说明

- `schema_mysql.sql` - 数据库表结构定义
- `sample_data.sql` - 完整的示例数据插入脚本

## 使用步骤

### 1. 创建数据库和表结构

```bash
# 在MySQL中执行
mysql -u root -p < schema_mysql.sql
```

或者直接在MySQL客户端中执行 `schema_mysql.sql` 文件。

### 2. 插入示例数据

```bash
# 在MySQL中执行
mysql -u root -p travel_match < sample_data.sql
```

或者直接在MySQL客户端中执行 `sample_data.sql` 文件。

## 示例数据说明

### 用户数据（6个用户）

| ID | 邮箱 | 手机 | 昵称 | 密码 |
|---|---|---|---|---|
| 1 | user1@example.com | 13800138001 | 旅行达人小王 | 123456 |
| 2 | user2@example.com | 13800138002 | 摄影爱好者 | 123456 |
| 3 | user3@example.com | 13800138003 | 美食探索家 | 123456 |
| 4 | xiaolu@example.com | 13900139001 | 小鹿 | 123456 |
| 5 | xingzhe@example.com | 13900139002 | 行者老张 | 123456 |
| 6 | taotao@example.com | 13900139003 | 桃桃 | 123456 |

**注意：** 所有用户密码统一为 `123456`（已使用BCrypt加密）

### 路线数据（3条路线，对应前端示例）

#### 路线1：京都奈良文化深度 5 日游
- **ID**: 1
- **目的地**: 京都、奈良
- **日期**: 2025-04-01 至 2025-04-05
- **预算**: 12000元
- **行程**: 5天，包含清水寺、伏见稻荷大社、奈良公园、岚山等景点

#### 路线2：云南大理丽江休闲 6 日
- **ID**: 2
- **目的地**: 大理、丽江
- **日期**: 2025-05-10 至 2025-05-15
- **预算**: 6000元
- **行程**: 6天，包含大理古城、洱海、苍山、丽江古城、玉龙雪山等

#### 路线3：厦门鼓浪屿美食 3 日
- **ID**: 3
- **目的地**: 厦门
- **日期**: 2025-06-01 至 2025-06-03
- **预算**: 3000元
- **行程**: 3天，包含鼓浪屿、厦门大学、南普陀寺、曾厝垵等

### 结伴数据（3个结伴帖子）

1. **北海道摄影小分队** - 2月初出发，7天
2. **新疆自驾游小队** - 7月出发，10天
3. **清迈美食探索团** - 3月出发，5天

### 游记数据（3篇游记）

1. **一个人去冰岛：环岛自驾与极光攻略** - 作者：旅行达人小王
2. **东京 5 日暴走打卡清单（含机酒预算）** - 作者：摄影爱好者
3. **大理洱海边的慢生活：住宿与拍照机位** - 作者：美食探索家

### 社区动态（5条动态）

包含用户发布的旅行动态和图片分享。

### 评论数据（14条评论）

- 路线评论：7条（对3条路线的评价）
- 游记评论：4条（对3篇游记的评论）
- 结伴评论：3条（对3个结伴帖子的回复）

## 数据关系说明

### 外键关系

- `t_user_profile.id` → `t_user.id` (一对一)
- `t_user_preference.id` → `t_user.id` (一对一)
- `t_user_reputation.id` → `t_user.id` (一对一)
- `t_trip_plan.user_id` → `t_user.id` (多对一)
- `t_trip_day.plan_id` → `t_trip_plan.id` (多对一)
- `t_trip_activity.day_id` → `t_trip_day.id` (多对一)
- `t_companion_post.creator_id` → `t_user.id` (多对一)
- `t_companion_post.related_plan_id` → `t_trip_plan.id` (可选)
- `t_companion_team.post_id` → `t_companion_post.id` (多对一)
- `t_team_member.team_id` → `t_companion_team.id` (多对一)
- `t_team_member.user_id` → `t_user.id` (多对一)
- `t_travel_note.author_id` → `t_user.id` (多对一)
- `t_feed_post.user_id` → `t_user.id` (多对一)
- `t_comment.user_id` → `t_user.id` (多对一)

## 注意事项

1. **密码加密**: 所有密码都使用BCrypt加密，明文密码为 `123456`
2. **时间戳**: 创建时间和更新时间已设置，部分数据使用了固定的时间以便测试
3. **图片URL**: 使用了 `picsum.photos` 作为占位图片服务
4. **JSON字段**: `preference_weights_json` 和 `image_urls_json` 字段存储JSON字符串格式的数据

## 测试账号

推荐使用以下账号进行测试：

- **邮箱**: `user1@example.com` / **密码**: `123456`
- **手机**: `13800138001` / **密码**: `123456`

登录后可以：
- 查看自己的路线（ID: 1）
- 查看结伴信息
- 发布动态和评论
- 查看其他用户的游记

## 数据重置

如果需要重置数据，可以执行：

```sql
-- 删除所有数据（保留表结构）
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE t_comment;
TRUNCATE TABLE t_feed_post;
TRUNCATE TABLE t_travel_note;
TRUNCATE TABLE t_team_member;
TRUNCATE TABLE t_companion_team;
TRUNCATE TABLE t_companion_post;
TRUNCATE TABLE t_trip_activity;
TRUNCATE TABLE t_trip_day;
TRUNCATE TABLE t_trip_plan;
TRUNCATE TABLE t_user_reputation;
TRUNCATE TABLE t_user_preference;
TRUNCATE TABLE t_user_profile;
TRUNCATE TABLE t_user;
SET FOREIGN_KEY_CHECKS = 1;

-- 然后重新执行 sample_data.sql
```

## 前端对接说明

前端可以直接使用这些数据进行展示：

1. **首页路线列表**: 查询 `t_trip_plan` 表，ID为1、2、3的路线对应前端的3条示例路线
2. **路线详情页**: 通过路线ID查询 `t_trip_plan`、`t_trip_day`、`t_trip_activity` 获取完整行程
3. **结伴列表**: 查询 `t_companion_post` 表
4. **游记列表**: 查询 `t_travel_note` 表
5. **评论数据**: 通过 `target_type` 和 `target_id` 查询 `t_comment` 表

## 扩展建议

如果需要添加更多测试数据，可以参考现有数据的格式，注意：
- 保持外键关系的正确性
- 时间戳使用合理的日期
- JSON字段格式正确
- 密码使用BCrypt加密
