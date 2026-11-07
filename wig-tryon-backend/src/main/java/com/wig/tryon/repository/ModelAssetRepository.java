package com.wig.tryon.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.model.ModelAsset;
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
 * 模特库仓库。与 {@link WigAssetRepository} 同构：classpath 种子 + dataDir 持久化。
 */
@Repository
public class ModelAssetRepository {

    private static final Logger log = LoggerFactory.getLogger(ModelAssetRepository.class);

    private final ConcurrentHashMap<Long, ModelAsset> models = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Value("${app.storage.data-dir:./data}")
    private String dataDir;

    @PostConstruct
    public void init() {
        if (Files.exists(dataFile())) {
            loadFromDataDirOnly();
        } else {
            loadFromClasspath();
        }
        log.info("[ModelAssetRepository] 初始化完成，共 {} 条模特", models.size());
    }

    public List<ModelAsset> findAll() {
        List<ModelAsset> list = new ArrayList<>(models.values());
        list.sort((a, b) -> Long.compare(a.id == null ? 0 : a.id, b.id == null ? 0 : b.id));
        return list;
    }

    public Optional<ModelAsset> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(models.get(id));
    }

    public ModelAsset save(ModelAsset m) {
        if (m.id == null) {
            m.id = sequence.incrementAndGet();
        } else {
            sequence.updateAndGet(v -> Math.max(v, m.id));
        }
        if (m.createdAt == null) {
            m.createdAt = Instant.now();
        }
        models.put(m.id, m);
        persist();
        return m;
    }

    public boolean delete(Long id) {
        if (id == null) return false;
        ModelAsset removed = models.remove(id);
        if (removed != null) {
            persist();
            return true;
        }
        return false;
    }

    /** 演示重置：清空全部模特。 */
    public synchronized void clearAll() {
        models.clear();
        sequence.set(0);
        persist();
        log.warn("[ModelAssetRepository] 已清空全部模特");
    }

    /** 旧行为：回退 classpath 种子。 */
    public synchronized void resetToSeed() {
        models.clear();
        sequence.set(0);
        loadFromClasspath();
        persist();
        log.warn("[ModelAssetRepository] 已重置为 classpath 种子数据，共 {} 条", models.size());
    }

    private void loadFromClasspath() {
        try {
            var resource = getClass().getClassLoader().getResourceAsStream("data/model-assets.json");
            if (resource == null) return;
            List<ModelAsset> seeds = objectMapper.readValue(resource, new TypeReference<List<ModelAsset>>() {});
            for (ModelAsset m : seeds) {
                if (m.id != null) {
                    models.putIfAbsent(m.id, m);
                    sequence.updateAndGet(v -> Math.max(v, m.id));
                }
            }
        } catch (Exception e) {
            log.warn("[ModelAssetRepository] 加载 classpath 种子失败：{}", e.getMessage());
        }
    }

    /** 热加载 data/model-assets.json。 */
    public synchronized void reloadFromDisk() {
        models.clear();
        sequence.set(0);
        if (Files.exists(dataFile())) {
            loadFromDataDirOnly();
        } else {
            loadFromClasspath();
        }
        log.info("[ModelAssetRepository] 热加载完成，共 {} 条模特", models.size());
    }

    private void loadFromDataDirOnly() {
        Path file = dataFile();
        if (!Files.exists(file)) return;
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) return;
            List<ModelAsset> list = objectMapper.readValue(bytes, new TypeReference<List<ModelAsset>>() {});
            for (ModelAsset m : list) {
                if (m.id != null) {
                    models.put(m.id, m);
                    sequence.updateAndGet(v -> Math.max(v, m.id));
                }
            }
        } catch (IOException e) {
            log.warn("[ModelAssetRepository] 加载持久化文件失败：{}", e.getMessage());
        }
    }

    private void loadFromDataDir() {
        loadFromDataDirOnly();
    }

    private synchronized void persist() {
        try {
            Path dir = Paths.get(dataDir);
            Files.createDirectories(dir);
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(findAll());
            Files.write(dataFile(), json);
        } catch (IOException e) {
            log.error("[ModelAssetRepository] 持久化失败", e);
            throw new RuntimeException("模特库持久化失败", e);
        }
    }

    private Path dataFile() {
        return Paths.get(dataDir, "model-assets.json");
    }
}
