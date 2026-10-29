package com.safework.system.file;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 本地磁盘存储 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(FileProperties props) {
        this.root = Path.of(props.getLocalPath()).toAbsolutePath().normalize();
    }

    @Override
    public void save(String key, InputStream in) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream open(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    /** 防止路径穿越：key 里如果有 ../ 跳出存储目录，直接拒绝 */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("非法的文件路径");
        }
        return p;
    }
}
