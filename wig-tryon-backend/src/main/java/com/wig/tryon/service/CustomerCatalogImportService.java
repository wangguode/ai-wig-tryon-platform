package com.wig.tryon.service;

import com.wig.tryon.repository.CatalogRepository;
import com.wig.tryon.repository.ModelAssetRepository;
import com.wig.tryon.repository.WigAssetRepository;
import com.wig.tryon.repository.WigShowcaseRepository;
import com.wig.tryon.util.CustomerCatalogRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 客户素材文件夹预览 + 一键导入（调用已验证 Python v3 脚本，不调 AI）。
 */
@Service
public class CustomerCatalogImportService {

    private static final Logger log = LoggerFactory.getLogger(CustomerCatalogImportService.class);
    private static final String DEFAULT_FOLDER = "发型素材-ai测试用-刘经理提供";

    private final WigAssetRepository wigAssetRepository;
    private final WigShowcaseRepository showcaseRepository;
    private final ModelAssetRepository modelAssetRepository;
    private final CatalogRepository catalogRepository;

    public CustomerCatalogImportService(WigAssetRepository wigAssetRepository,
                                        WigShowcaseRepository showcaseRepository,
                                        ModelAssetRepository modelAssetRepository,
                                        CatalogRepository catalogRepository) {
        this.wigAssetRepository = wigAssetRepository;
        this.showcaseRepository = showcaseRepository;
        this.modelAssetRepository = modelAssetRepository;
        this.catalogRepository = catalogRepository;
    }

    public Map<String, Object> preview(String sourcePath) throws Exception {
        Path root = resolveSourcePath(sourcePath);
        List<CustomerCatalogRules.StylePreview> vStyles = CustomerCatalogRules.scanVFolders(root);
        List<Map<String, Object>> styles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (CustomerCatalogRules.StylePreview s : vStyles) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("folderName", s.folderName());
            row.put("code", s.code());
            row.put("bareFile", s.bareFile());
            row.put("showcaseCount", s.showcaseCount());
            styles.add(row);
            warnings.addAll(s.warnings().stream().map(w -> s.code() + ": " + w).toList());
        }
        Path designer = root.resolve("设计师款");
        Path art = root.resolve("Art系列");
        if (Files.isDirectory(designer)) {
            styles.add(chunkSummary("设计师款", designer, 12));
        }
        if (Files.isDirectory(art)) {
            styles.add(chunkSummary("Art系列", art, 11));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourcePath", root.toString());
        out.put("vStyleCount", vStyles.size());
        out.put("styles", styles);
        out.put("estimatedStyleCount", vStyles.size() + (Files.isDirectory(designer) ? 8 : 0) + (Files.isDirectory(art) ? 4 : 0));
        out.put("warnings", warnings);
        return out;
    }

    private Map<String, Object> chunkSummary(String label, Path dir, int chunkSize) throws Exception {
        int imgs = CustomerCatalogRules.listImageNames(dir).size();
        int chunks = imgs / chunkSize;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("folderName", label);
        row.put("code", label);
        row.put("bareFile", "静物池/0F6A");
        row.put("showcaseCount", chunkSize);
        row.put("chunkCount", chunks);
        return row;
    }

    public Map<String, Object> execute(String sourcePath) throws Exception {
        Path root = resolveSourcePath(sourcePath);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("素材目录不存在: " + root);
        }

        Path backendRoot = Paths.get("").toAbsolutePath().normalize();
        Path script = backendRoot.resolve("scripts/batch-import-customer-catalog.py");
        if (!Files.exists(script)) {
            throw new IllegalStateException("导入脚本不存在: " + script);
        }

        ProcessBuilder pb = new ProcessBuilder("python3", script.toString());
        pb.directory(backendRoot.toFile());
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder output = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                log.info("[import] {}", line);
            }
        }
        boolean finished = proc.waitFor(120, TimeUnit.SECONDS);
        if (!finished) {
            proc.destroyForcibly();
            throw new IllegalStateException("导入超时（120s）");
        }
        if (proc.exitValue() != 0) {
            throw new IllegalStateException("导入脚本失败 exit=" + proc.exitValue() + "\n" + output);
        }

        reloadAllFromDisk();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourcePath", root.toString());
        out.put("wigAssetCount", wigAssetRepository.findAll().size());
        out.put("showcaseCount", showcaseRepository.findAll().size());
        out.put("modelCount", modelAssetRepository.findAll().size());
        out.put("productCount", catalogRepository.findAll().size());
        out.put("log", output.toString().trim());
        return out;
    }

    public void reloadAllFromDisk() {
        wigAssetRepository.reloadFromDisk();
        modelAssetRepository.reloadFromDisk();
        showcaseRepository.reloadFromDisk();
        catalogRepository.reloadFromDisk();
    }

    public Path resolveSourcePath(String sourcePath) {
        if (sourcePath != null && !sourcePath.isBlank()) {
            return Paths.get(sourcePath.trim()).toAbsolutePath().normalize();
        }
        Path backendRoot = Paths.get("").toAbsolutePath().normalize();
        return backendRoot.getParent().resolve(DEFAULT_FOLDER).normalize();
    }
}
