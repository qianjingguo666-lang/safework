package com.safework.ai.core;

/**
 * 从大模型的回复里取出第一个完整的 JSON 对象。
 * 模型经常在 JSON 前后加说明文字或 ```json 代码块，直接解析会失败。
 */
public final class JsonExtractor {

    private JsonExtractor() {
    }

    public static String firstObject(String text) {
        if (text == null) {
            return null;
        }
        int start = text.indexOf('{');
        while (start >= 0) {
            int depth = 0;
            boolean inString = false;
            boolean escape = false;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inString) {
                    if (escape) {
                        escape = false;
                    } else if (c == '\\') {
                        escape = true;
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1);
                    }
                }
            }
            start = text.indexOf('{', start + 1);
        }
        return null;
    }
}
