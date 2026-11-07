// 运营管理后台 API：所有请求都需要带 Bearer token（来自 useAdminAuth）。
// 与 client.ts 共用 API_BASE，但鉴权头单独管理，避免误用到 C 端接口。

const API_BASE: string = (import.meta as any).env?.VITE_API_BASE ?? '';

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface WigAsset {
  id: number;
  code: string;
  name: string;
  productImage: string;
  productImageThumbnail?: string;
  descriptionForAi: string;
  category: string;
  colorFamily: string;
  tags?: string[];
  createdAt?: string;
  createdBy?: string;
}

export interface WigAssetMeta {
  code: string;
  name: string;
  descriptionForAi: string;
  category: string;
  colorFamily: string;
  tags?: string[];
}

export interface ModelAsset {
  id: number;
  name: string;
  portraitImage: string;
  gender: string;
  ageGroup: string;
  faceShape: string;
  tags?: string[];
  createdAt?: string;
  createdBy?: string;
}

export interface ModelAssetMeta {
  name: string;
  gender: string;
  ageGroup: string;
  faceShape: string;
  tags?: string[];
}

export interface WigShowcase {
  id: number;
  wigAssetId: number;
  modelAssetId: number | null;
  resultImage: string | null;
  status: 'DRAFT' | 'APPROVED' | 'REJECTED';
  isPrimary: boolean;
  changeScore: number | null;
  prompt: string | null;
  strategyUsed: string | null;
  errorMessage: string | null;
  createdAt: string;
  reviewedAt: string | null;
  reviewedBy: string | null;
}

