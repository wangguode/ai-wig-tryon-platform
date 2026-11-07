package com.wig.tryon.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.model.WigAsset;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 假发本体仓库。MVP 用 JSON 文件持久化，避开 DB 依赖以保持"演示零外部依赖"的项目风格。
 * 启动时按以下顺序加载：
 *   1) data 目录的 wig-assets.json（运行时持久层，运营上传后写入这里）
 *   2) classpath:data/wig-assets.json（首次启动种子，仅在 1 不存在时使用）
 * 写操作总是落到 1。读操作合并 1 + 2，1 优先。
 */
@Repository
public class WigAssetRepository {

    private static final Logger log = LoggerFactory.getLogger(WigAssetRepository.class);

    private final ConcurrentHashMap<Long, WigAsset> assets = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    /**
     * 运行时持久化目录。启动用户可通过 app.storage.data-dir 覆盖到任意可写目录。
     * 默认 ./data，与本机演示一致。
     */
    @Value("${app.storage.data-dir:./data}")
    private String dataDir;

    @PostConstruct
    public void init() {
        if (Files.exists(dataFile())) {
            loadFromDataDirOnly();
        } else {
            loadFromClasspath();
        }
        log.info("[WigAssetRepository] 初始化完成，共 {} 条假发本体", assets.size());
    }

    public List<WigAsset> findAll() {
        List<WigAsset> list = new ArrayList<>(assets.values());
        list.sort((a, b) -> Long.compare(a.id == null ? 0 : a.id, b.id == null ? 0 : b.id));
        return list;
    }

    public Optional<WigAsset> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(assets.get(id));
    }

    /**
     * 保存或更新。id 为空则分配新 id。落盘失败抛异常，调用方决定是否回滚。
     */
    public WigAsset save(WigAsset asset) {
        if (asset.id == null) {
            asset.id = sequence.incrementAndGet();
        } else {
            sequence.updateAndGet(v -> Math.max(v, asset.id));
        }
        if (asset.createdAt == null) {
            asset.createdAt = Instant.now();
        }
        assets.put(asset.id, asset);
        persist();
        return asset;
    }

    public boolean delete(Long id) {
        if (id == null) return false;
        WigAsset removed = assets.remove(id);
        if (removed != null) {
            persist();
            return true;
        }
        return false;
    }

    /** 演示重置：清空全部本体（写空数组到磁盘，不再回退 classpath 种子）。 */
    public synchronized void clearAll() {
        assets.clear();
        sequence.set(0);
        persist();
        log.warn("[WigAssetRepository] 已清空全部假发本体");
    }

    /**
     * 演示模式专用：从 classpath 种子重新加载（旧行为，一般不再使用）。
     */
    public synchronized void resetToSeed() {
        assets.clear();
        sequence.set(0);
        loadFromClasspath();
        persist();
        log.warn("[WigAssetRepository] 已重置为 classpath 种子数据，共 {} 条", assets.size());
    }

    /** 种子加载：仅在数据目录文件不存在时使用，避免覆盖运营修改。 */
    private void loadFromClasspath() {
        try {
            var resource = getClass().getClassLoader().getResourceAsStream("data/wig-assets.json");
            if (resource == null) return;
            List<WigAsset> seeds = objectMapper.readValue(resource, new TypeReference<List<WigAsset>>() {});
            for (WigAsset a : seeds) {
                if (a.id != null) {
                    assets.putIfAbsent(a.id, a);
                    sequence.updateAndGet(v -> Math.max(v, a.id));
                }
            }
        } catch (Exception e) {
            log.warn("[WigAssetRepository] 加载 classpath 种子失败：{}", e.getMessage());
        }
    }

    /** 热加载：data 目录持久化文件覆盖内存（客户批量导入后无需重启 JVM）。 */
    public synchronized void reloadFromDisk() {
        assets.clear();
        sequence.set(0);
        if (Files.exists(dataFile())) {
            loadFromDataDirOnly();
        } else {
            loadFromClasspath();
        }
        log.info("[WigAssetRepository] 热加载完成，共 {} 条假发本体", assets.size());
    }

    /** 仅读 data 目录；文件存在且为 [] 时保持空库，不合并 classpath。 */
    private void loadFromDataDirOnly() {
        Path file = dataFile();
        if (!Files.exists(file)) return;
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) return;
            List<WigAsset> list = objectMapper.readValue(bytes, new TypeReference<List<WigAsset>>() {});
            for (WigAsset a : list) {
                if (a.id != null) {
                    assets.put(a.id, a);
                    sequence.updateAndGet(v -> Math.max(v, a.id));
                }
            }
        } catch (IOException e) {
            log.warn("[WigAssetRepository] 加载持久化文件失败：{}", e.getMessage());
        }
    }

    private void loadFromDataDir() {
        loadFromDataDirOnly();
    }

    private synchronized void persist() {
        try {
            Path dir = Paths.get(dataDir);
            Files.createDirectories(dir);
            Path file = dataFile();
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(findAll());
            Files.write(file, json);
        } catch (IOException e) {
            log.error("[WigAssetRepository] 持久化失败", e);
            throw new RuntimeException("假发资产持久化失败", e);
        }
    }

    private Path dataFile() {
        return Paths.get(dataDir, "wig-assets.json");
    }
}
