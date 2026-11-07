package com.wig.tryon.service.ai;

import com.wig.tryon.model.FaceAnalysisResult;
import com.wig.tryon.model.WigProduct;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt 构建器：集中管理"推荐分析"与"试戴生成"的提示词。
 * 这是推荐质量与生成效果（真实发丝感、刘海、发际线遮盖等）的关键所在。
 */
@Component
public class PromptBuilder {

    /** 推荐分析系统 prompt（投喂发型库，约束 JSON 输出）。 */
    public String analysisSystemPrompt(String preference, List<WigProduct> catalog) {
        String preferenceLine = (preference == null || preference.isBlank())
                ? "" : "- 用户特别偏好：" + preference;
        return """
                你是专业的假发造型顾问，请分析用户照片并从发型库中推荐最适合的款式。

                【分析维度】请重点关注：
                1. 脸型识别（圆脸/方脸/长脸/鹅蛋脸/心形脸）
                2. 发际线状态（正常/M型发际线/发际线后移/宽发际线）
                3. 头顶发量（是否稀疏、是否有白发）
                4. 肤色（白皙/自然/偏深）
                5. 整体气质（职业感/生活感/年龄段判断）

                【推荐原则】（按喜好与脸型推荐，不要按价格推荐）
                - 圆脸：有层次感的中长发、增加头顶高度
                - 方脸：柔和卷发、避免直发平刘海
                - 发际线后移/M型：优先推荐有刘海、可遮盖发际线的款式
                - 白发较多：推荐遮盖力强的全头套款式
                - 职业场景：推荐自然色系商务款
                - 仅允许从给定发型库中选择推荐ID，推荐ID数量 4-6 个
                - 当前仅做“推荐步骤”，不要生成试戴结果描述，不要输出任何库外产品
                - 重要：脸型 / 发际线 / 白发 / 发量 这几项会被系统用于综合排序，请务必如实、准确判断，
                  推荐ID仅作为参考信号，最终展示顺序由系统结合规则一并决定
                %s

                【当前可选发型库】
                %s

                【返回格式】严格按以下 JSON 返回，不要任何多余文字或解释：
                {
                  "faceShape": "脸型",
                  "hairlineType": "发际线类型",
                  "skinTone": "肤色",
                  "currentHairDesc": "用户当前发型简述",
                  "hasWhiteHair": true,
                  "suggestions": ["建议1", "建议2", "建议3"],
                  "recommendedIds": [1, 2, 3, 4],
                  "analysisText": "对用户的亲切分析文案，100字左右"
                }
                """.formatted(preferenceLine, catalogForAi(catalog));
    }

    public String analysisUserPrompt(String preference) {
        String base = "请分析我的照片，告诉我适合什么发型，并从发型库中推荐最适合我的 4 款。";
        if (preference != null && !preference.isBlank()) {
            base += "我的需求是：" + preference;
        }
        return base;
    }

    private String catalogForAi(List<WigProduct> products) {
        StringBuilder sb = new StringBuilder();
        for (WigProduct p : products) {
            sb.append(String.format(
                    "ID:%d | %s | 类型:%s | 适合脸型:%s | 风格:%s | 人群:%s | 场景:%s | 发际线遮盖:%d | 白发遮盖:%d | 门店:%s | 价格:¥%d-¥%d%n",
                    p.id, p.name, p.category,
                    String.join(",", p.faceTypes), String.join(",", p.tags),
                    p.targetCrowds == null ? "-" : String.join(",", p.targetCrowds),
                    p.sceneTags == null ? "-" : String.join(",", p.sceneTags),
                    p.hairlineCoverage, p.whiteHairCoverage,
                    p.storeIds == null ? "-" : String.join(",", p.storeIds.stream().map(String::valueOf).toList()),
                    p.priceMin, p.priceMax));
        }
        return sb.toString();
    }

