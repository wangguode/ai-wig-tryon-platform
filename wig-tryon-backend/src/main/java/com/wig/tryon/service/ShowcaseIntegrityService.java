package com.wig.tryon.service;

import com.wig.tryon.model.WigAsset;
import com.wig.tryon.model.WigProduct;
import com.wig.tryon.model.WigShowcase;
import com.wig.tryon.repository.CatalogRepository;
import com.wig.tryon.repository.WigAssetRepository;
import com.wig.tryon.repository.WigShowcaseRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 检查某款本体的商品 / 陈列 / 本体 URL 关联是否完整。 */
@Service
public class ShowcaseIntegrityService {

    private final WigAssetRepository wigAssetRepository;
    private final WigShowcaseRepository showcaseRepository;
    private final CatalogRepository catalogRepository;

    public ShowcaseIntegrityService(WigAssetRepository wigAssetRepository,
                                    WigShowcaseRepository showcaseRepository,
                                    CatalogRepository catalogRepository) {
        this.wigAssetRepository = wigAssetRepository;
        this.showcaseRepository = showcaseRepository;
        this.catalogRepository = catalogRepository;
    }

    public Map<String, Object> check(Long wigAssetId) {
        WigAsset asset = wigAssetRepository.findById(wigAssetId)
                .orElseThrow(() -> new IllegalArgumentException("假发本体不存在: " + wigAssetId));

        List<WigShowcase> all = showcaseRepository.findByWigAssetId(wigAssetId);
        List<WigShowcase> approved = all.stream().filter(s -> "APPROVED".equals(s.status)).toList();
        boolean hasPrimary = all.stream().anyMatch(s -> s.isPrimary && "APPROVED".equals(s.status));
        long missingModel = all.stream().filter(s -> s.modelAssetId == null).count();
        boolean bareEqualsShowcase = all.stream()
                .anyMatch(s -> Objects.equals(asset.productImage, s.resultImage));

        WigProduct product = catalogRepository.findProduct(wigAssetId).orElse(null);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("wigAssetId", wigAssetId);
        out.put("assetName", asset.name);
        out.put("bareImageUrl", asset.productImage);
        out.put("hasProduct", product != null);
        out.put("productName", product != null ? product.name : null);
        out.put("showcaseCount", all.size());
        out.put("approvedCount", approved.size());
        out.put("hasPrimary", hasPrimary);
        out.put("bareEqualsShowcase", bareEqualsShowcase);
        out.put("missingModelAssetIdCount", missingModel);
        out.put("ok", product != null && !approved.isEmpty() && hasPrimary && !bareEqualsShowcase);
        return out;
    }
}
