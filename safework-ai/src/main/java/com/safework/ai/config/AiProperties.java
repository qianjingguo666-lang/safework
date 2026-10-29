package com.safework.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** application.yml 里 safework.ai 下的配置 */
@Data
@ConfigurationProperties(prefix = "safework.ai")
public class AiProperties {

    private int rateLimitPerMinute = 10;
    private Chat chat = new Chat();
    private Embedding embedding = new Embedding();
    private Rag rag = new Rag();

    @Data
    public static class Chat {
        private String baseUrl;
        private String apiKey;
        private String model = "lite";
        private double temperature = 0.3;
        private int timeoutSeconds = 60;

        public boolean isEnabled() {
            return StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey);
        }
    }

    @Data
    public static class Embedding {
        private String baseUrl;
        private String apiKey;
        private String model = "text-embedding-v3";
        private int dimensions = 1024;
        private int batchSize = 10;

        public boolean isEnabled() {
            return StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey);
        }
    }

    @Data
    public static class Rag {
        private int chunkSize = 500;
        private int chunkOverlap = 80;
        private int topK = 4;
        /** 只用关键词检索时的最低分 */
        private double minScore = 0.25;
        /** 混合检索（向量 + 关键词）时的最低分：向量相似度天然偏高，阈值要更高 */
        private double minHybridScore = 0.45;
    }
}
