## 整体 TODO 清单（MVP 阶段）

### 一、项目基础搭建

- [ ] 创建后端 Spring Boot 工程（`backend/`）
  - [ ] 初始化 Maven 项目（`pom.xml`，Java 17+）
  - [ ] 引入依赖：web、security、validation、JPA、MySQL、Lombok、OpenAPI、Redis
  - [ ] 配置基础包结构：`config`, `common`, `security`, `user`, `route`, `poi`, `companion`, `social`
  - [ ] 配置全局异常处理 & 统一返回体
  - [ ] 配置基础 CORS & 安全策略（JWT 占位）

- [ ] 创建前端 Vue 3 + Vite 工程（`frontend/`）
  - [ ] 初始化 Vite+Vue3+TS 项目结构
  - [ ] 配置 Vue Router、Pinia、Axios
  - [ ] 集成 UI 组件库（Element Plus / Ant Design Vue）
  - [ ] 搭建基础布局：`MainLayout` + 顶部导航

### 二、用户系统（模块 A）

- 后端
  - [ ] 设计并创建表：`user`, `user_profile`, `user_preference`, `user_reputation`
  - [ ] 实现注册 / 登录（邮箱 / 手机 + 密码）
  - [ ] 集成 JWT 鉴权（Spring Security）
  - [ ] 用户资料查询 & 修改接口
  - [ ] 信誉积分基础字段 & 查询接口

- 前端
  - [ ] 登录页 / 注册页
  - [ ] 个人中心页（基本信息 + 旅行偏好标签）
  - [ ] 全局登录状态（Pinia + 本地存储）

### 三、个性化路线规划引擎（模块 B）

- 后端
  - [ ] 设计并创建表：`trip_plan`, `trip_day`, `trip_activity`, `route_option`
  - [ ] 行程生成参数模型（目的地、日期、预算、偏好权重、节奏）
  - [ ] AI / 规则引擎接口占位：`POST /api/routes/ai-generate`
  - [ ] 行程 CRUD 接口：保存 / 修改 / 查询 / 删除
  - [ ] 预算、时长、里程的基本计算逻辑

- 前端
  - [ ] 行程创建页：目的地 + 时间 + 预算 + 偏好滑块 + 节奏选择
  - [ ] 行程结果展示页：多方案 A/B/C 切换
  - [ ] 行程编辑页：积木式拖拽编辑 + 右侧预算 / 里程 / 时间实时统计
  - [ ] 地图可视化（高德 / 百度 / Mapbox 三选一，先做占位）

### 四、旅友结伴平台（模块 C）

- 后端
  - [ ] 设计并创建表：`companion_post`, `companion_team`, `team_member`, `team_announcement`
  - [ ] 结伴发布接口：关联行程 / 旅行意向
  - [ ] 结伴列表 & 筛选接口
  - [ ] 推荐接口：根据偏好、时间、信誉分匹配
  - [ ] 小队管理接口：创建、加入、退出、成员管理、公告

- 前端
  - [ ] 结伴发布页
  - [ ] 结伴列表页 + 筛选条件
  - [ ] 推荐结伴页
  - [ ] 小队详情页（成员、公告、行程摘要）

### 五、社交与内容生态（模块 D）

- 后端
  - [ ] 设计并创建表：`travel_note`, `feed_post`, `comment`, `follow`
  - [ ] 游记 CRUD 接口（支持关联行程 / 景点）
  - [ ] 动态流接口（关注 + 推荐）
  - [ ] 评论接口（通用：游记 / 动态 / 景点）
  - [ ] 关注 / 取消关注接口

- 前端
  - [ ] 游记发布页（富文本编辑器）
  - [ ] 游记列表 & 详情页
  - [ ] 动态流首页
  - [ ] 关注 / 粉丝列表页

### 六、后续增强（可选）

- [ ] 内置聊天系统（WebSocket/STOMP）
- [ ] 多人协作编辑同一行程
- [ ] 更智能的推荐算法（结合行为 & 信誉分）
- [ ] 举报 / 拉黑 / 内容审核

