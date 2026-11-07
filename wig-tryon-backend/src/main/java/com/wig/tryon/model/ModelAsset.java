package com.wig.tryon.model;

import java.time.Instant;
import java.util.List;

/**
 * 模特库（运营资产）。
 * 与 {@link WigAsset} 配合，由 {@link com.wig.tryon.service.ShowcaseService} 合成出
 * "模特戴假发的成品图"（{@link WigShowcase}）作为 C 端商品陈列。
 * 见 docs/wig-asset-system-design.md 第 2 章。
 */
public class ModelAsset {

    public Long id;
    public String name;             // 模特命名（小雅/小峰...）
    public String portraitImage;    // 纯模特肖像 URL
    public String gender;           // M / F
    public String ageGroup;         // young / mid / elder
    public String faceShape;        // 圆脸/方脸/长脸/瓜子/鹅蛋脸
    public List<String> tags;       // 职场/学生/宝妈/银发...
    public Instant createdAt;
    public String createdBy;

    public ModelAsset() {
    }
}
