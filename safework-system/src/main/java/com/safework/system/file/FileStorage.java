package com.safework.system.file;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件存储。现在用本地磁盘（{@link LocalFileStorage}，Docker 部署时挂载数据卷）；
 * 以后要换 MinIO / 阿里云 OSS，新写一个实现类即可，业务代码不用改。
 */
public interface FileStorage {

    /** 保存文件，key 形如 1/202610/3f2a….jpg */
    void save(String key, InputStream in) throws IOException;

    InputStream open(String key) throws IOException;

    void delete(String key) throws IOException;
}
