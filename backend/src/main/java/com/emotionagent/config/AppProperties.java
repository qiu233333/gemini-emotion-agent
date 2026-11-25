package com.emotionagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Llm llm = new Llm();
    private Nlp nlp = new Nlp();
    private Agent agent = new Agent();
    private Cors cors = new Cors();

    @Data
    public static class Jwt {
        private String secret;
        private int expirationHours = 72;
    }

    @Data
    public static class Llm {
        private String provider = "deepseek";
        private String baseUrl = "https://api.deepseek.com";
        private String apiKey = "";
        private String model = "deepseek-chat";
        private int timeoutSeconds = 30;
        private int maxRetries = 1;
        private double temperature = 0.5;
        private int maxTokens = 600;
        private String promptVersion = "v1.0";
    }

    @Data
    public static class Nlp {
        private String defaultMethod = "RULE_V1";
        private String externalServiceUrl = "http://127.0.0.1:8001";
        private boolean externalEnabled = false;
    }

    @Data
    public static class Agent {
        private int maxHistoryItems = 5;
        private int maxHistoryChars = 1200;
        private int rateLimitPerMinute = 10;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
    }
}