    /**
     * 试戴生成 prompt（中文，国产图像模型对中文 prompt 友好）。
     * 针对客户反馈定向优化：真实发丝感（非蜡像）、刘海、发际线、白发遮盖、局部调整。
     */
    public String tryonPrompt(WigProduct product, FaceAnalysisResult analysis,
                              String tryonType, String adjustNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("高清写实人物摄影，保持人物脸型、五官、肤色、体态与背景完全不变，");
        sb.append("只替换人物头发区域，使用所选假发进行覆盖式佩戴，");
        sb.append("原有头发必须被完整替换，不允许原发残留或双层叠发，");
        sb.append("假发特征：").append(product.description).append("，");
        sb.append("必须严格对齐所选假发参考图的轮廓、长度、分缝、刘海与体积，");

        if (analysis != null && analysis.faceShape != null) {
            switch (analysis.faceShape) {
                case "圆脸" -> sb.append("发型有层次感、顶部蓬松以修饰脸型，");
                case "方脸" -> sb.append("发型柔和、带波浪卷以软化下颌线条，");
                case "长脸" -> sb.append("两侧增加发量、中分平衡脸部比例，");
                default -> { }
            }
        }

        if ("短发".equals(product.category)) {
            sb.append("若原图是长发，必须先移除原长发再替换为短发，");
            sb.append("短发长度严格控制在耳下到下巴附近，边缘过渡自然，");
        }

        boolean hairlineFix = "LOCAL_HAIRLINE".equals(tryonType)
                || (analysis != null && ("M型发际线".equals(analysis.hairlineType)
                || "发际线后移".equals(analysis.hairlineType)));
        if (hairlineFix) {
            sb.append("用自然的刘海修饰并遮盖发际线，发际线过渡真实，");
        }
        if ("LOCAL_BANGS".equals(tryonType)) {
            sb.append("在额头添加自然垂落的刘海，刘海发丝真实，与原发型融合自然，");
        }
        if (analysis != null && Boolean.TRUE.equals(analysis.hasWhiteHair)) {
            sb.append("完全遮盖白发，发色均匀自然，");
        }

        appendRealismQualityCn(sb);

        if (adjustNote != null && !adjustNote.isBlank()) {
            sb.append("额外调整要求：").append(adjustNote).append("。");
        }
        appendAvoidCn(sb);
        return sb.toString();
    }

