package com.safework.ai.asr;

/** 语音识别失败（消息是给用户看的中文提示） */
public class AsrException extends RuntimeException {

    public AsrException(String message) {
        super(message);
    }

    public AsrException(String message, Throwable cause) {
        super(message, cause);
    }
}
