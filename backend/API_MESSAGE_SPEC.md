# 消息系统 API 接口规范

本文档描述前端消息系统所需的后端 API 接口规范，供后端开发参考实现。

## 基础路径

所有消息相关接口的基础路径：`/api/messages`

## 1. 消息概览（顶部角标）

### GET /api/messages/overview

获取当前用户的总未读消息数（用于顶部导航栏角标）。

**请求头：**
- `Authorization: Bearer {token}` （需要登录）

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalUnread": 5
  }
}
```

**字段说明：**
- `totalUnread`: 总未读消息数（互动消息 + 私信未读数）

---

## 2. 互动消息列表

### GET /api/messages/interactions

获取当前用户的互动消息列表（点赞、评论等）。

**请求头：**
- `Authorization: Bearer {token}` （需要登录）

**查询参数：**
- `page`: 页码，从 1 开始（必填）
- `pageSize`: 每页数量，默认 10（必填）
- `category`: 消息类型过滤，可选值：`all`（全部）、`like`（点赞）、`comment`（评论），默认 `all`

**示例请求：**
```
GET /api/messages/interactions?page=1&pageSize=10&category=all
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "type": "LIKE",
        "fromUserId": 2,
        "fromUserName": "张三",
        "fromUserAvatar": "https://example.com/avatar.jpg",
        "targetType": "note",
        "targetId": 10,
        "targetTitle": "我的游记标题",
        "contentPreview": null,
        "createdAt": "2026-01-29T10:30:00",
        "read": false
      },
      {
        "id": 2,
        "type": "COMMENT",
        "fromUserId": 3,
        "fromUserName": "李四",
        "fromUserAvatar": null,
        "targetType": "route",
        "targetId": 5,
        "targetTitle": "我的路线标题",
        "contentPreview": "这个路线很不错！",
        "createdAt": "2026-01-29T09:15:00",
        "read": true
      }
    ],
    "total": 25,
    "page": 1,
    "pageSize": 10
  }
}
```

**字段说明：**
- `id`: 消息 ID
- `type`: 消息类型，`LIKE`（点赞）或 `COMMENT`（评论）
- `fromUserId`: 发起互动的用户 ID
- `fromUserName`: 发起互动的用户昵称
- `fromUserAvatar`: 发起互动的用户头像 URL（可选）
- `targetType`: 被互动内容类型，`note`（游记）或 `route`（路线）
- `targetId`: 被互动内容的 ID
- `targetTitle`: 被互动内容的标题
- `contentPreview`: 评论内容预览（仅评论消息有值，点赞消息为 null）
- `createdAt`: 创建时间，ISO 8601 格式字符串
- `read`: 是否已读

**分页说明：**
- 使用 `PagedResult` 结构（与 `CommentDtos.PagedResult` 一致）
- `total`: 总记录数
- `page`: 当前页码
- `pageSize`: 每页数量
- `list`: 当前页数据列表

---

## 3. 私信会话列表

### GET /api/messages/conversations

获取当前用户的私信会话列表。

**请求头：**
- `Authorization: Bearer {token}` （需要登录）

**查询参数：**
- `page`: 页码，从 1 开始（必填）
- `pageSize`: 每页数量，默认 10（必填）

**示例请求：**
```
GET /api/messages/conversations?page=1&pageSize=10
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "peerUserId": 5,
        "peerNickname": "王五",
        "peerAvatar": "https://example.com/avatar.jpg",
        "lastMessagePreview": "好的，我们明天见",
        "lastMessageTime": "2026-01-29T14:20:00",
        "unreadCount": 3,
        "pinned": false
      }
    ],
    "total": 8,
    "page": 1,
    "pageSize": 10
  }
}
```

**字段说明：**
- `id`: 会话 ID
- `peerUserId`: 对方用户 ID
- `peerNickname`: 对方用户昵称
- `peerAvatar`: 对方用户头像 URL（可选）
- `lastMessagePreview`: 最后一条消息的预览（文本内容，截取前 50 字符）
- `lastMessageTime`: 最后一条消息的时间，ISO 8601 格式字符串
- `unreadCount`: 该会话的未读消息数
- `pinned`: 是否置顶（可选，默认 false）

---

## 4. 标记互动消息已读

### POST /api/messages/interactions/{id}/read

标记单条互动消息为已读。

**请求头：**
- `Authorization: Bearer {token}` （需要登录）

**路径参数：**
- `id`: 消息 ID

**请求体：**
```json
{}
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

---

## 5. 全部互动消息标记已读

### POST /api/messages/interactions/read-all

将当前用户的所有互动消息标记为已读。