    /**
     * 试戴 prompt（双图模式 - 第二张是"纯假发本体图"）。
     * <p>
     * 与旧 {@link #tryonPrompt} 的关键差异：
     *   - 明确告诉模型"第一张是用户自拍，第二张是假发商品图（可能在头模支架上）——只取发丝款式"
     *   - 不再提"参考图轮廓"等会让模型把第二张当成"主体广告图"重画的指令
     *   - 第一句即硬指令"只换发型，其它一切不动"
     * <p>
     * 配套使用前置条件：调用方必须已经从 WigAsset.productImage 取出"光秃秃假发图"
     * 作为第二张图传入；如果取到的是"模特戴假发的成品图"，prompt 再好也救不回来。
     * 见 docs/wig-asset-system-design.md 第 1.3 节。
     */
    public String tryonPromptWithBareWig(WigProduct product, FaceAnalysisResult analysis,
                                          String tryonType, String adjustNote,
                                          String wigDescriptionForAi) {
        StringBuilder sb = new StringBuilder();
        sb.append("第一张图是用户本人的自拍（主体），第二张图是店铺陈列的假发商品图。");
        appendWigProductRefHintCn(sb);
        sb.append("任务：保留第一张图中人物的全部特征——面部五官、表情、肤色、性别、年龄、姿势、")
          .append("衣服、配饰、背景、构图、光照与色调全部不得改动；")
          .append("仅把第一张图中的头发区域，完整替换为第二张图所示的假发样式（颜色、长度、卷直、分缝、刘海）。");

        sb.append("假发款式：").append(product.name).append("，");
        String desc = (wigDescriptionForAi != null && !wigDescriptionForAi.isBlank())
                ? wigDescriptionForAi
                : product.description;
        if (desc != null && !desc.isBlank()) {
            sb.append(desc).append("，");
        }
        if (product.category != null) {
            sb.append("类型为").append(product.category).append("，");
        }
        if (product.colorFamily != null && !product.colorFamily.isBlank()) {
            sb.append("发色为").append(product.colorFamily).append("，");
        }

        if (analysis != null && analysis.faceShape != null) {
            switch (analysis.faceShape) {
                case "圆脸" -> sb.append("发型有层次感、顶部蓬松以修饰圆脸，");
                case "方脸" -> sb.append("发型柔和、带波浪以软化下颌，");
                case "长脸" -> sb.append("两侧增加发量、中分平衡长脸比例，");
                default -> { }
            }
        }

        if (product.category != null && product.category.contains("短")) {
            sb.append("如原图为长发，先完整移除长发再生成短发，长度严格控制在耳下到下巴之间，");
        }

        if ("LOCAL_HAIRLINE".equals(tryonType)
                || (analysis != null && ("M型发际线".equals(analysis.hairlineType)
                    || "发际线后移".equals(analysis.hairlineType)))) {
            sb.append("用自然刘海柔和遮盖发际线，发际线过渡真实，");
        }
        if ("LOCAL_BANGS".equals(tryonType)) {
            sb.append("在额头自然添加垂落刘海，发丝真实与整体融合，");
        }
        if (analysis != null && Boolean.TRUE.equals(analysis.hasWhiteHair)) {
            sb.append("完全遮盖白发，发色均匀自然，");
        }

        if (adjustNote != null && !adjustNote.isBlank()) {
            sb.append("额外调整：").append(adjustNote).append("，");
        }

        appendRealismQualityCn(sb);
        appendAvoidCn(sb);

        sb.append("再次强调：输出图必须是与第一张图同一个人、同样的脸、同样的衣服、同样的背景，")
          .append("仅头发部分换为第二张图的假发样式，绝不能输出第二张商品陈列图本身。");
        return sb.toString();
    }

    /**
     * 单图试戴 prompt（qwen-image-edit 双图失败、疑似输出参考图时的降级路径）。
     * 不传假发参考图，仅靠文字描述款式，避免模型复刻头模陈列图。
     */
    public String tryonPromptSingleImage(WigProduct product, FaceAnalysisResult analysis,
                                         String tryonType, String adjustNote,
                                         String wigDescriptionForAi) {
        StringBuilder sb = new StringBuilder();
        sb.append("图中是用户本人自拍，输出必须是同一位真人佩戴假发后的效果，")
          .append("禁止输出头模、支架、商品陈列图或任何非真人主体。");
        sb.append("任务：保留人物面部五官、表情、肤色、姿势、衣服、配饰、背景与光照完全不变，")
          .append("仅替换头发区域为以下假发款式。");

        sb.append("假发款式：").append(product.name).append("，");
        String desc = (wigDescriptionForAi != null && !wigDescriptionForAi.isBlank())
                ? wigDescriptionForAi
                : product.description;
        if (desc != null && !desc.isBlank()) {
            sb.append(desc).append("，");
        }
        if (product.category != null) {
            sb.append("类型为").append(product.category).append("，");
        }
        if (product.colorFamily != null && !product.colorFamily.isBlank()) {
            sb.append("发色为").append(product.colorFamily).append("，");
        }

        if (analysis != null && analysis.faceShape != null) {
            switch (analysis.faceShape) {
                case "圆脸" -> sb.append("发型有层次感、顶部蓬松以修饰圆脸，");
                case "方脸" -> sb.append("发型柔和、带波浪以软化下颌，");
                case "长脸" -> sb.append("两侧增加发量、中分平衡长脸比例，");
                default -> { }
            }
        }
        if (product.category != null && product.category.contains("短")) {
            sb.append("如原图为长发，先完整移除长发再生成短发，");
        }
        if ("LOCAL_HAIRLINE".equals(tryonType)
                || (analysis != null && ("M型发际线".equals(analysis.hairlineType)
                    || "发际线后移".equals(analysis.hairlineType)))) {
            sb.append("用自然刘海柔和遮盖发际线，");
        }
        if ("LOCAL_BANGS".equals(tryonType)) {
            sb.append("在额头自然添加垂落刘海，");
        }
        if (analysis != null && Boolean.TRUE.equals(analysis.hasWhiteHair)) {
            sb.append("完全遮盖白发，");
        }
        if (adjustNote != null && !adjustNote.isBlank()) {
            sb.append("额外调整：").append(adjustNote).append("，");
        }
        appendRealismQualityCn(sb);
        appendAvoidCn(sb);
        return sb.toString();
    }

