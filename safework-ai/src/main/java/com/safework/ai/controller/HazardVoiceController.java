package com.safework.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.safework.ai.dto.HazardDraftVO;
import com.safework.ai.dto.HazardTextDTO;
import com.safework.ai.dto.TranscribeVO;
import com.safework.ai.service.HazardVoiceService;
import com.safework.common.core.R;
import com.safework.common.exception.BizException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "语音智能上报隐患", description = "语音转文字（讯飞语音听写）+ 大模型提取隐患信息，结果只用于填表，由上报人核对后提交")
@RestController
@RequestMapping("/ai/hazard")
@RequiredArgsConstructor
public class HazardVoiceController {

    private final HazardVoiceService service;

    @SaCheckPermission("safety:hazard:report")
    @Operation(summary = "语音转文字（audio：16k 16bit 单声道 PCM，最长 60 秒）")
    @PostMapping("/transcribe")
    public R<TranscribeVO> transcribe(@RequestParam("audio") MultipartFile audio) {
        try {
            return R.ok(service.transcribe(audio.getBytes()));
        } catch (IOException e) {
            throw new BizException("读取录音失败，请重试");
        }
    }

    @SaCheckPermission("safety:hazard:report")
    @Operation(summary = "一句话提取隐患信息（标题、类别、等级、位置、受限空间、描述）")
    @PostMapping("/parse")
    public R<HazardDraftVO> parse(@Valid @RequestBody HazardTextDTO dto) {
        return R.ok(service.parse(dto.getText()));
    }
}
