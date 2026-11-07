package com.wig.tryon.service;

import com.wig.tryon.model.WigShowcase;
import com.wig.tryon.repository.WigAssetRepository;
import com.wig.tryon.repository.WigShowcaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 手工导入客户现成成品图（不调 AI）。
 */
@Service
public class ShowcaseImportService {

    private static final int MAX_FILES = 15;
    private static final List<String> ALLOWED_MIME = List.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final WigAssetRepository wigAssetRepository;
    private final WigShowcaseRepository showcaseRepository;
    private final StorageService storageService;

    public ShowcaseImportService(WigAssetRepository wigAssetRepository,
                                 WigShowcaseRepository showcaseRepository,
                                 StorageService storageService) {
        this.wigAssetRepository = wigAssetRepository;
        this.showcaseRepository = showcaseRepository;
        this.storageService = storageService;
    }

    public List<WigShowcase> importFiles(Long wigAssetId,
                                         List<MultipartFile> files,
                                         List<Long> modelAssetIds,
                                         boolean autoApprove,
                                         boolean setFirstPrimary) throws IOException {
        wigAssetRepository.findById(wigAssetId)
                .orElseThrow(() -> new IllegalArgumentException("假发本体不存在: " + wigAssetId));
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("至少上传 1 张成品图");
        }
        if (files.size() > MAX_FILES) {
            throw new IllegalArgumentException("最多上传 " + MAX_FILES + " 张");
        }

        List<WigShowcase> created = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个文件为空");
            }
            String contentType = file.getContentType();
            if (contentType == null || ALLOWED_MIME.stream().noneMatch(contentType::startsWith)) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个文件格式不支持，仅 JPG/PNG/WEBP/GIF");
            }
            String ext = extensionOf(file.getOriginalFilename());
            String objectName = "showcases/" + wigAssetId + "-import-" + UUID.randomUUID().toString().replace("-", "") + ext;
            StorageService.Stored stored = storageService.save(file, objectName);

            WigShowcase s = new WigShowcase();
            s.wigAssetId = wigAssetId;
            if (modelAssetIds != null && i < modelAssetIds.size()) {
                s.modelAssetId = modelAssetIds.get(i);
            }
            s.resultImage = stored.url();
            s.status = autoApprove ? "APPROVED" : "DRAFT";
            s.strategyUsed = "customer-manual-import";
            s.prompt = "客户现成素材手工导入";
            s.createdAt = Instant.now();
            if (autoApprove) {
                s.reviewedAt = Instant.now();
                s.reviewedBy = "admin-import";
            }
            created.add(showcaseRepository.save(s));
        }

        if (setFirstPrimary && !created.isEmpty()
                && !showcaseRepository.hasApprovedPrimary(wigAssetId)) {
            showcaseRepository.markPrimary(created.get(0).id);
            return showcaseRepository.findByWigAssetId(wigAssetId);
        }
        return created;
    }

    private static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }
}
