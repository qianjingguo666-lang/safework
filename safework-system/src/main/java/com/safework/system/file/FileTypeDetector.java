package com.safework.system.file;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 根据文件头几个字节（“魔数”）判断真实类型，不信任文件名和浏览器给的 Content-Type。
 * 例如把 shell.jsp 改名成 photo.jpg 上传，文件头不是 JPEG，直接拒绝。
 * <p>
 * 纯 Java，没有依赖，方便单元测试。
 */
public final class FileTypeDetector {

    /** 识别结果：扩展名 + 浏览器用的 Content-Type */
    public record FileType(String ext, String contentType, boolean image) {
    }

    private static final FileType JPG = new FileType("jpg", "image/jpeg", true);
    private static final FileType PNG = new FileType("png", "image/png", true);
    private static final FileType WEBP = new FileType("webp", "image/webp", true);
    private static final FileType PDF = new FileType("pdf", "application/pdf", false);
    private static final Map<String, FileType> ZIP_BASED = Map.of(
            "docx", new FileType("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", false),
            "xlsx", new FileType("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", false));
    private static final Map<String, FileType> OLE_BASED = Map.of(
            "doc", new FileType("doc", "application/msword", false),
            "xls", new FileType("xls", "application/vnd.ms-excel", false));

    public static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "webp");

    private FileTypeDetector() {
    }

    /**
     * @param head     文件开头至少 12 个字节
     * @param fileName 原始文件名：只用来区分 docx / xlsx 这类文件头相同的格式
     * @return 识别不出或不在白名单里返回 null
     */
    public static FileType detect(byte[] head, String fileName) {
        if (head == null || head.length < 4) {
            return null;
        }
        if (u(head[0]) == 0xFF && u(head[1]) == 0xD8 && u(head[2]) == 0xFF) {
            return JPG;
        }
        if (u(head[0]) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return PNG;
        }
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return WEBP;
        }
        if (head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F') {
            return PDF;
        }
        String ext = extOf(fileName);
        // docx、xlsx 本质是 zip 压缩包（PK 开头）
        if (head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4) {
            return ZIP_BASED.get(ext);
        }
        // 老版 doc、xls 是 OLE 复合文档
        if (u(head[0]) == 0xD0 && u(head[1]) == 0xCF && u(head[2]) == 0x11 && u(head[3]) == 0xE0) {
            return OLE_BASED.get(ext);
        }
        return null;
    }

    public static String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static int u(byte b) {
        return b & 0xFF;
    }
}
