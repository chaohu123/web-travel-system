package com.example.travel.route.service;

import com.example.travel.route.config.AiRouteProperties;
import com.example.travel.route.dto.TripPlanDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 调用 OpenAI 兼容 API 生成旅行路线（根据表单参数生成不同内容）。
 * 支持 OpenAI、Azure OpenAI、通义千问、智谱、OpenRouter 等。
 */
@Service
public class AiRouteClient {

    private static final Logger log = LoggerFactory.getLogger(AiRouteClient.class);

    private final AiRouteProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AiRouteClient(AiRouteProperties properties,
                         @Qualifier("aiRouteRestTemplate") RestTemplate restTemplate,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return properties.isEnabled() && properties.hasApiKey();
    }

    /**
     * 根据用户表单请求调用 AI 生成多套路线方案；失败或不可用时返回 null，由调用方回退 mock。
     */
    public TripPlanDtos.AiGenerateResponse generate(TripPlanDtos.AiGenerateRequest req) {
        if (!isAvailable()) {
            if (!properties.isEnabled()) {
                log.info("[AI路线] 未调用 AI：app.ai.enabled=false，请在 application.yml 中设为 true");
            } else if (!properties.hasApiKey()) {
                log.info("[AI路线] 未调用 AI：未配置 API Key，请设置环境变量 OPENAI_API_KEY 或在 application.yml 中配置 app.ai.api-key");
            }
            return null;
        }
        String prompt = buildPrompt(req);
        String url = properties.getBaseUrl().replaceAll("/$", "") + "/v1/chat/completions";
        log.info("[AI路线] 开始调用 AI: url={}, model={}, 目的地={}", url, properties.getModel(), req.getDestinations());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey().trim());
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getModel());
        body.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)
        ));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0.7);
        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("[AI路线] API 非 2xx 或空 body: {}", response.getStatusCode());
                return null;
            }
            String rawBody = response.getBody();
            log.info("[AI路线] AI 原始响应长度: {} 字符", rawBody != null ? rawBody.length() : 0);
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                log.warn("[AI路线] AI 返回无 choices");
                return null;
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (content.isBlank()) {
                log.warn("[AI路线] AI 返回 content 为空");
                return null;
            }
            log.info("[AI路线] AI 返回 content 预览(前800字符): {}", content.length() > 800 ? content.substring(0, 800) + "..." : content);
            TripPlanDtos.AiGenerateResponse parsed = parseResponse(content, req.getStartDate());
            if (parsed != null && parsed.getVariants() != null) {
                log.info("[AI路线] AI 解析成功: 方案数={}, 各方案天数={}",
                        parsed.getVariants().size(),
                        parsed.getVariants().stream()
                                .map(v -> v.getDays() != null ? v.getDays().size() : 0)
                                .toList());
                log.debug("[AI路线] AI 解析后的完整数据: {}", toJsonSafe(parsed));
            }
            return parsed;
        } catch (Exception e) {
            log.warn("[AI路线] 调用失败，将回退 mock: {}", e.getMessage());
            return null;
        }
    }

    private static final String SYSTEM_PROMPT = """
你是一个专业的旅行路线规划助手。根据用户给出的出发地、目的地、预算、交通方式、节奏和兴趣权重，生成多套可执行的旅行方案。

你必须严格按照以下 JSON 结构返回，不要包含任何其他文字或 markdown 标记，只输出一个合法 JSON 对象。每个景点/活动必须包含经纬度 lng、lat（高德/GCJ-02 坐标系，中国境内使用）：

{
  "variants": [
    {
      "id": "a",
      "name": "方案 A（文化优先）",
      "days": [
        {
          "dayIndex": 1,
          "date": "YYYY-MM-DD",
          "durationMinutes": 300,
          "distanceKm": 15,
          "commuteMinutes": 30,
          "items": [
            {
              "id": "唯一短id",
              "name": "景点或活动名称",
              "image": "https://picsum.photos/seed/poi1/320/180",
              "stayMinutes": 120,
              "tags": ["文化", "历史"],
              "lng": 120.155,
              "lat": 30.274
            }
          ]
        }
      ]
    },
    {
      "id": "b",
      "name": "方案 B（自然优先）",
      "days": [ ... ]
    },
    {
      "id": "c",
      "name": "方案 C（轻松休闲）",
      "days": [ ... ]
    }
  ]
}

要求：
1. 必须返回 3 个方案（id 为 a、b、c），名称体现不同侧重（文化/自然/休闲等）。
2. days 的日期从用户给出的 startDate 连续到 endDate，每天 2～4 个景点/活动，且必须是目的地城市真实存在的景点或合理活动。
3. 每个 item 的 name 必须是具体景点或活动名，tags 为 2～3 个标签，stayMinutes 合理（30～240）。
4. 每个 item 必须包含 lng（经度）和 lat（纬度），使用高德/GCJ-02 坐标系。请根据景点的真实地理位置填写准确或近似的经纬度（可查阅中国常见景点的坐标），以便前端地图展示。例如：西湖约 120.155, 30.274；故宫约 116.397, 39.916；外滩约 121.490, 31.239。
5. 只输出上述 JSON，不要 markdown 代码块包裹。
""";

    private String buildPrompt(TripPlanDtos.AiGenerateRequest req) {
        String dest = req.getDestinations() != null ? String.join("、", req.getDestinations()) : "";
        String interests = req.getInterestWeightsJson() != null ? req.getInterestWeightsJson() : "{}";
        return String.format("""
请根据以下条件生成 3 套旅行方案（严格按约定 JSON 输出）：

- 出发地：%s
- 目的地：%s
- 出发日期：%s
- 结束日期：%s
- 总预算（元）：%d
- 人数：%d
- 交通方式：%s（public=公共交通，drive=自驾，mixed=混合）
- 节奏：%s（relaxed=轻松，moderate=适中，high=高强度）
- 兴趣权重（0～100）：%s

请让方案中的景点、活动与目的地和用户偏好一致，且每天行程合理。日期必须为 YYYY-MM-DD，从出发日期连续到结束日期。
""",
                nullToEmpty(req.getDepartureCity()),
                dest,
                req.getStartDate(),
                req.getEndDate(),
                req.getTotalBudget() != null ? req.getTotalBudget() : 8000,
                req.getPeopleCount() != null ? req.getPeopleCount() : 2,
                nullToEmpty(req.getTransport()),
                nullToEmpty(req.getIntensity()),
                interests);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String toJsonSafe(TripPlanDtos.AiGenerateResponse resp) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resp);
        } catch (Exception e) {
            return resp.toString();
        }
    }

    private TripPlanDtos.AiGenerateResponse parseResponse(String content, LocalDate startDate) {
        try {
            String json = content.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf("{");
                int end = json.lastIndexOf("}");
                if (start >= 0 && end > start) json = json.substring(start, end + 1);
            }
            JsonNode root = objectMapper.readTree(json);
            JsonNode variantsNode = root.path("variants");
            if (!variantsNode.isArray()) return null;
            List<TripPlanDtos.AiPlanVariant> variants = new ArrayList<>();
            for (JsonNode vNode : variantsNode) {
                TripPlanDtos.AiPlanVariant variant = new TripPlanDtos.AiPlanVariant();
                variant.setId(vNode.path("id").asText("a"));
                variant.setName(vNode.path("name").asText("方案"));
                ArrayNode daysArray = (ArrayNode) vNode.path("days");
                List<TripPlanDtos.AiDayPlan> days = new ArrayList<>();
                for (JsonNode dNode : daysArray) {
                    TripPlanDtos.AiDayPlan day = new TripPlanDtos.AiDayPlan();
                    int dayIndex = dNode.path("dayIndex").asInt(1);
                    day.setDayIndex(dayIndex);
                    String dateStr = dNode.path("date").asText(null);
                    if (dateStr != null && !dateStr.isBlank()) {
                        try {
                            day.setDate(LocalDate.parse(dateStr));
                        } catch (Exception e) {
                            day.setDate(startDate.plusDays(dayIndex - 1));
                        }
                    } else {
                        day.setDate(startDate.plusDays(dayIndex - 1));
                    }
                    day.setDurationMinutes(dNode.path("durationMinutes").asInt(180));
                    day.setDistanceKm(dNode.path("distanceKm").asInt(10));
                    day.setCommuteMinutes(dNode.path("commuteMinutes").asInt(20));
                    ArrayNode itemsArray = (ArrayNode) dNode.path("items");
                    List<TripPlanDtos.AiPoiItem> items = new ArrayList<>();
                    for (JsonNode iNode : itemsArray) {
                        TripPlanDtos.AiPoiItem item = new TripPlanDtos.AiPoiItem();
                        item.setId(iNode.path("id").asText(UUID.randomUUID().toString().replace("-", "").substring(0, 9)));
                        item.setName(iNode.path("name").asText("景点"));
                        item.setImage(iNode.path("image").asText("https://picsum.photos/seed/poi/320/180"));
                        item.setStayMinutes(iNode.path("stayMinutes").asInt(60));
                        List<String> tags = new ArrayList<>();
                        iNode.path("tags").forEach(t -> tags.add(t.asText()));
                        item.setTags(tags);
                        if (iNode.has("lng") && !iNode.path("lng").isNull()) {
                            item.setLng(iNode.path("lng").asDouble());
                        }
                        if (iNode.has("lat") && !iNode.path("lat").isNull()) {
                            item.setLat(iNode.path("lat").asDouble());
                        }
                        items.add(item);
                    }
                    day.setItems(items);
                    days.add(day);
                }
                variant.setDays(days);
                variants.add(variant);
            }
            TripPlanDtos.AiGenerateResponse resp = new TripPlanDtos.AiGenerateResponse();
            resp.setVariants(variants);
            return resp;
        } catch (Exception e) {
            log.warn("Parse AI response failed: {}", e.getMessage());
            return null;
        }
    }
}
