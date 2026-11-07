package com.wig.tryon.service.ai;

import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.WigProduct;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * AI 能力抽象层。可插拔实现：DashScope（阿里百炼，中国区推荐）或 Mock（离线演示）。
 * 这是文档中"换模型只改实现/配置、业务代码 0 改动"理念的落点。
 */
public interface AiService {

    /** 快速人脸检测：判断是否存在清晰正面人脸。 */
    boolean detectFace(byte[] imageBytes, String mimeType);

    /** 多模态分析用户照片（脸型/发际线/肤色/白发等）并从发型库中推荐。 */
    FaceAnalysisResult analyze(byte[] imageBytes, String mimeType, String preference, List<WigProduct> catalog);

    /** 生成试戴效果图。progress 回调用于上报 0-100 进度。 */
    GenResult generateTryon(GenContext ctx, IntConsumer progress);

    /** 当前是否处于 Mock 模式。 */
    boolean isMock();

    /** 当前 AI 模式名（seed / mock / real）。 */
    default String modeName() {
        return isMock() ? "mock" : "real";
    }

    /**
     * 试戴生成上下文。
     * <p>
     * 关键字段 {@code wigImageUrl} —— 指向"光秃秃的假发本体图"（无模特、无人脸），
     * 由 {@link com.wig.tryon.repository.WigAssetRepository} 取出后注入。
     * 这是修复"模型重画用户照片"问题的核心：让 AI 收到的"参考图"是纯假发，
     * 而非"模特戴假发的成品图"——后者会让模型学走模特的脸/衣/背景。
     * 见 docs/wig-asset-system-design.md。
     * <p>
     * 兼容性：当 {@code wigImageUrl} 为空时，AI 实现应回退到 {@code product.refImage}
     * （旧字段，一般等于 {@code product.image}），保证零数据时不阻塞演示。
     */
    record GenContext(
            String userPhotoUrl,
            byte[] userBytes,
            String userMime,
            WigProduct product,
            FaceAnalysisResult analysis,
            String tryonType,
            String adjustNote,
            String wigImageUrl,
            String wigDescriptionForAi,
            /** 前端演示模式下选择的模型 key（如 gpt-image-1.5 / qwen 等）；null 表示使用后端环境变量配置。 */
            String preferredModel
    ) {
        /** 7 参便利构造器：wigImageUrl / wigDescriptionForAi / preferredModel 均为 null。 */
        public GenContext(String userPhotoUrl, byte[] userBytes, String userMime,
                          WigProduct product, FaceAnalysisResult analysis,
                          String tryonType, String adjustNote) {
            this(userPhotoUrl, userBytes, userMime, product, analysis, tryonType, adjustNote,
                    null, null, null);
        }

        /** 9 参便利构造器：向后兼容旧调用点，preferredModel 为 null。 */
        public GenContext(String userPhotoUrl, byte[] userBytes, String userMime,
                          WigProduct product, FaceAnalysisResult analysis,
                          String tryonType, String adjustNote,
                          String wigImageUrl, String wigDescriptionForAi) {
            this(userPhotoUrl, userBytes, userMime, product, analysis, tryonType, adjustNote,
                    wigImageUrl, wigDescriptionForAi, null);
        }
    }

    /**
     * @param externalUrl 外部结果图 URL；为 null 时表示调用方应使用回退策略（如复用原图）。
     * @param prompt      实际使用的 prompt（记录用）。
     * @param retryCount  因“变化不足”触发的自动重试次数。
     * @param changeScore 原图与结果图的平均像素变化分（越大变化越明显）。
     * @param qualityNote 质量提示（为空表示通过）。
     * @param strategyUsed 本次试戴策略（便于验收不同模型/参数组合）。
     */
    record GenResult(String externalUrl, String prompt, int retryCount, Double changeScore, String qualityNote, String strategyUsed) {
        public GenResult(String externalUrl, String prompt) {
            this(externalUrl, prompt, 0, null, null, "default");
        }
    }
}
