package com.wig.tryon.service;

import com.wig.tryon.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地文件存储（替代 MinIO/OSS）。文件落到 app.storage.dir，
 * 通过 /files/** 静态映射对外访问。
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final AppProperties appProperties;
    private final Path root;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public StorageService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.root = Paths.get(appProperties.getStorage().getDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建存储目录: " + this.root, e);
        }
    }

    public record Stored(String url, Path path) {
    }

    /** 保存上传文件，返回可访问 URL 与本地路径。 */
    public Stored save(MultipartFile file, String objectName) throws IOException {
        Path target = resolve(objectName);
        Files.createDirectories(target.getParent());
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Stored(toUrl(objectName), target);
    }

    /** 保存字节内容。 */
    public Stored saveBytes(byte[] bytes, String objectName) throws IOException {
        Path target = resolve(objectName);
        Files.createDirectories(target.getParent());
        Files.write(target, bytes);
        return new Stored(toUrl(objectName), target);
    }

    /** 从一个外部 URL 下载并保存（用于落地 AI 生成结果）。 */
    public Stored saveFromUrl(String sourceUrl, String objectName) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl)).GET().build();
        HttpResponse<byte[]> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() >= 300) {
            throw new IOException("下载结果图失败, status=" + resp.statusCode());
        }
        return saveBytes(resp.body(), objectName);
    }

    /** 复制一个已存在的本地文件为新对象（mock 模式生成结果时复用用户图）。 */
    public Stored copyLocal(Path source, String objectName) throws IOException {
        Path target = resolve(objectName);
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return new Stored(toUrl(objectName), target);
    }

    public byte[] readBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    public void delete(String objectName) {
        try {
            Files.deleteIfExists(resolve(objectName));
        } catch (IOException e) {
            log.warn("删除文件失败: {}", objectName, e);
        }
    }

    private Path resolve(String objectName) {
        Path target = root.resolve(objectName).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("非法的对象路径: " + objectName);
        }
        return target;
    }

    private String toUrl(String objectName) {
        String base = appProperties.getStorage().getPublicBaseUrl();
        return base.endsWith("/") ? base + objectName : base + "/" + objectName;
    }
}
