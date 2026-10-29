package com.safework.system.file;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** application.yml 里的 safework.file */
@Data
@Component
@ConfigurationProperties(prefix = "safework.file")
public class FileProperties {
    /** 本地存储目录（相对于启动目录）；Docker 里是 /app/data/uploads，挂载了数据卷 */
    private String localPath = "./data/uploads";
    /** 单个文件最大 MB */
    private int maxSizeMb = 10;
    /** 同一张单据同一种用途最多几个附件 */
    private int maxCountPerBiz = 9;
}
