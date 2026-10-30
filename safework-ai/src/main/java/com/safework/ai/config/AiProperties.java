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
    private Agent agent = new Agent();
    private Asr asr = new Asr();

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

    @Data
    public static class Agent {
        /** 最多请求大模型几轮（每轮可以调用多个工具），也是单次提问的成本上限 */
        private int maxRounds = 5;
        /** 单个工具结果最多返回给大模型多少字符，超出截断 */
        private int maxResultChars = 6000;
    }

    /** 语音转文字：讯飞开放平台「语音听写（流式版）」WebAPI */
    @Data
    public static class Asr {
        private String hostUrl = "wss://iat-api.xfyun.cn/v2/iat";
        /** 讯飞开放平台 → 控制台 → 我的应用 → 语音听写（流式版）里的 APPID、APIKey、APISecret */
        private String appId;
        private String apiKey;
        private String apiSecret;
        /** 每帧发送的音频字节数（16k 16bit 单声道，1280 字节 = 40 毫秒） */
        private int frameBytes = 1280;
        /** 两帧之间的间隔（毫秒）。音频是录好的整段，不需要按实时速度发送，但发得太快会被服务端拒绝 */
        private int frameIntervalMs = 10;
        private int timeoutSeconds = 30;

        public boolean isEnabled() {
            return StringUtils.hasText(appId) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret);
        }
    }
}