    /**
     * 试戴英文 prompt（双图 bare-wig 模式，云雾 GPT/Gemini/Doubao 用）。
     * 语义与 {@link #tryonPromptWithBareWig} 对齐：图1=用户、图2=已抠假发本体，只换头发。
     */
    public String tryonPromptWithBareWigEn(WigProduct product, FaceAnalysisResult analysis,
                                           String tryonType, String adjustNote,
                                           String wigDescriptionForAi, boolean hasReferenceImage) {
        StringBuilder sb = new StringBuilder();
        if (hasReferenceImage) {
            sb.append("Image 1 is the customer's selfie (subject). Image 2 is a wig product display photo. ");
            appendWigProductRefHintEn(sb);
            sb.append("Task: keep EVERYTHING in Image 1 unchanged — facial features, expression, skin tone, ")
              .append("gender, age, pose, clothing, accessories, background, composition and lighting; ")
              .append("ONLY replace the hair region with the wig style shown in Image 2 ")
              .append("(color, length, curl pattern, parting, bangs). ");
        } else {
            sb.append("Edit the customer's selfie to wear the described wig. ");
            sb.append("Keep face shape, facial features, skin tone, expression, body, clothing and background unchanged. ");
            sb.append("Replace ONLY the hair region. ");
        }
        sb.append("The original hair must be fully replaced — no residual hair, no double-layered hair. ");

        if (product != null) {
            sb.append("Wig style: ").append(product.name).append(". ");
            String desc = (wigDescriptionForAi != null && !wigDescriptionForAi.isBlank())
                    ? wigDescriptionForAi
                    : (product.description != null ? product.description : "");
            if (!desc.isBlank()) {
                sb.append("Details: ").append(desc).append(". ");
            }
            if (product.category != null) {
                sb.append("Category: ").append(product.category).append(". ");
            }
            if (product.colorFamily != null && !product.colorFamily.isBlank()) {
                sb.append("Hair color: ").append(product.colorFamily).append(". ");
            }
        }

        appendFaceShapeHintEn(sb, analysis);
        appendShortHairHintEn(sb, product);
        appendTryonTypeHintsEn(sb, analysis, tryonType);

        if (adjustNote != null && !adjustNote.isBlank()) {
            sb.append("Additional adjustment: ").append(adjustNote).append(". ");
        }

        appendRealismQualityEn(sb);
        if (hasReferenceImage) {
            sb.append("Output must be the same person as Image 1 with the same face, clothes and background — only hair changed. ");
        }
        appendAvoidEn(sb);
        return sb.toString();
    }

    /**
     * 试戴 prompt（Flux Kontext 专用，单图编辑模式）。
     * <p>
     * 关键差异 vs {@link #tryonPromptEn}：
     *   - Flux Kontext 走 OpenAI dall-e edits 协议，**只能传 1 张用户图**（无参考图）
     *     → 假发款式只能用文字描述
     *   - Flux Kontext 自身保 ID 强，prompt 反而要"克制"，不要堆叠太多约束
     *   - 强化"真实摄影"风格词（DSLR / 35mm / film grain / natural skin texture）
     *     正面对症客户反馈"AI 痕迹太重 / 蜡像感"
     * <p>
     * 经验：Flux Kontext 对"keep the same person"这类显式 ID 保留指令响应非常好，
     * 不像 qwen-image-edit 需要靠"reference image contour"间接保 ID，所以 prompt 短而准更佳。
     */
    public String tryonPromptForFlux(WigProduct product, FaceAnalysisResult analysis,
                                     String tryonType, String adjustNote) {
        return tryonPromptForFlux(product, analysis, tryonType, adjustNote, null);
    }

