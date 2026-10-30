package com.safework.ai.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocketHandshakeException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 讯飞「语音听写（流式版）」WebAPI 客户端，只依赖 JDK 和 Jackson（方便单独测试）。
 * <pre>
 * 1. 鉴权：用 APISecret 对 "host、date、请求行" 做 HMAC-SHA256 签名，拼到 wss 地址的参数里
 * 2. 建立 WebSocket 连接，按帧发送音频（16k 采样、16bit、单声道 PCM，Base64 编码）
 *    第一帧 status=0 带 common（APPID）和 business（语言、方言、标点），中间帧 status=1，最后一帧 status=2
 * 3. 服务端分段返回识别结果（sn 是段号），收到 status=2 表示识别结束，按段号拼接成完整文字
 * </pre>
 */
public class XfyunIatClient {

    /** 16k 采样 × 2 字节 */
    public static final int BYTES_PER_SECOND = 32000;
    private static final DateTimeFormatter RFC1123 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);

    private final String hostUrl;
    private final String appId;
    private final String apiKey;
    private final String apiSecret;
    private final int frameBytes;
    private final int frameIntervalMs;
    private final int timeoutSeconds;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public XfyunIatClient(String hostUrl, String appId, String apiKey, String apiSecret,
                          int frameBytes, int frameIntervalMs, int timeoutSeconds) {
        this.hostUrl = hostUrl;
        this.appId = appId;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.frameBytes = frameBytes;
        this.frameIntervalMs = frameIntervalMs;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 识别一段完整的 PCM 音频，返回文字 */
    public String recognize(byte[] pcm) {
        Collector collector = new Collector();
        WebSocket ws;
        try {
            ws = http.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(authUrl(hostUrl, apiKey, apiSecret, ZonedDateTime.now(ZoneOffset.UTC))), collector)
                    .get(15, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new AsrException(handshakeMessage(e.getCause()), e);
        } catch (TimeoutException e) {
            throw new AsrException("连接讯飞语音服务超时，请检查网络", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AsrException("语音识别被中断", e);
        }
        try {
            int frames = Math.max(1, (pcm.length + frameBytes - 1) / frameBytes);
            for (int i = 0; i < frames && !collector.result.isDone(); i++) {
                int from = i * frameBytes;
                int to = Math.min(pcm.length, from + frameBytes);
                byte[] chunk = java.util.Arrays.copyOfRange(pcm, from, to);
                int status = i == 0 ? 0 : 1;
                ws.sendText(frame(status, chunk), true).get(10, TimeUnit.SECONDS);
                if (frameIntervalMs > 0) {
                    Thread.sleep(frameIntervalMs);
                }
            }
            if (!collector.result.isDone()) {
                ws.sendText(frame(2, new byte[0]), true).get(10, TimeUnit.SECONDS);
            }
            return collector.result.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof AsrException a ? a : new AsrException("语音识别失败：" + cause.getMessage(), cause);
        } catch (TimeoutException e) {
            throw new AsrException("语音识别超时，请重试", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AsrException("语音识别被中断", e);
        } finally {
            ws.abort();
        }
    }

    /**
     * 生成带鉴权参数的地址：
     * signature = Base64(HmacSHA256("host: …\ndate: …\nGET /v2/iat HTTP/1.1", APISecret))
     * authorization = Base64('api_key="…", algorithm="hmac-sha256", headers="host date request-line", signature="…"')
     */
    public static String authUrl(String hostUrl, String apiKey, String apiSecret, ZonedDateTime now) {
        URI uri = URI.create(hostUrl);
        String date = RFC1123.format(now.withZoneSameInstant(ZoneOffset.UTC));
        String origin = "host: " + uri.getHost() + "\ndate: " + date + "\nGET " + uri.getPath() + " HTTP/1.1";
        String signature = Base64.getEncoder().encodeToString(hmacSha256(origin, apiSecret));
        String authorization = Base64.getEncoder().encodeToString(String.format(
                "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                apiKey, signature).getBytes(StandardCharsets.UTF_8));
        return hostUrl + "?authorization=" + enc(authorization) + "&date=" + enc(date) + "&host=" + enc(uri.getHost());
    }

    static byte[] hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String frame(int status, byte[] audio) {
        ObjectNode root = json.createObjectNode();
        if (status == 0) {
            root.putObject("common").put("app_id", appId);
            // ptt=1 返回标点；vad_eos 静音多久算说完（毫秒），录好的整段音频设长一点，避免中间停顿被截断
            root.putObject("business").put("language", "zh_cn").put("domain", "iat").put("accent", "mandarin")
                    .put("ptt", 1).put("vad_eos", 5000);
        }
        root.putObject("data").put("status", status).put("format", "audio/L16;rate=16000").put("encoding", "raw")
                .put("audio", Base64.getEncoder().encodeToString(audio));
        return root.toString();
    }

    /** 把一条服务端返回的结果里的文字取出来：result.ws[].cw[0].w */
    static String words(JsonNode result) {
        StringBuilder sb = new StringBuilder();
        for (JsonNode w : result.path("ws")) {
            sb.append(w.path("cw").path(0).path("w").asText(""));
        }
        return sb.toString();
    }

    /** 讯飞的错误码翻译成能看懂的提示 */
    static String errorMessage(int code, String message) {
        String hint = switch (code) {
            case 10105, 10106, 10107 -> "参数错误";
            case 10165 -> "会话已失效，请重试";
            case 10313 -> "APPID 和 APIKey 不匹配，请检查 config/secret.yml";
            case 10114 -> "识别超时";
            case 11200 -> "没有开通语音听写服务，或者免费额度已用完（讯飞控制台 → 语音听写）";
            case 11201 -> "今天的调用次数已用完";
            case 10800 -> "并发数超出限制，请稍后再试";
            default -> null;
        };
        return "讯飞语音识别失败（错误码 " + code + "）" + (hint == null ? "：" + message : "：" + hint);
    }

    private static String handshakeMessage(Throwable e) {
        if (e instanceof WebSocketHandshakeException h) {
            int status = h.getResponse().statusCode();
            return switch (status) {
                case 401 -> "讯飞鉴权失败（401）：APIKey 或 APISecret 不正确，请检查 config/secret.yml";
                case 403 -> "讯飞拒绝访问（403）：电脑时间和北京时间相差超过 5 分钟，或者开启了 IP 白名单";
                default -> "连接讯飞语音服务失败（HTTP " + status + "）";
            };
        }
        if (e instanceof java.net.ConnectException || (e != null && e.getMessage() == null)) {
            return "连接不上讯飞语音服务，请检查网络（" + (e == null ? "未知错误" : e.getClass().getSimpleName()) + "）";
        }
        return "连接讯飞语音服务失败：" + (e == null ? "未知错误" : e.getMessage());
    }

    /** 接收服务端消息：一条消息可能被拆成多个文本片段，last=true 时才是完整的 JSON */
    private class Collector implements WebSocket.Listener {
        final CompletableFuture<String> result = new CompletableFuture<>();
        private final StringBuilder buffer = new StringBuilder();
        private final Map<Integer, String> segments = new TreeMap<>();

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                handle(text);
            }
            ws.request(1);
            return null;
        }

        private void handle(String text) {
            try {
                JsonNode root = json.readTree(text);
                int code = root.path("code").asInt(-1);
                if (code != 0) {
                    result.completeExceptionally(new AsrException(errorMessage(code, root.path("message").asText())));
                    return;
                }
                JsonNode data = root.path("data");
                JsonNode r = data.path("result");
                if (!r.isMissingNode()) {
                    segments.put(r.path("sn").asInt(segments.size() + 1), words(r));
                }
                if (data.path("status").asInt() == 2) {
                    result.complete(String.join("", segments.values()).trim());
                }
            } catch (Exception e) {
                result.completeExceptionally(new AsrException("解析讯飞返回结果失败：" + e.getMessage(), e));
            }
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            // 正常情况下先收到 status=2 再关闭；没收到就关闭说明出错了
            result.completeExceptionally(new AsrException("讯飞语音服务提前断开连接（" + statusCode + " " + reason + "）"));
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            result.completeExceptionally(new AsrException("语音识别连接出错：" + error.getMessage(), error));
        }
    }
}
