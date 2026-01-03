# 个性化旅游路线规划与旅友结伴平台

## 项目简介

为旅行者提供智能化的个性化路线规划工具，并搭建一个安全、可靠的旅友匹配与社交平台。

## 技术栈

### 前端
- React 18 + TypeScript
- Vite (构建工具)
- Zustand (状态管理)
- Element Plus (UI组件库)
- React Router (路由)
- Axios (HTTP请求)
- 高德地图API

### 后端
- Spring Boot 3.2.0
- Java 17
- MySQL 8.0
- Redis
- JWT (认证)
- WebSocket (实时通信)
- Swagger (API文档)
- Maven

## 项目结构

```
web-travel-system/
├── frontend/          # 前端项目
│   ├── src/
│   │   ├── api/      # API请求
│   │   ├── components/ # 公共组件
│   │   ├── layouts/  # 布局组件
│   │   ├── pages/    # 页面组件
│   │   ├── stores/   # 状态管理
│   │   ├── types/    # TypeScript类型
│   │   └── utils/    # 工具函数
│   └── package.json
│
└── backend/           # 后端项目
    ├── src/main/java/com/travel/
    │   ├── config/    # 配置类
    │   ├── controller/ # 控制器
    │   ├── service/   # 业务逻辑
    │   ├── repository/ # 数据访问
    │   ├── entity/    # 实体类
    │   ├── dto/       # 数据传输对象
    │   ├── common/    # 公共类
    │   └── util/      # 工具类
    └── pom.xml
```

## 快速开始

### 环境要求

- Node.js 18+
- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端服务运行在：http://localhost:3000

### 后端启动

1. 创建MySQL数据库：
```sql
CREATE DATABASE travel_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 修改 `backend/src/main/resources/application.yml` 中的数据库和Redis配置

3. 运行后端：
```bash
cd backend
mvn spring-boot:run
```

后端服务运行在：http://localhost:8080

API文档：http://localhost:8080/swagger-ui.html

## 核心功能模块

### 模块A：用户系统
- 多方式注册/登录（邮箱、手机号、第三方）
- 个性化档案管理
- 验证与信誉体系

### 模块B：个性化路线规划引擎
- 智能目的地输入
- 多维度偏好设置
- AI路线生成
- 路线可视化与编辑
- 信息库（景点、餐厅、酒店）

### 模块C：旅友结伴平台
- 结伴需求发布
- 智能匹配与发现
- 沟通与约定
- 组队管理

### 模块D：社交与内容生态
- 旅行游记/攻略发布
- 动态/社区
- 关注/粉丝系统
- 收藏与分享

## 开发计划

详细开发计划请参考项目根目录下的开发文档。

## License

MIT

