package com.wig.tryon.service.ai;

import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.WigProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Mock 实现：无需任何外部 API Key，离线即可跑通整条流程，用于快速演示。
 * - 人脸检测：始终通过
 * - 分析推荐：用基于规则的启发式给出可信结果
 * - 试戴生成：返回 null，由 TryonService 回退为复用原图（仅用于流程演示）
 */
@Component
public class MockAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(MockAiService.class);

    private final PromptBuilder promptBuilder;

    public MockAiService(PromptBuilder promptBuilder) {
        this.promptBuilder = promptBuilder;
    }

    @Override
    public boolean detectFace(byte[] imageBytes, String mimeType) {
        return imageBytes != null && imageBytes.length > 0;
    }

    @Override
    public FaceAnalysisResult analyze(byte[] imageBytes, String mimeType, String preference, List<WigProduct> catalog) {
        FaceAnalysisResult r = new FaceAnalysisResult();
        r.faceShape = "鹅蛋脸";
        r.hairlineType = (preference != null && preference.contains("发际线")) ? "M型发际线" : "正常";
        r.skinTone = "自然";
        r.currentHairDesc = "自然黑色中长发";
        r.hasWhiteHair = preference != null && preference.contains("白发");
        r.suggestions = List.of(
                "你的脸型偏鹅蛋脸，几乎百搭，建议优先尝试有层次感的中长发",
                "若想显年轻，可加一款空气刘海",
                "职场场合推荐自然色系的商务款，质感更高级");

        List<Long> ids = new ArrayList<>();
        for (WigProduct p : catalog) {
            boolean prefMatch = preference == null || preference.isBlank()
                    || p.tags.stream().anyMatch(preference::contains)
                    || (preference.contains("白发") && p.tags.contains("遮白发"))
                    || (preference.contains("发际线") && p.tags.contains("遮发际线"));
            if (prefMatch) {
                ids.add(p.id);
            }
            if (ids.size() >= 4) {
                break;
            }
        }
        if (ids.size() < 4) {
            for (WigProduct p : catalog) {
                if (!ids.contains(p.id)) {
                    ids.add(p.id);
                }
                if (ids.size() >= 4) {
                    break;
                }
            }
        }
        r.recommendedIds = ids;
        r.analysisText = "根据你的照片，整体气质清爽、脸型百搭。我为你挑选了几款贴合你气质且修饰脸型的发型，"
                + "兼顾自然真实的发丝质感，戴上后既显精神又不会有假发的生硬感～";
        log.info("[MOCK] 分析完成，推荐 IDs={}", ids);
        return r;
    }

    @Override
    public GenResult generateTryon(GenContext ctx, IntConsumer progress) {
        String prompt = promptBuilder.tryonPrompt(ctx.product(), ctx.analysis(), ctx.tryonType(), ctx.adjustNote());
        // 模拟生成耗时与进度
        try {
            for (int p = 20; p <= 90; p += 10) {
                Thread.sleep(500);
                progress.accept(p);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[MOCK] 试戴生成（回退复用原图），prompt={}", prompt);
        return new GenResult(null, prompt, 0, 0.0, "mock模式仅验证流程，未执行真实融合", "mock-flow");
    }

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public String modeName() {
        return "mock";
    }
}
