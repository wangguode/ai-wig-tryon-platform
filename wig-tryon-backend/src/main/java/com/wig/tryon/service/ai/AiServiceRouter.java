package com.wig.tryon.service.ai;

import com.wig.tryon.config.AppProperties;
import com.wig.tryon.config.DashScopeProperties;
import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.WigProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * AI 路由：根据配置选择 real/seed/mock。
 * - strict-real=false（默认）：真实调用失败时降级 seed，保证演示稳定。
 * - strict-real=true：真实调用失败直接抛错，便于联调定位问题。
 */
@Primary
@Service
public class AiServiceRouter implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceRouter.class);

    private final AppProperties appProperties;
    private final DashScopeProperties dashScopeProperties;
    private final DashScopeAiService dashScope;
    private final SeedAiService seed;
    private final MockAiService mock;
    private final YunwuOpenAiAiService yunwu;
    private final YunwuFluxKontextAiService yunwuFlux;
    private final VolcengineArkAiService volcengineArk;

    public AiServiceRouter(AppProperties appProperties, DashScopeProperties dashScopeProperties,
                           DashScopeAiService dashScope, SeedAiService seed, MockAiService mock,
                           YunwuOpenAiAiService yunwu, YunwuFluxKontextAiService yunwuFlux,
                           VolcengineArkAiService volcengineArk) {
        this.appProperties = appProperties;
        this.dashScopeProperties = dashScopeProperties;
        this.dashScope = dashScope;
        this.seed = seed;
        this.mock = mock;
        this.yunwu = yunwu;
        this.yunwuFlux = yunwuFlux;
        this.volcengineArk = volcengineArk;
    }

    private boolean useReal() {
        return "real".equals(appProperties.getAi().resolvedMode()) && dashScopeProperties.hasApiKey();
    }

    /**
     * 试戴是否能真实出图：tryonProvider=yunwu/flux 且对应 key 在时也算"real"，
     * 这样前端 effectiveMode 不会跟 strategyUsed=yunwu|chat|... 自相矛盾。
     */
    private boolean tryonReady() {
        if (!"real".equals(appProperties.getAi().resolvedMode())) return false;
        String provider = resolveTryonProvider();
        if ("yunwu".equals(provider)) return yunwu.hasApiKey();
        // flux 用的是同一个云雾 key（YunwuProperties），key 检查复用 yunwu.hasApiKey()
        if ("flux".equals(provider)) return yunwuFlux.hasApiKey();
        if ("ark".equals(provider)) return volcengineArk.hasApiKey();
        return dashScopeProperties.hasApiKey();
    }

    private boolean wantsReal() {
        return "real".equals(appProperties.getAi().resolvedMode());
    }

    private boolean strictReal() {
        return appProperties.getAi().isStrictReal();
    }

    /** 离线兜底实现：mock 模式用 Mock，其余（seed/real 失败）用 Seed。 */
    private AiService offline() {
        return "mock".equals(appProperties.getAi().resolvedMode()) ? mock : seed;
    }

    @Override
    public boolean detectFace(byte[] imageBytes, String mimeType) {
        if (wantsReal() && !dashScopeProperties.hasApiKey()) {
            if (strictReal()) {
                throw new IllegalStateException("AI_MODE=real 但未配置 DASHSCOPE_API_KEY");
            }
            log.warn("AI_MODE=real 但未配置 Key，降级到 {}", offline().modeName());
        }
        if (!useReal()) {
            return offline().detectFace(imageBytes, mimeType);
        }
        try {
            return dashScope.detectFace(imageBytes, mimeType);
        } catch (Exception e) {
            if (strictReal()) {
                throw new RuntimeException("真实人脸检测失败: " + e.getMessage(), e);
            }
            log.warn("真实人脸检测失败，降级离线实现", e);
            return offline().detectFace(imageBytes, mimeType);
        }
    }

    @Override
    public FaceAnalysisResult analyze(byte[] imageBytes, String mimeType, String preference, List<WigProduct> catalog) {
        if (wantsReal() && !dashScopeProperties.hasApiKey()) {
            if (strictReal()) {
                throw new IllegalStateException("AI_MODE=real 但未配置 DASHSCOPE_API_KEY");
            }
            log.warn("AI_MODE=real 但未配置 Key，降级到 {}", offline().modeName());
        }
        if (!useReal()) {
            return offline().analyze(imageBytes, mimeType, preference, catalog);
        }
        try {
            return dashScope.analyze(imageBytes, mimeType, preference, catalog);
        } catch (Exception e) {
            if (strictReal()) {
                throw new RuntimeException("真实分析失败: " + e.getMessage(), e);
            }
            log.warn("真实分析失败，降级离线实现", e);
            return offline().analyze(imageBytes, mimeType, preference, catalog);
        }
    }

    @Override
    public GenResult generateTryon(GenContext ctx, IntConsumer progress) {
        // 试戴 provider：dashscope（默认）/ yunwu（gpt-image-2 多图融合）/ flux（Flux Kontext 单图保 ID 编辑）
        // 若前端指定了 preferredModel，优先按 model key 覆盖 provider（演示模式切换模型用）
        String provider;
        if (ctx.preferredModel() != null && !ctx.preferredModel().isBlank()) {
            provider = resolveProviderForModel(ctx.preferredModel());
            log.info("[Router] 前端指定 preferredModel={}, 解析 provider={}", ctx.preferredModel(), provider);
        } else {
            provider = resolveTryonProvider();
        }

        if ("yunwu".equals(provider)) {
            if (!yunwu.hasApiKey()) {
                if (strictReal()) {
                    throw new IllegalStateException("AI_TRYON_PROVIDER=yunwu 但未配置 YUNWU_API_KEY");
                }
                log.warn("AI_TRYON_PROVIDER=yunwu 但未配置 Key，降级到 {}", offline().modeName());
                return offline().generateTryon(ctx, progress);
            }
            if (!"real".equals(appProperties.getAi().resolvedMode())) {
                return offline().generateTryon(ctx, progress);
            }
            try {
                return yunwu.generateTryon(ctx, progress);
            } catch (Exception e) {
                if (strictReal()) {
                    throw new RuntimeException("Yunwu 试戴失败: " + e.getMessage(), e);
                }
                log.warn("Yunwu 试戴失败，降级种子图", e);
                return seed.generateTryon(ctx, progress);
            }
        }

        if ("flux".equals(provider)) {
            if (!yunwuFlux.hasApiKey()) {
                if (strictReal()) {
                    throw new IllegalStateException("AI_TRYON_PROVIDER=flux 但未配置 YUNWU_API_KEY");
                }
                log.warn("AI_TRYON_PROVIDER=flux 但未配置 Key，降级到 {}", offline().modeName());
                return offline().generateTryon(ctx, progress);
            }
            if (!"real".equals(appProperties.getAi().resolvedMode())) {
                // 非 real 模式不调付费 API
                return offline().generateTryon(ctx, progress);
            }
            try {
                return yunwuFlux.generateTryon(ctx, progress);
            } catch (Exception e) {
                if (strictReal()) {
                    throw new RuntimeException("Flux Kontext 试戴失败: " + e.getMessage(), e);
                }
                log.warn("Flux Kontext 试戴失败，降级种子图", e);
                return seed.generateTryon(ctx, progress);
            }
        }

        if ("ark".equals(provider)) {
            if (!volcengineArk.hasApiKey()) {
                if (strictReal()) {
                    throw new IllegalStateException("Doubao Seedream 需要 ARK_API_KEY（火山方舟）");
                }
                log.warn("未配置 ARK_API_KEY，Doubao 试戴降级到 {}", offline().modeName());
                return offline().generateTryon(ctx, progress);
            }
            if (!"real".equals(appProperties.getAi().resolvedMode())) {
                return offline().generateTryon(ctx, progress);
            }
            try {
                return volcengineArk.generateTryon(ctx, progress);
            } catch (Exception e) {
                if (strictReal()) {
                    throw new RuntimeException("火山方舟 Seedream 试戴失败: " + e.getMessage(), e);
                }
                log.warn("火山方舟 Seedream 试戴失败，降级种子图", e);
                return seed.generateTryon(ctx, progress);
            }
        }

        // 默认 provider=dashscope，行为与改造前完全一致
        if (wantsReal() && !dashScopeProperties.hasApiKey()) {
            if (strictReal()) {
                throw new IllegalStateException("AI_MODE=real 但未配置 DASHSCOPE_API_KEY");
            }
            log.warn("AI_MODE=real 但未配置 Key，降级到 {}", offline().modeName());
        }
        if (!useReal()) {
            return offline().generateTryon(ctx, progress);
        }
        try {
            return dashScope.generateTryon(ctx, progress);
        } catch (Exception e) {
            if (strictReal()) {
                throw new RuntimeException("真实试戴失败: " + e.getMessage(), e);
            }
            log.warn("真实试戴生成失败，降级种子图", e);
            return seed.generateTryon(ctx, progress);
        }
    }

    private String resolveTryonProvider() {
        String p = appProperties.getAi().getTryonProvider();
        if (p == null) return "dashscope";
        String low = p.trim().toLowerCase();
        // flux 是 Flux Kontext 的别名；yunwu-flux 也兼容
        if ("flux".equals(low) || "yunwu-flux".equals(low) || "flux-kontext".equals(low)) {
            return "flux";
        }
        if ("ark".equals(low) || "volcengine".equals(low) || "doubao".equals(low)) {
            return "ark";
        }
        return "yunwu".equals(low) ? "yunwu" : "dashscope";
    }

    /**
     * 将前端 preferredModel key 解析为 provider 字符串。
     * qwen → 百炼；doubao-seedream-5 → 火山方舟；
     * flux-2-max → 云雾 gpt-image-2（UI 展示名，见 YunwuOpenAiAiService.resolveEffectiveModel）；
     * 其它 GPT/Gemini → 云雾。
     */
    private String resolveProviderForModel(String modelKey) {
        return switch (modelKey.toLowerCase()) {
            case "flux-kontext", "flux-1-kontext" -> "flux";
            case "doubao-seedream-5", "seedream-5", "seedream-5-lite" -> "ark";
            case "qwen" -> "dashscope";
            case "gpt-image-1", "gpt-image-1.5", "gpt-image-2",
                 "gemini-3-pro-image", "gemini-3.1-flash-image",
                 "seedream-4.5", "flux-2-max", "qwen-yunwu" -> "yunwu";
            default -> resolveTryonProvider();
        };
    }

    @Override
    public boolean isMock() {
        return !tryonReady();
    }

    @Override
    public String modeName() {
        if (tryonReady()) return "real";
        return offline().modeName();
    }
}
