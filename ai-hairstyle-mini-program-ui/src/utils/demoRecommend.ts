import { AnalyzeResult, RecommendationMatch } from '../api/client';
import { HairStyle } from '../data/mockData';

/** 与后端 RecommendService.demoFastRecommend 文案对齐，演示不调 VL。 */
export function buildDemoAnalyzeResult(
  products: HairStyle[],
  category: string,
  sessionId = 'demo-local',
): AnalyzeResult | null {
  if (!products.length) return null;

  const catLabel = !category || category === '全部' ? '全部分类' : category;
  let pool = products.filter((p) => category === '全部' || !category || p.category === category);
  if (pool.length === 0) pool = products;
  pool = pool.slice(0, 2);

  const recommendedIds = pool.map((p) => p.id);
  const recommendationMatches: RecommendationMatch[] = pool.map((p, i) => ({
    wigProductId: p.id,
    name: p.name,
    matchScore: 96 - i * 2,
    matchReasons: [
      `${catLabel}门店试戴热度较高`,
      i === 0 ? '刘海与发际线修饰友好' : '发色与日常通勤场景适配',
    ],
  }));

  const names = pool.map((p) => `「${p.name}」`).join('、');

  return {
    sessionId,
    mock: false,
    aiMode: 'demo-fast' as AnalyzeResult['aiMode'],
    recommendationStepOnly: true,
    nextStepHint: '点击下方推荐款或浏览素材库，选中后即可一键 AI 试戴',
    recommendedProducts: pool,
    recommendationMatches,
    faceAnalysis: {
      faceShape: '鹅蛋脸',
      hairlineType: '正常',
      skinTone: '自然偏暖',
      currentHairDesc: '发量适中，便于与假发款式做直观对比',
      hasWhiteHair: false,
      suggestions: [
        '鹅蛋脸适合略带层次的中长发，能拉长视觉比例',
        '若日常通勤，建议优先尝试自然色系、易打理款式',
        '试戴时可对比刘海长度，找到最修饰发际线的一版',
      ],
      recommendedIds,
      analysisText:
        `您好！已根据您上传的照片完成初步形象分析：脸型偏鹅蛋脸，三庭比例较为均衡，`
        + `肤色自然偏暖，整体气质大方亲和。结合${catLabel}素材库中的门店热销数据，`
        + `我们为您优先挑选了 ${names} 两款——兼顾修饰脸型与日常佩戴的真实感。`
        + `您可直接点选试戴，也可以在下方浏览更多款式。`,
    },
  };
}

/** 选发型页推荐加载时的分步提示（演示用，营造分析过程感） */
export const RECOMMEND_LOADING_STEPS = [
  '正在读取您的照片特征…',
  '正在分析脸型、发际线与肤色…',
  '正在从素材库匹配适合您的款式…',
  '即将为您呈现专属推荐…',
] as const;

export const RECOMMEND_MIN_REVEAL_MS = 1600;
