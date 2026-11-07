package com.wig.tryon.model;

import java.util.List;

/**
 * 发型产品。价格采用区间（priceMin/priceMax），符合客户"价格显示区间而非固定值"的要求。
 */
public class WigProduct {

    public Long id;
    public String name;
    public String category;          // 短发/中长发/长发/卷发
    public String material;          // 100% 真人发丝 / 混合材质 ...
    public int priceMin;
    public int priceMax;
    public String price;             // 展示用，例如 "¥3,800 - ¥6,500"
    public String image;             // 封面图（C 端展示，未来由 primaryShowcase 派生）
    public String refImage;          // [Deprecated v0.x] 发型参考图——已被 wigAssetId 取代，仅保留向下兼容
    public String seedResultImage;   // 种子模式下的"试戴效果图"（演示用，缺省回退到 image）
    public Long wigAssetId;          // 关联假发本体（WigAsset.id），唯一喂给 AI 的"参考图"来源
    public Long primaryShowcaseId;   // [P2] 关联默认陈列图（WigShowcase.id），P0 阶段未使用
    public List<String> tags;        // 风格标签
    public List<String> faceTypes;   // 适合脸型
    public double rating;
    public int reviews;
    public String suitable;          // 适合脸型文案
    public String weight;
    public String craft;             // 工艺
    public String description;       // 英文/中文描述（喂给图像模型）
    public String colorFamily;       // 发色体系：自然黑/深棕/亚麻...
    public String capStructure;      // 帽网结构：全手织/半手织/机器织
    public List<String> targetCrowds; // 目标人群：职场/宝妈/学生/银发等
    public List<String> sceneTags;   // 场景标签：通勤/约会/宴会/面试等
    public List<Long> storeIds;      // 可体验门店（多对多）
    public int hairlineCoverage;     // 发际线遮盖能力 1-5
    public int whiteHairCoverage;    // 白发遮盖能力 1-5
    public boolean isHot;
    public boolean isNew;

    /** C 端展示：该款式下全部审核通过的成品图 URL（10~15 张），非持久化字段由 RecommendService 注入。 */
    public List<String> galleryImages;

    public WigProduct() {
    }
}