async function adminRequest<T>(token: string, path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${token}`);
  const res = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (res.status === 401 || res.status === 403) {
    throw new Error('管理员 token 无效或已过期');
  }
  if (res.status === 404 || res.status === 503) {
    throw new Error('管理后台未启用，请联系管理员开启 WIG_ADMIN_ENABLED');
  }
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  const body = (await res.json()) as ApiResponse<T>;
  if (body.code !== 200) {
    throw new Error(body.message || '请求失败');
  }
  return body.data;
}

export const adminApi = {
  /** 列出全部假发本体（按 id 升序）。 */
  listWigAssets(token: string): Promise<WigAsset[]> {
    return adminRequest<WigAsset[]>(token, '/admin/wig-assets');
  },

  /**
   * 上传/更新假发本体。
   * 新建：id 不传；更新：id 必传。
   * file 必传——即使只改元数据，也建议重新上传同一张图，简化前后端协议。
   */
  uploadWigAsset(token: string, file: File, meta: WigAssetMeta, id?: number): Promise<WigAsset> {
    const form = new FormData();
    form.append('file', file);
    form.append('meta', JSON.stringify(meta));
    if (id != null) form.append('id', String(id));
    return adminRequest<WigAsset>(token, '/admin/wig-assets', {
      method: 'POST',
      body: form,
    });
  },

  /** 仅更新元数据（不替换图）。 */
  updateMeta(token: string, id: number, meta: Partial<WigAssetMeta>): Promise<WigAsset> {
    return adminRequest<WigAsset>(token, `/admin/wig-assets/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(meta),
    });
  },

  deleteWigAsset(token: string, id: number): Promise<boolean> {
    return adminRequest<boolean>(token, `/admin/wig-assets/${id}`, { method: 'DELETE' });
  },

  /**
   * 用 AI 文生图按 descriptionForAi 重画一张白底纯本体图，覆盖 productImage。
   * 同步阻塞 30~60s，调用方必须自行 loading 蒙层。
   */
  regenerateBareWig(token: string, id: number): Promise<WigAsset> {
    return adminRequest<WigAsset>(token, `/admin/wig-assets/${id}/regenerate-bare`, {
      method: 'POST',
    });
  },

  // -------- 模特库 --------
  listModelAssets(token: string): Promise<ModelAsset[]> {
    return adminRequest<ModelAsset[]>(token, '/admin/model-assets');
  },

  uploadModelAsset(token: string, file: File, meta: ModelAssetMeta, id?: number): Promise<ModelAsset> {
    const form = new FormData();
    form.append('file', file);
    form.append('meta', JSON.stringify(meta));
    if (id != null) form.append('id', String(id));
    return adminRequest<ModelAsset>(token, '/admin/model-assets', { method: 'POST', body: form });
  },

  deleteModelAsset(token: string, id: number): Promise<boolean> {
    return adminRequest<boolean>(token, `/admin/model-assets/${id}`, { method: 'DELETE' });
  },

  // -------- 成品图工坊 --------
  listShowcases(token: string, wigAssetId?: number): Promise<WigShowcase[]> {
    const q = wigAssetId != null ? `?wigAssetId=${wigAssetId}` : '';
    return adminRequest<WigShowcase[]>(token, `/admin/showcases${q}`);
  },

  generateShowcases(token: string, wigAssetId: number, modelAssetIds: number[]): Promise<WigShowcase[]> {
    return adminRequest<WigShowcase[]>(token, '/admin/showcases/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ wigAssetId, modelAssetIds }),
    });
  },

  approveShowcase(token: string, id: number, isPrimary: boolean): Promise<WigShowcase> {
    return adminRequest<WigShowcase>(token, `/admin/showcases/${id}/approve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ isPrimary }),
    });
  },

  rejectShowcase(token: string, id: number): Promise<WigShowcase> {
    return adminRequest<WigShowcase>(token, `/admin/showcases/${id}/reject`, { method: 'POST' });
  },

  deleteShowcase(token: string, id: number): Promise<boolean> {
    return adminRequest<boolean>(token, `/admin/showcases/${id}`, { method: 'DELETE' });
  },

  importShowcases(
    token: string,
    wigAssetId: number,
    files: File[],
    opts?: { modelAssetId?: number; setFirstPrimary?: boolean },
  ): Promise<WigShowcase[]> {
    const form = new FormData();
    form.append('wigAssetId', String(wigAssetId));
    files.forEach((f) => form.append('files', f));
    form.append('autoApprove', 'true');
    form.append('setFirstPrimary', String(opts?.setFirstPrimary ?? true));
    if (opts?.modelAssetId != null) {
      files.forEach(() => form.append('modelAssetIds', String(opts.modelAssetId)));
    }
    return adminRequest<WigShowcase[]>(token, '/admin/showcases/import', { method: 'POST', body: form });
  },

  showcaseIntegrity(token: string, wigAssetId: number): Promise<ShowcaseIntegrity> {
    return adminRequest<ShowcaseIntegrity>(token, `/admin/showcases/integrity?wigAssetId=${wigAssetId}`);
  },

  previewCustomerImport(token: string, sourcePath: string): Promise<CustomerImportPreview> {
    return adminRequest<CustomerImportPreview>(token, '/admin/catalog/import/preview', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sourcePath }),
    });
  },

  executeCustomerImport(token: string, sourcePath: string): Promise<CustomerImportResult> {
    return adminRequest<CustomerImportResult>(token, '/admin/catalog/import/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sourcePath }),
    });
  },

  /** 用任意有效 token 调一次列表当作"探活+登录"——成功即视为已认证。 */
  ping(token: string): Promise<WigAsset[]> {
    return adminApi.listWigAssets(token);
  },

  // -------- Dashboard / 一键填充 --------
  dashboard(token: string): Promise<DashboardData> {
    return adminRequest<DashboardData>(token, '/admin/dashboard');
  },

  startBareWigBatch(token: string, ids?: number[]): Promise<BatchJob> {
    return adminRequest<BatchJob>(token, '/admin/dashboard/regenerate-bare-wigs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids: ids ?? [] }),
    });
  },

  startFillShowcases(
    token: string,
    modelAssetId: number,
    wigAssetIds?: number[],
    autoApprove = true,
    autoPrimary = true,
  ): Promise<BatchJob> {
    return adminRequest<BatchJob>(token, '/admin/dashboard/fill-showcases', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ modelAssetId, wigAssetIds: wigAssetIds ?? [], autoApprove, autoPrimary }),
    });
  },

  startModelPortraitBatch(token: string, ids?: number[]): Promise<BatchJob> {
    return adminRequest<BatchJob>(token, '/admin/dashboard/regenerate-model-portraits', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ids: ids ?? [] }),
    });
  },

  jobStatus(token: string, jobId: string): Promise<BatchJob> {
    return adminRequest<BatchJob>(token, `/admin/dashboard/jobs/${jobId}`);
  },

  /** 演示重置：清空全部 showcase + 把 wigAsset/modelAsset 还原到 classpath 种子。 */
  resetDemoData(token: string): Promise<{ ok: boolean; message: string }> {
    return adminRequest(token, '/admin/dashboard/reset', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ confirm: 'RESET-DEMO' }),
    });
  },
};

export interface BatchJobItem {
  entityId: number;
  entityName: string;
  state: 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';
  reason: string | null;
  resultUrl: string | null;
  updatedAt: string;
}

export interface BatchJob {
  jobId: string;
  type: 'REGENERATE_BARE_WIG' | 'FILL_SHOWCASES' | 'REGENERATE_MODEL_PORTRAIT';
  startedAt: string;
  finishedAt: string | null;
  status: 'RUNNING' | 'DONE' | 'CANCELLED';
  total: number;
  /** 后端用 AtomicInteger 序列化，前端拿到的是数字。 */
  completed: number;
  failed: number;
  items: BatchJobItem[];
  description: string;
}

export interface DashboardData {
  wigAssetTotal: number;
  wigAssetRealCount: number;
  modelAssetTotal: number;
  modelAssetRealCount: number;
  showcaseTotal: number;
  showcaseApprovedCount: number;
  showcasePrimaryCount: number;
  productTotal: number;
  productWithPrimaryCount: number;
  recentJobs: BatchJob[];
  ready: boolean;
}

export interface ShowcaseIntegrity {
  wigAssetId: number;
  assetName: string;
  bareImageUrl: string;
  hasProduct: boolean;
  productName: string | null;
  showcaseCount: number;
  approvedCount: number;
  hasPrimary: boolean;
  bareEqualsShowcase: boolean;
  missingModelAssetIdCount: number;
  ok: boolean;
}

export interface CustomerImportPreview {
  sourcePath: string;
  vStyleCount: number;
  estimatedStyleCount: number;
  styles: Array<{
    folderName: string;
    code: string;
    bareFile: string | null;
    showcaseCount: number;
    chunkCount?: number;
  }>;
  warnings: string[];
}

export interface CustomerImportResult {
  sourcePath: string;
  wigAssetCount: number;
  showcaseCount: number;
  modelCount: number;
  productCount: number;
  log: string;
}