    public String tryonPromptForFlux(WigProduct product, FaceAnalysisResult analysis,
                                     String tryonType, String adjustNote,
                                     String wigDescriptionForAi) {
        StringBuilder sb = new StringBuilder();
        // 第一句：明确人物身份不变，只换头发——Flux Kontext 的核心能力点
        sb.append("Keep the exact same person, same face, same identity, same skin texture, ")
          .append("same expression, same body pose, same clothing, same background and same lighting. ")
          .append("Only replace the hair region. ");

        // 假发款式描述（无参考图，全靠文字）
        if (product != null) {
            sb.append("Replace the hair with the following wig style: ");
            String desc = (wigDescriptionForAi != null && !wigDescriptionForAi.isBlank())
                    ? wigDescriptionForAi
                    : product.description;
            if (desc != null && !desc.isBlank()) {
                sb.append(desc).append(". ");
            } else {
                sb.append(product.name).append(". ");
            }
            if (product.category != null) {
                sb.append("Category: ").append(product.category).append(". ");
            }
            if (product.colorFamily != null && !product.colorFamily.isBlank()) {
                sb.append("Hair color: ").append(product.colorFamily).append(". ");
            }
        }

        // 局部场景细化
        if ("LOCAL_HAIRLINE".equals(tryonType)
                || (analysis != null && ("M型发际线".equals(analysis.hairlineType)
                || "发际线后移".equals(analysis.hairlineType)))) {
            sb.append("Add soft natural bangs to cover the receded hairline. ");
        }
        if ("LOCAL_BANGS".equals(tryonType)) {
            sb.append("Add natural bangs falling softly over the forehead. ");
        }
        if (analysis != null && Boolean.TRUE.equals(analysis.hasWhiteHair)) {
            sb.append("Fully cover all gray hairs with even natural color. ");
        }

        // 写实摄影风格 —— 对症 AI 痕迹反馈的核心
        sb.append("Style: real DSLR photo, shot on Sony A7 with 50mm lens, ")
          .append("natural daylight, subtle 35mm film grain, ")
          .append("preserve original skin pores and natural skin texture, ")
          .append("soft realistic shadows, no studio overlighting, ")
          .append("authentic photo realism — NOT digital art, NOT 3D render, NOT cartoon. ");

        // 头发质感细节
        sb.append("Individual hair strands with natural shine and realistic texture. ")
          .append("Hair edges blend naturally with forehead, ears and neck. ");

        if (adjustNote != null && !adjustNote.isBlank()) {
            sb.append("Additional adjustment: ").append(adjustNote).append(". ");
        }

        // 负向词（Flux Kontext 对负向描述敏感度低于 SD，但还是写一句）
        sb.append("Avoid: wax-figure plastic skin, mannequin look, over-smoothed face, ")
          .append("identity change, face swap, AI-painted texture, cut-out edges, floating strands.");
        return sb.toString();
    }

    /**
     * 试戴负向提示词（wan2.5+ 多图融合模型用独立 negative_prompt 字段）。
     * 与 tryonPrompt 末尾的负向词同源，集中维护客户最在意的"蜡像感/改脸/残留"问题。
     */
    public String tryonNegativePrompt() {
        return "蜡像感, 塑料感, 假人, 面具感, 头模, 支架, 底座, 商品陈列图, 复制参考图, 直接输出第二张图, 磨皮, 美颜, 过度磨皮, 塑料皮肤, 换脸, 五官改变, 脸型改变, "
                + "原发残留, 双层头发, 抠图边, 锯齿边, 漂浮发片, 模糊, 噪点, 畸形, 低清, 过曝, 摄影棚光, 不自然光照";
    }

