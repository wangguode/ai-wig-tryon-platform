package com.wig.tryon.model;

import java.util.List;

/**
 * GPT/通义千问VL 对用户照片的分析结果（结构化）。
 */
public class FaceAnalysisResult {

    public String faceShape;          // 脸型：圆脸/方脸/长脸/鹅蛋脸/心形脸
    public String hairlineType;       // 发际线类型：正常/M型发际线/发际线后移/宽发际线
    public String skinTone;           // 肤色：白皙/自然/偏深
    public String currentHairDesc;    // 当前发型描述
    public Boolean hasWhiteHair;      // 是否有白发
    public List<String> suggestions;  // 造型建议
    public List<Long> recommendedIds; // 推荐发型 ID 列表
    public String analysisText;       // 完整分析文案（展示用）

    public FaceAnalysisResult() {
    }
}
