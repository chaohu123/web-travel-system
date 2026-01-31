package com.example.travel.route.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        factory.setReadTimeout(60_000);
        return new RestTemplate(factory);
    }

    /** AI 路线生成专用：读超时较长（DeepSeek 等生成多套方案可能需 2～3 分钟） */
    @Bean("aiRouteRestTemplate")
    public RestTemplate aiRouteRestTemplate(AiRouteProperties aiProps) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15_000);
        int readMs = (aiProps.getTimeoutSeconds() > 0 ? aiProps.getTimeoutSeconds() : 180) * 1000;
        factory.setReadTimeout(readMs);
        return new RestTemplate(factory);
    }
}