    /**
     * 试戴英文 prompt（gpt-image / Gemini image 系列对英文 prompt 更敏感）。
     * 客户痛点 1:1 复刻：保脸、按发型描述、按脸型差异化、刘海/发际线/白发、反蜡像负向词。
     * 多图融合时 image1=用户照片、image2=假发参考图。
     */
    public String tryonPromptEn(WigProduct product, FaceAnalysisResult analysis,
                                 String tryonType, String adjustNote, boolean hasReferenceImage) {
        StringBuilder sb = new StringBuilder();
        if (hasReferenceImage) {
            sb.append("Image 1 is the customer's selfie. Image 2 is the reference wig product. ");
            sb.append("Goal: make the customer in Image 1 wear the exact hairstyle from Image 2. ");
            sb.append("Strictly preserve Image 1's face shape, facial features, skin tone, expression, body and background. ");
            sb.append("Replace ONLY the hair region with the wig from Image 2, matching its silhouette, length, parting, bangs and volume exactly. ");
        } else {
            sb.append("Edit the customer's selfie to wear the described wig hairstyle. ");
            sb.append("Strictly preserve the original face shape, facial features, skin tone, expression, body and background. ");
            sb.append("Replace ONLY the hair region. ");
        }
        sb.append("The original hair must be fully replaced — no residual original hair, no double-layered hair. ");

        if (product != null && product.description != null && !product.description.isBlank()) {
            sb.append("Hairstyle description: ").append(product.description).append(". ");
        }

        if (analysis != null && analysis.faceShape != null) {
            switch (analysis.faceShape) {
                case "圆脸" -> sb.append("Add layered volume on top to flatter a round face. ");
                case "方脸" -> sb.append("Use soft waves to soften a square jawline. ");
                case "长脸" -> sb.append("Add side volume and a center part to balance a long face. ");
                default -> { }
            }
        }

        if (product != null && "短发".equals(product.category)) {
            sb.append("If the original hair is long, fully remove the long hair before applying the short cut. ");
            sb.append("Short hair length must end between earlobe and chin, with natural edges. ");
        }

        boolean hairlineFix = "LOCAL_HAIRLINE".equals(tryonType)
                || (analysis != null && ("M型发际线".equals(analysis.hairlineType)
                || "发际线后移".equals(analysis.hairlineType)));
        if (hairlineFix) {
            sb.append("Use natural bangs to softly cover and fix the receded hairline. ");
        }
        if ("LOCAL_BANGS".equals(tryonType)) {
            sb.append("Add natural-looking bangs falling over the forehead, blending realistically with the rest of the hair. ");
        }
        if (analysis != null && Boolean.TRUE.equals(analysis.hasWhiteHair)) {
            sb.append("Fully cover all gray/white hair with even, natural color. ");
        }

        appendRealismQualityEn(sb);

        if (adjustNote != null && !adjustNote.isBlank()) {
            sb.append("Additional adjustment: ").append(adjustNote).append(". ");
        }

        appendAvoidEn(sb);
        return sb.toString();
    }

    /** 写实质感（中文）：刻意不用「摄影棚/8K」，避免模型走向磨皮蜡像。 */
    private void appendRealismQualityCn(StringBuilder sb) {
        sb.append("发丝根根分明、有自然光泽与真实质感，发型有自然垂坠与动感，绝非塑料或假人质感，");
        sb.append("保留原图肤色、毛孔与自然皮肤纹理，禁止磨皮美颜，");
        sb.append("光照与原图一致、自然柔和，禁止摄影棚过度打光，");
        sb.append("头发边缘与额头、耳侧、颈部衔接自然，禁止抠图边、锯齿边、漂浮发片，");
        sb.append("真实相机拍摄质感，非数字绘画、非3D渲染。");
    }

