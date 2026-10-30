package com.safework.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.safework.ai.agent.ModelReply;
import com.safework.ai.config.AiProperties;
import com.safework.ai.dto.ChatMessage;
import com.safework.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 大模型客户端：按 OpenAI 兼容协议调用（/chat/completions、/embeddings）。
 * 讯飞星火、通义千问、DeepSeek、智谱等都提供了兼容接口，换模型只需要改配置。
 * 没有引入 Spring AI 等框架，直接用 JDK 自带的 HttpClient，便于理解每一步在做什么。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmClient {

    private final AiProperties props;
    /** 独立的 ObjectMapper，不受全局 JSON 配置（Long 转字符串等）影响 */
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public boolean chatEnabled() {
        return props.getChat().isEnabled();
    }

    public boolean embeddingEnabled() {
        return props.getEmbedding().isEnabled();
    }

    /** 一次性返回完整回答 */
    public String chat(List<ChatMessage> messages) {
        return chat(messages, props.getChat().getTimeoutSeconds());
    }

    /** 指定超时时间：输出很长的任务（例如一次出十几道题）需要更久 */
    public String chat(List<ChatMessage> messages, int timeoutSeconds) {
        try {
            HttpResponse<String> resp = http.send(chatRequest(messages, false, timeoutSeconds), HttpResponse.BodyHandlers.ofString());
            JsonNode root = parse(resp.statusCode(), resp.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode()) {
                throw new BizException(502, "大模型返回格式异常：" + abbreviate(resp.body()));
            }
            return content.asText();
        } catch (IOException e) {
            throw new BizException(502, "调用大模型失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(502, "调用大模型被中断");
        }
    }

    /**
     * 流式回答：大模型每生成一小段就回调一次 onDelta。
     * 协议是 SSE：每行以 "data:" 开头，内容是 JSON，最后一行是 "data: [DONE]"。
     */
    public void chatStream(List<ChatMessage> messages, Consumer<String> onDelta) {
        try {
            HttpResponse<Stream<String>> resp = http.send(chatRequest(messages, true, props.getChat().getTimeoutSeconds()), HttpResponse.BodyHandlers.ofLines());
            try (Stream<String> lines = resp.body()) {
                if (resp.statusCode() / 100 != 2) {
                    String body = lines.collect(Collectors.joining("\n"));
                    throw new BizException(502, "大模型返回错误（HTTP " + resp.statusCode() + "）：" + abbreviate(body));
                }
                for (String line : (Iterable<String>) lines::iterator) {
                    String l = line.trim();
                    if (l.isEmpty()) {
                        continue;
                    }
                    if (!l.startsWith("data:")) {
                        // 部分厂商出错时不按 SSE 格式返回，直接给一个 JSON
                        if (l.startsWith("{")) {
                            parse(200, l);
                        }
                        continue;
                    }
                    String payload = l.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    JsonNode node = parse(200, payload);
                    String delta = node.path("choices").path(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        onDelta.accept(delta);
                    }
                }
            }
        } catch (IOException e) {
            throw new BizException(502, "调用大模型失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(502, "调用大模型被中断");
        }
    }

    /**
     * Function Calling：带上工具定义调用一次（非流式），返回 choices[0].message。
     * message 里有 tool_calls 表示模型要调用工具，否则 content 就是最终回答。
     * 需要模型支持 tools 参数（DeepSeek、通义千问、GLM、讯飞星火 Max/4.0 Ultra 等；星火 Lite 不支持）。
     *
     * @param tools 传 null 表示不提供工具，要求模型直接回答
     */
    public ModelReply chatWithTools(ArrayNode messages, ArrayNode tools) {
        AiProperties.Chat cfg = props.getChat();
        ObjectNode body = json.createObjectNode();
        body.put("model", cfg.getModel());
        body.put("stream", false);
        body.put("temperature", cfg.getTemperature());
        body.set("messages", messages);
        if (tools != null && !tools.isEmpty()) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(trimSlash(cfg.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = parse(resp.statusCode(), resp.body());
            JsonNode message = root.path("choices").path(0).path("message");
            if (!message.isObject()) {
                throw new BizException(502, "大模型返回格式异常：" + abbreviate(resp.body()));
            }
            return new ModelReply((ObjectNode) message, root.path("usage").path("total_tokens").asInt(0));
        } catch (IOException e) {
            throw new BizException(502, "调用大模型失败：" + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(502, "调用大模型被中断");
        }
    }

    /** 批量生成向量，返回顺序与输入一致 */
    public List<float[]> embed(List<String> texts) {
        AiProperties.Embedding cfg = props.getEmbedding();
        List<float[]> result = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += cfg.getBatchSize()) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + cfg.getBatchSize()));
            ObjectNode body = json.createObjectNode();
            body.put("model", cfg.getModel());
            ArrayNode input = body.putArray("input");
            batch.forEach(input::add);
            body.put("dimensions", cfg.getDimensions());
            body.put("encoding_format", "float");
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(trimSlash(cfg.getBaseUrl()) + "/embeddings"))
                        .timeout(Duration.ofSeconds(60))
                        .header("Authorization", "Bearer " + cfg.getApiKey())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                        .build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                JsonNode data = parse(resp.statusCode(), resp.body()).path("data");
                float[][] vectors = new float[batch.size()][];
                for (JsonNode item : data) {
                    JsonNode emb = item.path("embedding");
                    float[] v = new float[emb.size()];
                    for (int i = 0; i < v.length; i++) {
                        v[i] = (float) emb.get(i).asDouble();
                    }
                    vectors[item.path("index").asInt()] = v;
                }
                for (float[] v : vectors) {
                    if (v == null) {
                        throw new BizException(502, "向量模型返回的数量不对");
                    }
                    result.add(v);
                }
            } catch (IOException e) {
                throw new BizException(502, "调用向量模型失败：" + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BizException(502, "调用向量模型被中断");
            }
        }
        return result;
    }

    public float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    private HttpRequest chatRequest(List<ChatMessage> messages, boolean stream, int timeoutSeconds) throws IOException {
        AiProperties.Chat cfg = props.getChat();
        ObjectNode body = json.createObjectNode();
        body.put("model", cfg.getModel());
        body.put("stream", stream);
        body.put("temperature", cfg.getTemperature());
        ArrayNode arr = body.putArray("messages");
        for (ChatMessage m : messages) {
            arr.addObject().put("role", m.role()).put("content", m.content());
        }
        return HttpRequest.newBuilder(URI.create(trimSlash(cfg.getBaseUrl()) + "/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Authorization", "Bearer " + cfg.getApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
    }

    /** 统一检查 HTTP 状态码和厂商自定义的错误码 */
    private JsonNode parse(int status, String body) throws IOException {
        if (status / 100 != 2) {
            throw new BizException(502, "大模型接口返回错误（HTTP " + status + "）：" + abbreviate(body));
        }
        JsonNode node = json.readTree(body);
        // 讯飞：{"code": 10013, "message": "..."}；OpenAI 风格：{"error": {"message": "..."}}
        if (node.path("code").isInt() && node.path("code").asInt() != 0) {
            throw new BizException(502, "大模型接口返回错误：" + node.path("message").asText(abbreviate(body)));
        }
        if (node.has("error")) {
            throw new BizException(502, "大模型接口返回错误：" + node.path("error").path("message").asText(abbreviate(body)));
        }
        return node;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String abbreviate(String s) {
        return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "..." : s);
    }
}
