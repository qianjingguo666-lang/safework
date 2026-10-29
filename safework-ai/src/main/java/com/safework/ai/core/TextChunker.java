package com.safework.ai.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本切分：把长文档切成几百字的片段，用于检索。
 * <p>
 * 要点：
 * 1. 按句子边界切，不在句子中间截断；
 * 2. 相邻片段保留一点重叠，避免关键信息正好被切在两段中间；
 * 3. 识别标题（如“三、气体检测”），把标题拼到片段开头，片段单独拿出来也知道在讲什么；
 * 4. PDF 按页切，回答时可以注明出处在第几页。
 */
public final class TextChunker {

    /** 一页文本；pageNo 为 null 表示没有页码（txt、md） */
    public record PageText(Integer pageNo, String text) {
    }

    public record Chunk(int index, Integer pageNo, String content) {
    }

    private static final Pattern HEADING = Pattern.compile(
            "^(#{1,6}\\s+.+|第[一二三四五六七八九十百零\\d]+[章节条].{0,30}|[一二三四五六七八九十]+[、.．].{0,30}|\\d+(\\.\\d+)*[、.．\\s].{0,30})$");
    private static final String SENTENCE_END = "。！？；!?;";

    private final int chunkSize;
    private final int overlap;

    public TextChunker(int chunkSize, int overlap) {
        if (chunkSize < 50) {
            throw new IllegalArgumentException("chunkSize 太小");
        }
        this.chunkSize = chunkSize;
        this.overlap = Math.max(0, Math.min(overlap, chunkSize / 2));
    }

    public List<Chunk> split(List<PageText> pages) {
        List<Chunk> result = new ArrayList<>();
        String heading = "";
        for (PageText page : pages) {
            if (page.text() == null || page.text().isBlank()) {
                continue;
            }
            StringBuilder buf = new StringBuilder();
            String lastTail = "";
            for (String rawLine : page.text().replace("\r", "").split("\n")) {
                String line = rawLine.strip().replaceAll("[ \\t\\u3000]+", " ");
                if (line.isEmpty()) {
                    continue;
                }
                if (isHeading(line)) {
                    // 遇到新标题：先把之前的内容输出，标题之后的内容归属新标题
                    lastTail = flush(result, page.pageNo(), heading, buf, "");
                    heading = line.replaceFirst("^#+\\s*", "");
                    lastTail = "";
                    continue;
                }
                for (String sentence : sentences(line)) {
                    if (buf.length() + sentence.length() > chunkSize && buf.length() > 0) {
                        lastTail = flush(result, page.pageNo(), heading, buf, lastTail);
                        buf.append(lastTail);
                    }
                    if (sentence.length() > chunkSize) {
                        // 超长句子（比如没有标点的表格）只能硬切
                        for (int i = 0; i < sentence.length(); i += chunkSize - overlap) {
                            String piece = sentence.substring(i, Math.min(sentence.length(), i + chunkSize - overlap));
                            if (buf.length() + piece.length() > chunkSize && buf.length() > 0) {
                                lastTail = flush(result, page.pageNo(), heading, buf, lastTail);
                                buf.append(lastTail);
                            }
                            buf.append(piece);
                        }
                    } else {
                        buf.append(sentence);
                    }
                }
            }
            flush(result, page.pageNo(), heading, buf, lastTail);
        }
        return result;
    }

    /** 输出当前片段，返回片段结尾的重叠部分；buf 会被清空 */
    private String flush(List<Chunk> out, Integer pageNo, String heading, StringBuilder buf, String prevTail) {
        String body = buf.toString().strip();
        buf.setLength(0);
        // 只剩上一段的重叠尾巴，没有新内容，不输出
        if (body.isEmpty() || body.equals(prevTail.strip())) {
            return "";
        }
        String content = heading.isEmpty() ? body : "【" + heading + "】" + body;
        out.add(new Chunk(out.size(), pageNo, content));
        return tail(body);
    }

    /** 从结尾往前取若干完整句子，长度不超过 overlap，也不超过本段的一半（短片段不整段重复） */
    private String tail(String body) {
        int limit = Math.min(overlap, body.length() / 2);
        if (limit == 0) {
            return "";
        }
        List<String> parts = sentences(body);
        StringBuilder sb = new StringBuilder();
        for (int i = parts.size() - 1; i >= 0; i--) {
            if (sb.length() + parts.get(i).length() > limit) {
                break;
            }
            sb.insert(0, parts.get(i));
        }
        return sb.toString();
    }

    static List<String> sentences(String text) {
        List<String> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(c);
            if (SENTENCE_END.indexOf(c) >= 0) {
                list.add(sb.toString());
                sb.setLength(0);
            }
        }
        if (sb.length() > 0) {
            list.add(sb.toString());
        }
        return list;
    }

    static boolean isHeading(String line) {
        return line.length() <= 40 && HEADING.matcher(line).matches()
                && SENTENCE_END.indexOf(line.charAt(line.length() - 1)) < 0;
    }
}
