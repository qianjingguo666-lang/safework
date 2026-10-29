package com.safework.ai.service;

import com.safework.ai.core.TextChunker.PageText;
import com.safework.common.exception.BizException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 从上传的文件中提取文字：PDF 按页提取；txt / md 自动识别 UTF-8 或 GBK 编码 */
@Component
public class DocumentParser {

    public static final List<String> SUPPORTED = List.of("pdf", "txt", "md");

    public static String extension(String fileName) {
        int i = fileName == null ? -1 : fileName.lastIndexOf('.');
        return i < 0 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    public List<PageText> parse(String fileName, byte[] bytes) {
        String ext = extension(fileName);
        if ("pdf".equals(ext)) {
            return parsePdf(bytes);
        }
        if ("txt".equals(ext) || "md".equals(ext)) {
            return List.of(new PageText(null, decodeText(bytes)));
        }
        throw new BizException("只支持 PDF、TXT、MD 文件");
    }

    private List<PageText> parsePdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<PageText> pages = new ArrayList<>();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                pages.add(new PageText(p, stripper.getText(doc)));
            }
            boolean empty = pages.stream().allMatch(pt -> pt.text() == null || pt.text().isBlank());
            if (empty) {
                throw new BizException("PDF 中没有提取到文字，可能是扫描件，请上传文字版 PDF");
            }
            return pages;
        } catch (IOException e) {
            throw new BizException("PDF 解析失败：" + e.getMessage());
        }
    }

    /** Windows 记事本保存的文件可能是 GBK：先按 UTF-8 严格解码，失败再用 GBK */
    static String decodeText(byte[] bytes) {
        int offset = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer buf = ByteBuffer.wrap(bytes, offset, bytes.length - offset);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(buf).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, offset, bytes.length - offset, Charset.forName("GBK"));
        }
    }
}
