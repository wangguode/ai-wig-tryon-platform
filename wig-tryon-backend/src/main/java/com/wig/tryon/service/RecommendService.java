package com.wig.tryon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.config.AppProperties;
import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.WigProduct;
import com.wig.tryon.model.WigShowcase;
import com.wig.tryon.repository.CatalogRepository;
import com.wig.tryon.repository.SessionRepository;
import com.wig.tryon.repository.WigShowcaseRepository;
import com.wig.tryon.service.ai.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI 分析 + 个性化推荐。
 */
@Service
public class RecommendService {

    private static final Logger log = LoggerFactory.getLogger(RecommendService.class);

    private final AiService aiService;
    private final AppProperties appProperties;
    private final CatalogRepository catalogRepository;
    private final SessionRepository sessionRepository;
    private final WigShowcaseRepository showcaseRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecommendService(AiService aiService, AppProperties appProperties,
                            CatalogRepository catalogRepository,
                            SessionRepository sessionRepository,
                            WigShowcaseRepository showcaseRepository) {
        this.aiService = aiService;
        this.appProperties = appProperties;
        this.catalogRepository = catalogRepository;
        this.sessionRepository = sessionRepository;
        this.showcaseRepository = showcaseRepository;
    }

    /**
     * 给 C 端用户看的产品视图：把 image / seedResultImage 字段替换成
     * 该 wigAssetId 关联的 primary showcase。资产分层架构关键透明替换点：
     * 前端字段层面无感，但实际看到的"商品图"已经是运营审核过的"模特戴假发图"。
     * 没有 primary showcase 时（比如 P0 阶段刚上线）保留老 image 字段，演示零阻塞。
     */
    private WigProduct enrichWithShowcase(WigProduct p) {
        if (p == null || p.wigAssetId == null) return p;
        List<WigShowcase> all = showcaseRepository.findByWigAssetId(p.wigAssetId).stream()
                .filter(s -> "APPROVED".equals(s.status) && s.resultImage != null && !s.resultImage.isBlank())
                .toList();
        WigShowcase primary = all.stream()
                .filter(s -> s.isPrimary && "APPROVED".equals(s.status))
                .findFirst()
                .orElse(null);
        WigProduct copy = objectMapper.convertValue(p, WigProduct.class);
        if (primary != null) {
            copy.image = toRelativeFilesUrl(primary.resultImage);
            copy.seedResultImage = copy.image;
            copy.primaryShowcaseId = primary.id;
        }
        // 非主图款式素材，供合成页画廊；主图仅作列表封面
        copy.galleryImages = all.stream()
                .filter(s -> !s.isPrimary)
                .map(s -> toRelativeFilesUrl(s.resultImage))
                .distinct()
                .limit(15)
                .toList();
        return copy;
    }

    /** C 端走 Vite /files 代理或同域静态资源，避免写死 localhost:8080 导致手机/LAN 缩略图裂图。 */
    private static String toRelativeFilesUrl(String url) {
        if (url == null || url.isBlank()) return url;
        int idx = url.indexOf("/files/");
        if (idx >= 0) {
            return url.substring(idx);
        }
        return url;
    }

    private List<WigProduct> enrichAll(List<WigProduct> list) {
        if (list == null) return null;
        return list.stream().map(this::enrichWithShowcase).collect(Collectors.toList());
    }

    public Map<String, Object> analyzeAndRecommend(String sessionId, String preference, String category) {
        SessionRepository.Session session = sessionRepository.getSession(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话已过期，请重新上传照片");
        }
        if (PhotoService.FACE_FAILED.equals(session.faceCheckStatus)) {
            throw new IllegalArgumentException(session.faceCheckMessage != null
                    ? session.faceCheckMessage
                    : "未检测到清晰的正面人脸，请重新拍摄");
        }
        if (PhotoService.FACE_PENDING.equals(session.faceCheckStatus)) {
            throw new IllegalArgumentException("人脸检测进行中，请稍候");
        }

        List<WigProduct> catalog = catalogRepository.findAll();
        if (appProperties.getAi().isDemoFastRecommend()) {
            return demoFastRecommend(sessionId, catalog, category);
        }

        byte[] imageBytes = readBytes(session.photoPath);

        FaceAnalysisResult analysis = aiService.analyze(imageBytes, "image/jpeg", preference, catalog);

        // 两阶段推荐：AI 只负责"读脸"，最终选款由 Java 规则在全库打分决定（融合 AI 视觉信号）。
        // 好处：推荐稳定、可解释、可 A/B，不会因模型一次 JSON 抖动而乱选款。
        Set<Long> vlRecommendedIds = new LinkedHashSet<>();
        if (analysis.recommendedIds != null) {
            for (Long id : analysis.recommendedIds) {
                if (id != null) {
                    vlRecommendedIds.add(id);
                }
            }
        }
        List<Map<String, Object>> recommendationMatches = rankCatalog(catalog, analysis, preference, vlRecommendedIds);
        List<Long> topIds = recommendationMatches.stream()
                .map(m -> (Long) m.get("wigProductId"))
                .collect(Collectors.toList());
        List<WigProduct> recommended = resolveRecommended(topIds, catalog);
        // 同步最终推荐 ID 给前端（AI推荐徽标 / 排序），保证展示与规则结果一致
        analysis.recommendedIds = recommended.stream().map(p -> p.id).collect(Collectors.toList());
        sessionRepository.saveAnalysis(sessionId, analysis);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("faceAnalysis", analysis);
        result.put("recommendedProducts", enrichAll(recommended));
        result.put("recommendationMatches", recommendationMatches);
        result.put("recommendationStepOnly", true);
        result.put("nextStepHint", "请选择想试戴的假发，再提交合成任务");
        result.put("mock", aiService.isMock());
        result.put("aiMode", aiService.modeName());
        return result;
    }

