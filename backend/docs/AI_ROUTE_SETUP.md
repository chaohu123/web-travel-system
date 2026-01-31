# AI 路线生成配置说明

路线规划页的「AI 生成我的路线」会根据前端表单（出发地、目的地、预算、交通、节奏、兴趣权重）**调用真实 AI API** 生成多套方案，不同选择会得到不同内容。

## 1. 配置 API Key

支持 **OpenAI 兼容** 的接口。当前默认已配置 **DeepSeek**（`base-url: https://api.deepseek.com`，`model: deepseek-chat`）。

### 方式一：环境变量（推荐，避免把 key 提交到仓库）

**DeepSeek 示例：**

```bash
export OPENAI_API_KEY=你的DeepSeek密钥
```

然后启动后端即可。

### 方式二：配置文件

在 `application.yml` 或 `application-local.yml` 中设置：

```yaml
app:
  ai:
    enabled: true
    api-key: sk-xxx
    base-url: https://api.openai.com   # 可改为 Azure/通义/智谱等地址
    model: gpt-4o-mini
    timeout-seconds: 60
```

- **OpenAI**：`base-url: https://api.openai.com`，`model: gpt-4o-mini` 或 `gpt-4o`
- **Azure OpenAI**：`base-url: https://xxx.openai.azure.com`，需在请求头或 URL 中区分 deployment（本实现默认用 `model` 作为 deployment）
- **通义千问**：`base-url: https://dashscope.aliyuncs.com/compatible-mode/v1`，`model: qwen-turbo` 等
- **智谱**：`base-url: https://open.bigmodel.cn/api/paas/v4`，`model: glm-4-flash` 等

## 2. 未配置或失败时的行为

- **未配置 API Key** 或 **app.ai.enabled=false**：不调用 AI，直接使用内置 mock 数据（按目的地城市返回固定方案）。
- **调用超时/报错**：自动回退为 mock 数据，并在日志中输出 `AI route generation failed, will fallback to mock`。

## 3. 接口约定

后端调用的是 **Chat Completions** 风格接口：

- 请求：`POST {base-url}/v1/chat/completions`
- 请求头：`Authorization: Bearer {api-key}`，`Content-Type: application/json`
- 请求体：`model`、`messages`、`response_format: { "type": "json_object" }`、`temperature`
- 若某厂商不支持 `response_format`，可去掉该字段，但需保证模型返回**纯 JSON**，否则解析会失败并回退 mock。

AI 返回的 JSON 需符合约定结构（见 `AiRouteClient` 中 SYSTEM_PROMPT），包含 `variants` 数组，每项含 `id`、`name`、`days`，每天含 `dayIndex`、`date`、`durationMinutes`、`distanceKm`、`commuteMinutes`、`items`，每个 item 含 `id`、`name`、`image`、`stayMinutes`、`tags`。
