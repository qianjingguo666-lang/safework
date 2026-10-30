package com.safework.ai.asr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("讯飞语音听写：鉴权地址、结果解析、错误提示")
class XfyunIatClientTest {

    @Test
    @DisplayName("鉴权地址：date 是 RFC1123 格式，signature 是对 host/date/请求行的 HMAC-SHA256")
    void authUrl() throws Exception {
        ZonedDateTime now = ZonedDateTime.of(2026, 10, 30, 8, 5, 9, 0, ZoneOffset.ofHours(8));
        String url = XfyunIatClient.authUrl("wss://iat-api.xfyun.cn/v2/iat", "key123", "secret456", now);
        assertTrue(url.startsWith("wss://iat-api.xfyun.cn/v2/iat?authorization="));

        Map<String, String> q = new HashMap<>();
        for (String kv : URI.create(url).getRawQuery().split("&")) {
            String[] p = kv.split("=", 2);
            q.put(p[0], URLDecoder.decode(p[1], StandardCharsets.UTF_8));
        }
        assertEquals("Fri, 30 Oct 2026 00:05:09 GMT", q.get("date"));
        assertEquals("iat-api.xfyun.cn", q.get("host"));

        // 按讯飞文档的步骤独立算一遍签名
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret456".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = Base64.getEncoder().encodeToString(mac.doFinal(
                "host: iat-api.xfyun.cn\ndate: Fri, 30 Oct 2026 00:05:09 GMT\nGET /v2/iat HTTP/1.1".getBytes(StandardCharsets.UTF_8)));
        String auth = new String(Base64.getDecoder().decode(q.get("authorization")), StandardCharsets.UTF_8);
        assertEquals("api_key=\"key123\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"" + sig + "\"", auth);
    }

    @Test
    @DisplayName("一段结果里的文字按 ws[].cw[0].w 拼接")
    void words() throws Exception {
        var node = new ObjectMapper().readTree("{\"sn\":1,\"ws\":[{\"cw\":[{\"w\":\"罐区\"}]},{\"cw\":[{\"w\":\"东侧\"}]},{\"cw\":[{\"w\":\"。\"}]}]}");
        assertEquals("罐区东侧。", XfyunIatClient.words(node));
    }

    @Test
    @DisplayName("常见错误码翻译成中文提示")
    void errorMessage() {
        assertTrue(XfyunIatClient.errorMessage(11200, "licc limit").contains("没有开通"));
        assertTrue(XfyunIatClient.errorMessage(99999, "unknown").endsWith("unknown"));
    }
}
