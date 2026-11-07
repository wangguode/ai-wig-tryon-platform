import { HairStyle, Store } from '../data/mockData';

// 开发期通过 Vite 代理走 /api；生产可用 VITE_API_BASE 指定后端地址。
const API_BASE: string =
  (import.meta as any).env?.VITE_API_BASE ?? '';

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface FaceAnalysis {
  faceShape: string;
  hairlineType: string;
  skinTone: string;
  currentHairDesc: string;
  hasWhiteHair: boolean;
  suggestions: string[];
  recommendedIds: number[];
  analysisText: string;
}

export interface AnalyzeResult {
  sessionId: string;
  faceAnalysis: FaceAnalysis;
  recommendedProducts: HairStyle[];
  recommendationMatches?: RecommendationMatch[];
  recommendationStepOnly?: boolean;
  nextStepHint?: string;
  mock: boolean;
  aiMode?: 'real' | 'seed' | 'mock' | 'demo-fast';
}

export interface RecommendationMatch {
  wigProductId: number;
  name: string;
  matchScore: number;
  matchReasons: string[];
  colorFamily?: string;
  material?: string;
  sceneTags?: string[];
  targetCrowds?: string[];
  storeIds?: number[];
  hairlineCoverage?: number;
  whiteHairCoverage?: number;
}

export interface UploadResult {
  sessionId: string;
  photoUrl: string;
  message: string;
  faceCheckStatus?: 'PENDING' | 'PASSED' | 'FAILED';
}

export interface FaceCheckStatus {
  sessionId: string;
  status: 'PENDING' | 'PASSED' | 'FAILED';
  message?: string | null;
}

export interface TaskStatus {
  taskId: string;
  status: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | 'NOT_FOUND';
  progress?: number;
  resultImageUrl?: string | null;
  errorMessage?: string | null;
  aiMode?: 'real' | 'seed' | 'mock' | 'demo-fast';
  retryCount?: number;
  changeScore?: number | null;
  qualityNote?: string | null;
  strategyUsed?: string | null;
}

export interface FunnelStats {
  totalEvents: number;
  uploadPhoto: number;
  enterWall: number;
  addToWall: number;
  openCompare: number;
  openConsult: number;
  copyWechat: number;
  consultRate: string;
  compareRate: string;
}

export interface AiStatus {
  configuredMode: string;
  effectiveMode: 'real' | 'seed' | 'mock';
  strictReal: boolean;
  demoFastFaceCheck?: boolean;
  demoFastRecommend?: boolean;
  dashscopeKeyConfigured: boolean;
  storagePublicBaseUrl: string;
  storageUrlLikelyPublic: boolean;
  readyForRealDemo: boolean;
  suggestion: string;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, init);
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  const body = (await res.json()) as ApiResponse<T>;
  if (body.code !== 200) {
    throw new Error(body.message || '请求失败');
  }
  return body.data;
}

export const api = {
  uploadPhoto(file: File): Promise<UploadResult> {
    const form = new FormData();
    form.append('file', file);
    return request<UploadResult>('/api/photo/upload', { method: 'POST', body: form });
  },

  faceCheckStatus(sessionId: string): Promise<FaceCheckStatus> {
    return request<FaceCheckStatus>(`/api/photo/face-check/${sessionId}`);
  },

  analyze(sessionId: string, preference = '', category = ''): Promise<AnalyzeResult> {
    const params = new URLSearchParams();
    if (preference) params.set('preference', preference);
    if (category && category !== '全部') params.set('category', category);
    const q = params.toString() ? `?${params.toString()}` : '';
    return request<AnalyzeResult>(`/api/recommend/analyze/${sessionId}${q}`);
  },

  products(category?: string, faceType?: string): Promise<HairStyle[]> {
    const params = new URLSearchParams();
    if (category && category !== '全部') params.set('category', category);
    if (faceType) params.set('faceType', faceType);
    const q = params.toString() ? `?${params.toString()}` : '';
    return request<HairStyle[]>(`/api/recommend/products${q}`);
  },

  submitTryon(payload: {
    sessionId: string;
    wigProductId: number;
    adjustmentNote?: string;
    tryonType?: string;
    /** 演示模式下选择的模型 key，不传则后端按环境变量决定 */
    preferredModel?: string;
  }): Promise<{ taskId: string; status: string }> {
    return request('/api/tryon/submit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },

  taskStatus(taskId: string): Promise<TaskStatus> {
    return request<TaskStatus>(`/api/tryon/status/${taskId}`);
  },

  nearbyStores(limit = 10): Promise<Store[]> {
    return request<Store[]>(`/api/store/nearby?limit=${limit}`);
  },

  submitFeedback(payload: {
    taskId?: string;
    sentiment?: string;
    rating?: number;
    sessionId?: string;
    productId?: number;
    eventType?: string;
    channel?: string;
    note?: string;
  }): Promise<unknown> {
    return request('/api/feedback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
  },

  funnel(): Promise<FunnelStats> {
    return request<FunnelStats>('/api/feedback/funnel');
  },

  aiStatus(): Promise<AiStatus> {
    return request<AiStatus>('/api/system/ai-status');
  },
};

/** 轮询任务直至完成/失败/超时。 */
export async function pollTask(
  taskId: string,
  onProgress: (status: TaskStatus) => void,
  opts: { intervalMs?: number; timeoutMs?: number } = {},
): Promise<TaskStatus> {
  const interval = opts.intervalMs ?? 2000;
  // 默认与后端 dashscope.task-timeout-seconds（120s）对齐，避免前端先超时误判失败
  const timeout = opts.timeoutMs ?? 120000;
  const start = Date.now();
  // eslint-disable-next-line no-constant-condition
  while (true) {
    const status = await api.taskStatus(taskId);
    onProgress(status);
    if (status.status === 'SUCCESS' || status.status === 'FAILED' || status.status === 'NOT_FOUND') {
      return status;
    }
    if (Date.now() - start > timeout) {
      return { ...status, status: 'FAILED', errorMessage: '生成超时' };
    }
    await new Promise((r) => setTimeout(r, interval));
  }
}

/** 轮询异步人脸预检直至 PASSED / FAILED / 超时。 */
export async function pollFaceCheck(
  sessionId: string,
  opts: { intervalMs?: number; timeoutMs?: number } = {},
): Promise<FaceCheckStatus> {
  const interval = opts.intervalMs ?? 800;
  const timeout = opts.timeoutMs ?? 60000;
  const start = Date.now();
  // eslint-disable-next-line no-constant-condition
  while (true) {
    const fc = await api.faceCheckStatus(sessionId);
    if (fc.status === 'PASSED' || fc.status === 'FAILED') {
      return fc;
    }
    if (Date.now() - start > timeout) {
      return { sessionId, status: 'FAILED', message: '人脸检测超时，请重试' };
    }
    await new Promise((r) => setTimeout(r, interval));
  }
}
