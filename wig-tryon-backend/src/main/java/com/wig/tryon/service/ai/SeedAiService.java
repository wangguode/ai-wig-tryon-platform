package com.wig.tryon.service.ai;

import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.WigProduct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * 种子模式：用"预生成的效果图"返回试戴结果，离线也能呈现一面差异明显的效果墙，
 * 是给领导演示的首选（断网也完美）。分析/人脸检测复用 Mock 的规则启发式。
 *
 * 说明：演示阶段以发型款式图作为"试戴效果种子"。接入真实素材时，
 * 把每个 WigProduct 的种子结果图替换为该测试照片的精修效果图即可。
 */
@Component
public class SeedAiService implements AiService {

    private static final Logger log = LoggerFactory.getLogger(SeedAiService.class);

    private final MockAiService mock;

    public SeedAiService(MockAiService mock) {
        this.mock = mock;
    }

    @Override
    public boolean detectFace(byte[] imageBytes, String mimeType) {
        return mock.detectFace(imageBytes, mimeType);
    }

    @Override
    public FaceAnalysisResult analyze(byte[] imageBytes, String mimeType, String preference, List<WigProduct> catalog) {
        return mock.analyze(imageBytes, mimeType, preference, catalog);
    }

    @Override
    public GenResult generateTryon(GenContext ctx, IntConsumer progress) {
        try {
            for (int p = 25; p <= 90; p += 13) {
                Thread.sleep(450);
                progress.accept(p);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        WigProduct product = ctx.product();
        String seed = product.seedResultImage != null ? product.seedResultImage : product.image;
        log.info("[SEED] 返回种子效果图 productId={}, url={}", product.id, seed);
        // 返回相对路径（由前端按自身域名解析），TryonService 会按 URL 协议直接使用。
        return new GenResult(seed, "seed:" + product.id, 0, 0.0, "seed模式返回预置效果图", "seed-gallery");
    }

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public String modeName() {
        return "seed";
    }
}
