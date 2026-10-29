package com.safework.system.file;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.safework.common.exception.BizException;
import com.safework.common.tenant.TenantContext;
import com.safework.system.mapper.SysFileMapper;
import com.safework.system.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 附件上传、下载。安全措施：
 * <ol>
 *   <li>权限跟随业务单据（{@link BizAccessChecker}），不是“登录了就能下载任意文件”</li>
 *   <li>按文件内容识别类型（{@link FileTypeDetector}），只允许白名单格式，防止上传网页、脚本</li>
 *   <li>存储文件名用随机 UUID，不使用用户上传的文件名，防止路径穿越和覆盖别人的文件</li>
 *   <li>存储路径不返回给前端，下载必须经过接口</li>
 * </ol>
 */
@Slf4j
@Service
public class FileService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Map<String, BizAccessChecker> checkers;
    private final SysFileMapper fileMapper;
    private final FileStorage storage;
    private final FileProperties props;

    public FileService(List<BizAccessChecker> checkers, SysFileMapper fileMapper, FileStorage storage, FileProperties props) {
        this.checkers = checkers.stream().collect(Collectors.toMap(BizAccessChecker::bizType, Function.identity()));
        this.fileMapper = fileMapper;
        this.storage = storage;
        this.props = props;
    }

    public FileListVO list(String bizType, Long bizId, String tag) {
        BizAccessChecker checker = checker(bizType);
        checker.checkView(bizId);
        List<SysFile> files = fileMapper.selectList(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getBizType, bizType)
                .eq(SysFile::getBizId, bizId)
                .eq(StringUtils.hasText(tag), SysFile::getTag, tag)
                .orderByAsc(SysFile::getCreateTime));
        boolean editable = StringUtils.hasText(tag) && checker.canEdit(bizId, tag);
        return new FileListVO(files, editable, props.getMaxCountPerBiz());
    }

    public SysFile upload(String bizType, Long bizId, String tag, MultipartFile file) {
        BizAccessChecker checker = checker(bizType);
        checker.checkView(bizId);
        if (!StringUtils.hasText(tag) || !checker.canEdit(bizId, tag)) {
            throw new BizException(403, "当前环节不能上传附件");
        }
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择文件");
        }
        if (file.getSize() > props.getMaxSizeMb() * 1024L * 1024L) {
            throw new BizException("文件不能超过 " + props.getMaxSizeMb() + "MB");
        }
        long count = fileMapper.selectCount(Wrappers.<SysFile>lambdaQuery()
                .eq(SysFile::getBizType, bizType).eq(SysFile::getBizId, bizId).eq(SysFile::getTag, tag));
        if (count >= props.getMaxCountPerBiz()) {
            throw new BizException("最多上传 " + props.getMaxCountPerBiz() + " 个附件");
        }

        String originalName = cleanName(file.getOriginalFilename());
        FileTypeDetector.FileType type;
        try (InputStream in = file.getInputStream()) {
            type = FileTypeDetector.detect(in.readNBytes(12), originalName);
        } catch (IOException e) {
            throw new BizException("读取文件失败");
        }
        if (type == null) {
            throw new BizException("只支持 jpg / png / webp 图片、PDF、Word、Excel 文件（按文件内容识别，改扩展名无效）");
        }
        if (checker.imageOnly(tag) && !type.image()) {
            throw new BizException("这里只能上传图片");
        }

        String key = TenantContext.get() + "/" + LocalDate.now().format(MONTH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + "." + type.ext();
        try (InputStream in = file.getInputStream()) {
            storage.save(key, in);
        } catch (IOException e) {
            log.error("保存附件失败", e);
            throw new BizException(500, "保存文件失败，请稍后再试");
        }

        SysFile f = new SysFile();
        f.setBizType(bizType);
        f.setBizId(bizId);
        f.setTag(tag);
        f.setOriginalName(originalName);
        f.setExt(type.ext());
        f.setContentType(type.contentType());
        f.setSize(file.getSize());
        f.setStorageKey(key);
        f.setUploaderId(StpUtil.getLoginIdAsLong());
        Object nickname = StpUtil.getSession().get(AuthService.SESSION_NICKNAME);
        f.setUploaderName(nickname == null ? null : nickname.toString());
        try {
            fileMapper.insert(f);
        } catch (RuntimeException e) {
            // 数据库失败时把刚存的文件删掉，避免磁盘上留下没人认领的文件
            deleteQuietly(key);
            throw e;
        }
        return f;
    }

    /** 下载 / 预览：图片和 PDF 在浏览器里直接打开，其他格式下载 */
    public void download(Long id, boolean forceDownload, HttpServletResponse response) {
        SysFile f = get(id);
        checker(f.getBizType()).checkView(f.getBizId());
        InputStream in;
        try {
            in = storage.open(f.getStorageKey());
        } catch (IOException e) {
            // 先打开文件再写响应头：文件丢失时还能正常返回 JSON 错误
            log.warn("读取附件失败 {}：{}", id, e.getMessage());
            throw new BizException(404, "文件不存在或已被删除");
        }
        boolean inline = !forceDownload && (f.getContentType().startsWith("image/") || "pdf".equals(f.getExt()));
        String encoded = URLEncoder.encode(f.getOriginalName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(f.getContentType());
        response.setContentLengthLong(f.getSize());
        response.setHeader("Content-Disposition", (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded);
        // 禁止浏览器“猜”文件类型，配合内容识别，防止把文件当网页执行
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "private, max-age=3600");
        try (in; OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        } catch (IOException e) {
            // 一般是浏览器中途取消下载
            log.debug("附件下载中断 {}：{}", id, e.getMessage());
        }
    }

    /** 删除：只能删除自己上传的，并且当前环节允许修改附件 */
    public void delete(Long id) {
        SysFile f = get(id);
        BizAccessChecker checker = checker(f.getBizType());
        checker.checkView(f.getBizId());
        if (!f.getUploaderId().equals(StpUtil.getLoginIdAsLong()) || !checker.canEdit(f.getBizId(), f.getTag())) {
            throw new BizException(403, "只能在当前环节删除自己上传的附件");
        }
        fileMapper.deleteById(id);
        deleteQuietly(f.getStorageKey());
    }

    private SysFile get(Long id) {
        SysFile f = fileMapper.selectById(id);
        if (f == null) {
            throw new BizException(404, "文件不存在");
        }
        return f;
    }

    private BizAccessChecker checker(String bizType) {
        BizAccessChecker c = bizType == null ? null : checkers.get(bizType);
        if (c == null) {
            throw new BizException("不支持的业务类型：" + bizType);
        }
        return c;
    }

    /** 只保留文件名本身（去掉浏览器可能带上的路径），并限制长度 */
    static String cleanName(String name) {
        if (!StringUtils.hasText(name)) {
            return "未命名";
        }
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).trim();
        return n.length() > 100 ? n.substring(n.length() - 100) : n;
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (Exception e) {
            log.warn("删除附件文件失败 {}：{}", key, e.getMessage());
        }
    }
}
