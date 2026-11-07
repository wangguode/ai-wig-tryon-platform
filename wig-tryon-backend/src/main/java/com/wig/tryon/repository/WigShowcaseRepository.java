package com.wig.tryon.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.model.WigShowcase;
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
 * 成品图仓库。结构与 WigAssetRepository 一致。
 * 这里只持久化到 dataDir，不放 classpath 种子——showcase 全部由运营生产/上传，没有出厂数据。
 */
@Repository
public class WigShowcaseRepository {

    private static final Logger log = LoggerFactory.getLogger(WigShowcaseRepository.class);

    private final ConcurrentHashMap<Long, WigShowcase> showcases = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Value("${app.storage.data-dir:./data}")
    private String dataDir;

    @PostConstruct
    public void init() {
        loadFromDataDir();
        log.info("[WigShowcaseRepository] 初始化完成，共 {} 条成品图", showcases.size());
    }

    public List<WigShowcase> findAll() {
        List<WigShowcase> list = new ArrayList<>(showcases.values());
        list.sort((a, b) -> Long.compare(b.id == null ? 0 : b.id, a.id == null ? 0 : a.id));
        return list;
    }

    public List<WigShowcase> findByWigAssetId(Long wigAssetId) {
        if (wigAssetId == null) return List.of();
        return findAll().stream().filter(s -> wigAssetId.equals(s.wigAssetId)).toList();
    }

    public Optional<WigShowcase> findById(Long id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(showcases.get(id));
    }

    public WigShowcase save(WigShowcase s) {
        if (s.id == null) {
            s.id = sequence.incrementAndGet();
        } else {
            sequence.updateAndGet(v -> Math.max(v, s.id));
        }
        if (s.createdAt == null) {
            s.createdAt = Instant.now();
        }
        showcases.put(s.id, s);
        persist();
        return s;
    }

    public boolean delete(Long id) {
        if (id == null) return false;
        WigShowcase removed = showcases.remove(id);
        if (removed != null) {
            persist();
            return true;
        }
        return false;
    }

    /** 演示重置：清空全部成品图（无 classpath 种子，所以不重新加载）。 */
    public synchronized void deleteAll() {
        int n = showcases.size();
        showcases.clear();
        sequence.set(0);
        persist();
        log.warn("[WigShowcaseRepository] 已清空全部成品图，共删除 {} 条", n);
    }

    /** 设置某 wigAssetId 下的指定 showcase 为唯一 primary（其它同 wigAssetId 的 isPrimary 置 false）。 */
    public WigShowcase markPrimary(Long showcaseId) {
        WigShowcase target = showcases.get(showcaseId);
        if (target == null) return null;
        Long wigAssetId = target.wigAssetId;
        for (WigShowcase s : showcases.values()) {
            if (wigAssetId != null && wigAssetId.equals(s.wigAssetId)) {
                s.isPrimary = s.id.equals(showcaseId);
            }
        }
        persist();
        return target;
    }

    /** 该 wigAsset 是否已有 APPROVED 主图。 */
    public boolean hasApprovedPrimary(Long wigAssetId) {
        if (wigAssetId == null) return false;
        return findByWigAssetId(wigAssetId).stream()
                .anyMatch(s -> s.isPrimary && "APPROVED".equals(s.status));
    }

    /** 读取某 wigAssetId 的 primary showcase；没有显式 primary 时返回 null（不再隐式回退）。 */
    public Optional<WigShowcase> findPrimaryByWigAssetId(Long wigAssetId) {
        List<WigShowcase> list = findByWigAssetId(wigAssetId);
        return list.stream()
                .filter(s -> s.isPrimary && "APPROVED".equals(s.status))
                .findFirst();
    }

    /** 热加载 data/wig-showcases.json。 */
    public synchronized void reloadFromDisk() {
        showcases.clear();
        sequence.set(0);
        loadFromDataDir();
        log.info("[WigShowcaseRepository] 热加载完成，共 {} 条成品图", showcases.size());
    }

    private void loadFromDataDir() {
        Path file = dataFile();
        if (!Files.exists(file)) return;
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) return;
            List<WigShowcase> list = objectMapper.readValue(bytes, new TypeReference<List<WigShowcase>>() {});
            for (WigShowcase s : list) {
                if (s.id != null) {
                    showcases.put(s.id, s);
                    sequence.updateAndGet(v -> Math.max(v, s.id));
                }
            }
        } catch (IOException e) {
            log.warn("[WigShowcaseRepository] 加载持久化文件失败：{}", e.getMessage());
        }
    }

    private synchronized void persist() {
        try {
            Path dir = Paths.get(dataDir);
            Files.createDirectories(dir);
            byte[] json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(findAll());
            Files.write(dataFile(), json);
        } catch (IOException e) {
            log.error("[WigShowcaseRepository] 持久化失败", e);
            throw new RuntimeException("成品图持久化失败", e);
        }
    }

    private Path dataFile() {
        return Paths.get(dataDir, "wig-showcases.json");
    }
}
