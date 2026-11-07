package com.wig.tryon.model;

import java.time.Instant;

/**
 * 陈列成品图：模特 + 假发本体 → AI 合成的"模特戴假发的样子"。
 * 这是 C 端用户在选品页看到的"商品图"——但对 AI 不可见。
 * 见 docs/wig-asset-system-design.md。
 */
public class WigShowcase {

    public Long id;
    public Long wigAssetId;        // 关联 WigAsset.id
    public Long modelAssetId;      // 关联 ModelAsset.id（可空——支持运营手动上传成品图）
    public String resultImage;     // 成品图 URL（落地后的本地路径）
    public String status;          // DRAFT / APPROVED / REJECTED
    public boolean isPrimary;      // 该 wigAssetId 下是否设为产品默认陈列图
    public Double changeScore;     // AI 生成时的"变化分"——越大变化越明显
    public String prompt;          // 实际使用的 prompt（审计/排查用）
    public String strategyUsed;    // AI 策略（dashscope|qwen-edit|... 等）
    public String errorMessage;    // 生成失败时的错误（生成失败也入库一条 status=REJECTED）
    public Instant createdAt;
    public Instant reviewedAt;
    public String reviewedBy;

    public WigShowcase() {
    }
}
