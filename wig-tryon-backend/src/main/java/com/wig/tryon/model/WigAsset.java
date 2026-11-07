package com.wig.tryon.model;

import java.time.Instant;
import java.util.List;

/**
 * 假发本体（运营核心资产）。
 * 与 {@link WigProduct} 的关系：
 *   - 1:1 强关联（每款上架产品必须挂一个 WigAsset）
 *   - WigAsset 是"光秃秃的假发图"——无模特、无人脸，唯一喂给图像模型作"参考"的资产
 *   - WigProduct 是上架商品，承载价格/门店/标签等业务信息
 * 见 docs/wig-asset-system-design.md。
 */
public class WigAsset {

    public Long id;
    public String code;                  // 内部 SKU 编码（运营录入）
    public String name;                  // 内部名（运营辨识）
    public String productImage;          // 假发本体图 URL（白底/透明底，无模特）
    public String productImageThumbnail; // 缩略图（管理后台列表用，可与 productImage 相同）
    public String descriptionForAi;      // 喂给 AI 的中文描述（长度/卷直/颜色/分缝/刘海...）
    public String category;              // 短发/中长发/长发/卷发
    public String colorFamily;           // 自然黑/深棕/亚麻...
    public List<String> tags;            // 风格标签
    public Instant createdAt;
    public String createdBy;             // 哪个运营上传

    public WigAsset() {
    }
}