**请求头：**
- `Authorization: Bearer {token}` （需要登录）

**请求体：**
```json
{}
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

---

## 6. 清空会话未读数

### POST /api/messages/conversations/{id}/clear-unread

清空指定会话的未读消息数（用户进入聊天页时调用）。

**请求头：**
- `Authorization: Bearer {token}` （需要登录）

**路径参数：**
- `id`: 会话 ID

**请求体：**
```json
{}
```

**响应：**
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

---

## 数据库设计建议

### 互动消息表（t_interaction_message）

```sql
CREATE TABLE `t_interaction_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `recipient_id` BIGINT NOT NULL COMMENT '接收者用户ID',
  `from_user_id` BIGINT NOT NULL COMMENT '发起互动用户ID',
  `type` VARCHAR(16) NOT NULL COMMENT '消息类型: LIKE/COMMENT',
  `target_type` VARCHAR(32) NOT NULL COMMENT '目标类型: note/route',
  `target_id` BIGINT NOT NULL COMMENT '目标ID',
  `target_title` VARCHAR(255) DEFAULT NULL COMMENT '目标标题（冗余字段，便于展示）',
  `content_preview` VARCHAR(512) DEFAULT NULL COMMENT '评论内容预览（仅评论有值）',
  `read` BOOLEAN DEFAULT FALSE COMMENT '是否已读',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_recipient_read` (`recipient_id`, `read`, `created_at`),
  KEY `idx_target` (`target_type`, `target_id`),
  CONSTRAINT `fk_msg_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_msg_from_user` FOREIGN KEY (`from_user_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='互动消息表';
```

### 私信会话表（t_private_conversation）

```sql
CREATE TABLE `t_private_conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user1_id` BIGINT NOT NULL COMMENT '用户1 ID（较小的ID）',
  `user2_id` BIGINT NOT NULL COMMENT '用户2 ID（较大的ID）',
  `last_message_preview` VARCHAR(255) DEFAULT NULL COMMENT '最后一条消息预览',
  `last_message_time` DATETIME DEFAULT NULL COMMENT '最后一条消息时间',
  `user1_unread_count` INT DEFAULT 0 COMMENT '用户1的未读数',
  `user2_unread_count` INT DEFAULT 0 COMMENT '用户2的未读数',
  `user1_pinned` BOOLEAN DEFAULT FALSE COMMENT '用户1是否置顶',
  `user2_pinned` BOOLEAN DEFAULT FALSE COMMENT '用户2是否置顶',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversation_pair` (`user1_id`, `user2_id`),
  KEY `idx_user1` (`user1_id`, `updated_at`),
  KEY `idx_user2` (`user2_id`, `updated_at`),
  CONSTRAINT `fk_conv_user1` FOREIGN KEY (`user1_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_conv_user2` FOREIGN KEY (`user2_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信会话表';
```

### 私信消息表（t_private_message）

```sql
CREATE TABLE `t_private_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `conversation_id` BIGINT NOT NULL COMMENT '所属会话ID',
  `sender_id` BIGINT NOT NULL COMMENT '发送者用户ID',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `type` VARCHAR(16) DEFAULT 'text' COMMENT '消息类型: text/image/route/companion',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_time` (`conversation_id`, `created_at`),
  CONSTRAINT `fk_msg_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `t_private_conversation` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_msg_sender` FOREIGN KEY (`sender_id`) REFERENCES `t_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私信消息表';
```

---

## 实现建议

1. **互动消息生成时机：**
   - 用户点赞时，在 `ContentLike` 保存后，创建一条 `LIKE` 类型的互动消息
   - 用户评论时，在 `Comment` 保存后，创建一条 `COMMENT` 类型的互动消息

2. **未读数计算：**
   - `overview` 接口：统计 `t_interaction_message` 中 `recipient_id = 当前用户` 且 `read = false` 的数量 + 所有会话的未读数之和

3. **会话列表排序：**
   - 按 `last_message_time` 降序排列（最新的在前）

4. **分页：**
   - 使用 Spring Data JPA 的 `Pageable`，注意前端传入的 `page` 从 1 开始，需要转换为从 0 开始

5. **权限校验：**
   - 所有接口都需要登录（通过 JWT 认证）
   - 确保用户只能查看自己的消息

---

## 前端对接说明

前端已实现完整的消息系统 UI，包括：
- 顶部导航栏消息入口（带未读角标）
- 消息中心页面（互动消息 + 私信会话）
- 消息已读/未读状态管理
- 分页加载
- 移动端适配

前端 API 调用已对齐后端接口规范，后端实现上述接口后即可正常使用。