    /** 写实质感（英文）：与 {@link #tryonPromptForFlux} 中已验证有效的反蜡像词对齐。 */
    private void appendRealismQualityEn(StringBuilder sb) {
        sb.append("Individual hair strands with natural shine and realistic texture, soft natural drape. ");
        sb.append("Preserve original skin pores, skin tone and natural skin texture — no beauty filter, no over-smoothing. ");
        sb.append("Keep the same natural lighting as the original photo — no studio overlighting. ");
        sb.append("Hair edges blend naturally with forehead, ears and neck — no cut-out edges, no floating strands. ");
        sb.append("Authentic photo realism — NOT digital art, NOT 3D render, NOT wax figure. ");
    }

    private void appendAvoidCn(StringBuilder sb) {
        sb.append("避免：假发塑料感、蜡像感、假人外观、磨皮美颜、过度棚光、换脸、五官改变、")
          .append("头模脸、支架、底座、原发残留、双层头发、模糊细节。");
    }

    private void appendAvoidEn(StringBuilder sb) {
        sb.append("AVOID: wax-figure look, plastic feel, mannequin look, over-smoothed face, beauty filter, ")
          .append("identity change, face change, studio overlighting, mannequin head, wig stand, pedestal, ")
          .append("residual original hair, double-layered hair, cut-out edges, floating strands, blur, noise.");
    }

    /** 客户本体图常为头模支架陈列照：只取发丝，忽略头模/支架/背景。 */
    private void appendWigProductRefHintCn(StringBuilder sb) {
        sb.append("第二张可能在头模与支架上：只参考发丝的颜色、长度、卷直、分缝与刘海，")
          .append("忽略塑料头模、支架、底座与黑色背景，禁止把支架或头模带入成图，")
          .append("禁止用第二张的头模替换用户真脸，禁止直接输出或近似复制第二张整图；");
    }

    private void appendWigProductRefHintEn(StringBuilder sb) {
        sb.append("Image 2 may show the wig on a display mannequin head with a stand — ")
          .append("use ONLY the hair/wig style, color, length, curl pattern, parting and bangs. ")
          .append("Ignore the plastic mannequin, stand, pedestal and background in Image 2. ")
          .append("Do NOT copy the mannequin or stand into the result. Do NOT replace the customer's face. ");
    }

    private void appendFaceShapeHintEn(StringBuilder sb, FaceAnalysisResult analysis) {
        if (analysis == null || analysis.faceShape == null) return;
        switch (analysis.faceShape) {
            case "圆脸" -> sb.append("Add layered volume on top to flatter a round face. ");
            case "方脸" -> sb.append("Use soft waves to soften a square jawline. ");
            case "长脸" -> sb.append("Add side volume and a center part to balance a long face. ");
            default -> { }
        }
    }

    private void appendShortHairHintEn(StringBuilder sb, WigProduct product) {
        if (product == null) return;
        if ("短发".equals(product.category) || (product.category != null && product.category.contains("短"))) {
            sb.append("If original hair is long, remove it first. Short hair ends between earlobe and chin. ");
        }
    }

    private void appendTryonTypeHintsEn(StringBuilder sb, FaceAnalysisResult analysis,
                                        String tryonType) {
        boolean hairlineFix = "LOCAL_HAIRLINE".equals(tryonType)
                || (analysis != null && ("M型发际线".equals(analysis.hairlineType)
                || "发际线后移".equals(analysis.hairlineType)));
        if (hairlineFix) {
            sb.append("Use natural bangs to softly cover the receded hairline. ");
        }
        if ("LOCAL_BANGS".equals(tryonType)) {
            sb.append("Add natural bangs over the forehead, blending with the rest of the hair. ");
        }
        if (analysis != null && Boolean.TRUE.equals(analysis.hasWhiteHair)) {
            sb.append("Fully cover all gray/white hair with even natural color. ");
        }
    }
}
