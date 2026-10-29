package com.safework.system.file;

import com.safework.common.annotation.OperLog;
import com.safework.common.core.R;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 附件：登录即可调用，具体能不能看、能不能传由业务单据决定（见 BizAccessChecker） */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping("/list")
    public R<FileListVO> list(@RequestParam String bizType, @RequestParam Long bizId,
                              @RequestParam(required = false) String tag) {
        return R.ok(fileService.list(bizType, bizId, tag));
    }

    @OperLog(module = "附件", action = "上传附件")
    @PostMapping("/upload")
    public R<SysFile> upload(@RequestParam String bizType, @RequestParam Long bizId, @RequestParam String tag,
                             @RequestParam("file") MultipartFile file) {
        return R.ok(fileService.upload(bizType, bizId, tag, file));
    }

    /** 预览 / 下载（download=true 强制下载） */
    @GetMapping("/{id}")
    public void download(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download,
                         HttpServletResponse response) {
        fileService.download(id, download, response);
    }

    @OperLog(module = "附件", action = "删除附件")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return R.ok();
    }
}