    public List<WigProduct> getProducts(String category, String faceType) {
        return enrichAll(catalogRepository.find(category, faceType));
    }

    private List<WigProduct> resolveRecommended(List<Long> ids, List<WigProduct> catalog) {
        Set<Long> selected = new LinkedHashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    selected.add(id);
                }
            }
        }

        List<WigProduct> list = new ArrayList<>();
        for (Long id : selected) {
            catalog.stream().filter(p -> p.id.equals(id)).findFirst().ifPresent(list::add);
            if (list.size() >= 6) {
                return list;
            }
        }

        List<WigProduct> fallback = new ArrayList<>(catalog);
        fallback.sort(Comparator
                .comparing((WigProduct p) -> p.isHot).reversed()
                .thenComparing((WigProduct p) -> p.rating, Comparator.reverseOrder())
                .thenComparing((WigProduct p) -> p.reviews, Comparator.reverseOrder()));
        for (WigProduct p : fallback) {
            if (list.stream().noneMatch(x -> x.id.equals(p.id))) {
                list.add(p);
            }
            if (list.size() >= 6) {
                break;
            }
        }
        return list;
    }

    /**
     * 全库规则打分排序，取 Top 6。融合 AI 视觉信号（vlRecommendedIds 命中加分），
     * 这是"两阶段推荐"的第二阶段：让选款稳定、可解释。
     */
    private List<Map<String, Object>> rankCatalog(
            List<WigProduct> catalog, FaceAnalysisResult analysis, String preference, Set<Long> vlRecommendedIds) {
        if (catalog == null || catalog.isEmpty()) {
            return List.of();
        }
        return catalog.stream()
                .map(p -> toMatchCard(p, analysis, preference, vlRecommendedIds.contains(p.id)))
                .sorted(Comparator.comparingInt(m -> -((Integer) m.get("matchScore"))))
                .limit(6)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMatchCard(WigProduct product, FaceAnalysisResult analysis,
                                            String preference, boolean vlRecommended) {
        int score = 45;
        List<String> reasons = new ArrayList<>();
        String pref = preference == null ? "" : preference;

        if (analysis != null && analysis.faceShape != null
                && product.faceTypes != null && product.faceTypes.contains(analysis.faceShape)) {
            score += 20;
            reasons.add("脸型匹配：" + analysis.faceShape);
        }
        if (!pref.isBlank()) {
            if (containsAny(product.tags, pref)) {
                score += 10;
                reasons.add("风格偏好命中：" + hitTags(product.tags, pref));
            }
            if (containsAny(product.sceneTags, pref)) {
                score += 8;
                reasons.add("场景偏好命中：" + hitTags(product.sceneTags, pref));
            }
            if (containsAny(product.targetCrowds, pref)) {
                score += 7;
                reasons.add("人群偏好命中：" + hitTags(product.targetCrowds, pref));
            }
        }
        if (analysis != null && Boolean.TRUE.equals(analysis.hasWhiteHair) && product.whiteHairCoverage >= 4) {
            score += 10;
            reasons.add("白发遮盖能力强");
        }
        if (analysis != null && analysis.hairlineType != null
                && (analysis.hairlineType.contains("M型") || analysis.hairlineType.contains("后移"))
                && product.hairlineCoverage >= 4) {
            score += 10;
            reasons.add("发际线修饰能力强");
        }
        if (product.rating >= 4.8) {
            score += 5;
            reasons.add("口碑评分高");
        }
        if (product.storeIds != null && !product.storeIds.isEmpty()) {
            score += 3;
            reasons.add("线下门店可试戴");
        }
        if (vlRecommended) {
            // AI 视觉分析也点名了这款：作为加分信号，而非唯一决定因素
            score += 12;
            reasons.add("AI 视觉分析也推荐了这款");
        }
        score = Math.min(score, 100);
        if (reasons.isEmpty()) {
            reasons.add("综合风格与场景适配度较高");
        }

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("wigProductId", product.id);
        card.put("name", product.name);
        card.put("matchScore", score);
        card.put("matchReasons", reasons);
        card.put("colorFamily", product.colorFamily);
        card.put("material", product.material);
        card.put("sceneTags", product.sceneTags);
        card.put("targetCrowds", product.targetCrowds);
        card.put("storeIds", product.storeIds);
        card.put("hairlineCoverage", product.hairlineCoverage);
        card.put("whiteHairCoverage", product.whiteHairCoverage);
        return card;
    }

    private boolean containsAny(List<String> tags, String preference) {
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        return tags.stream().anyMatch(preference::contains);
    }

    private String hitTags(List<String> tags, String preference) {
        if (tags == null || tags.isEmpty()) {
            return "风格";
        }
        return tags.stream().filter(preference::contains).limit(2).collect(Collectors.joining("、"));
    }

    /**
     * 演示快速推荐：不调 VL 模型，按当前分类取库内前 2 款，文案固定。
     * 切换 Tab 时前端带 category 重新请求即可刷新推荐。
     */
    private Map<String, Object> demoFastRecommend(String sessionId, List<WigProduct> catalog, String category) {
        String catLabel = (category == null || category.isBlank() || "全部".equals(category)) ? "全部分类" : category;
        List<WigProduct> pool = catalog.stream()
                .filter(p -> category == null || category.isBlank() || "全部".equals(category)
                        || category.equals(p.category))
                .sorted(Comparator
                        .comparing((WigProduct p) -> Boolean.TRUE.equals(p.isHot)).reversed()
                        .thenComparing((WigProduct p) -> p.rating, Comparator.reverseOrder())
                        .thenComparing(p -> p.id))
                .limit(2)
                .collect(Collectors.toList());
        if (pool.isEmpty()) {
            pool = catalog.stream().limit(2).collect(Collectors.toList());
        }

        FaceAnalysisResult analysis = new FaceAnalysisResult();
        analysis.faceShape = "鹅蛋脸";
        analysis.hairlineType = "正常";
        analysis.skinTone = "自然偏暖";
        analysis.currentHairDesc = "发量适中，便于与假发款式做直观对比";
        analysis.hasWhiteHair = false;
        analysis.suggestions = List.of(
                "鹅蛋脸适合略带层次的中长发，能拉长视觉比例",
                "若日常通勤，建议优先尝试自然色系、易打理款式",
                "试戴时可对比刘海长度，找到最修饰发际线的一版");
        analysis.recommendedIds = pool.stream().map(p -> p.id).collect(Collectors.toList());
        String names = pool.stream().map(p -> "「" + p.name + "」").collect(Collectors.joining("、"));
        analysis.analysisText = String.format(
                "您好！已根据您上传的照片完成初步形象分析：脸型偏鹅蛋脸，三庭比例较为均衡，"
                        + "肤色自然偏暖，整体气质大方亲和。结合%s素材库中的门店热销数据，"
                        + "我们为您优先挑选了 %s 两款——兼顾修饰脸型与日常佩戴的真实感。"
                        + "您可直接点选试戴，也可以在下方浏览更多款式。",
                catLabel, names);

        List<Map<String, Object>> recommendationMatches = new ArrayList<>();
        int score = 96;
        for (WigProduct p : pool) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("wigProductId", p.id);
            card.put("name", p.name);
            card.put("matchScore", score);
            card.put("matchReasons", List.of(
                    catLabel + "门店试戴热度较高",
                    score >= 95 ? "刘海与发际线修饰友好" : "发色与日常通勤场景适配"));
            card.put("colorFamily", p.colorFamily);
            card.put("material", p.material);
            card.put("sceneTags", p.sceneTags);
            card.put("targetCrowds", p.targetCrowds);
            card.put("storeIds", p.storeIds);
            card.put("hairlineCoverage", p.hairlineCoverage);
            card.put("whiteHairCoverage", p.whiteHairCoverage);
            recommendationMatches.add(card);
            score -= 2;
        }

        sessionRepository.saveAnalysis(sessionId, analysis);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("faceAnalysis", analysis);
        result.put("recommendedProducts", enrichAll(pool));
        result.put("recommendationMatches", recommendationMatches);
        result.put("recommendationStepOnly", true);
        result.put("demoFastRecommend", true);
        result.put("nextStepHint", "点击下方推荐款或浏览素材库，选中后即可一键 AI 试戴");
        result.put("mock", false);
        result.put("aiMode", "demo-fast");
        log.info("[Recommend] 演示快速推荐 category={} ids={}", catLabel, analysis.recommendedIds);
        return result;
    }

    private byte[] readBytes(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            log.warn("读取照片字节失败: {}", path, e);
            return new byte[0];
        }
    }
}
