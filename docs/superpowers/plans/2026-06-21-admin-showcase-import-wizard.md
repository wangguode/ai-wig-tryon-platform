# 成品图工坊 — 现成素材导入 + 文件夹向导 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 方案 C — 管理端成品图工坊支持 **（A）单款多图手工上传关联** + **（B）客户素材根目录一键导入 31 款向导**；不调 AI，维护本体↔陈列↔商品关系，小程序刷新即见。

**Architecture:** 后端新增 `ShowcaseImportService`（存图 + 写 WigShowcase）与 `CustomerCatalogImportService`（移植 `scripts/lib/customer_catalog_v3.py` 规则到 Java，或 MVP 调用已验证 Python 脚本）；导入后 `Repository.reloadFromDisk()` 热加载；前端 `ShowcaseStudioTab` 双 Tab + `CustomerImportWizard` 三步（路径→预览→执行）。

**Tech Stack:** Spring Boot 3.3、React 19 admin 页、现有 JSON 仓库、Python v3 脚本（可复用作批量引擎）

**设计依据:** `docs/admin-showcase-manual-import-design.md`（Q1=C 已确认）

---

## 文件结构

| 路径 | 职责 |
|---|---|
| `service/ShowcaseImportService.java` | **新建** 手工 multipart 导入 showcase |
| `service/CustomerCatalogImportService.java` | **新建** 扫描客户文件夹、预览、执行导入 |
| `controller/AdminShowcaseController.java` | **改** +import / +integrity |
| `controller/AdminCatalogImportController.java` | **新建** preview + execute 向导 API |
| `repository/*Repository.java` | **改** 增加 `reloadFromDisk()` |
| `repository/CatalogRepository.java` | **改** 增加 reload + `saveProduct()` |
| `scripts/lib/customer_catalog_v3.py` | **不改**（Java 移植同规则，或 execute 调脚本） |
| `pages/admin/ShowcaseStudioTab.tsx` | **改** Tab：AI 生成 / 导入现成图 |
| `pages/admin/CustomerImportWizard.tsx` | **新建** 文件夹向导 UI |
| `api/admin.ts` | **改** 新 API 封装 |

**硬约束：** 手工/批量导入 **禁止调用 AiService**；`TryonService` 不改；`strategyUsed=customer-manual-import` / `customer-folder-import-v3`。

---

### Task 1: ShowcaseImportService + POST /admin/showcases/import

**Files:**
- Create: `wig-tryon-backend/src/main/java/com/wig/tryon/service/ShowcaseImportService.java`
- Modify: `wig-tryon-backend/src/main/java/com/wig/tryon/controller/AdminShowcaseController.java`

- [ ] **Step 1: 实现 import 服务**

```java
// ShowcaseImportService.java 核心方法
public List<WigShowcase> importFiles(
    Long wigAssetId,
    List<MultipartFile> files,
    List<Long> modelAssetIds,  // 与 files 等长，可 null 元素
    boolean autoApprove,
    boolean setFirstPrimary
) {
    wigAssetRepository.findById(wigAssetId)
        .orElseThrow(() -> new IllegalArgumentException("假发本体不存在: " + wigAssetId));
    if (files == null || files.isEmpty()) throw new IllegalArgumentException("至少 1 张图");
    if (files.size() > 15) throw new IllegalArgumentException("最多 15 张");
    List<WigShowcase> created = new ArrayList<>();
    for (int i = 0; i < files.size(); i++) {
        MultipartFile f = files.get(i);
        var saved = storageService.saveMultipart(f, "showcases/" + wigAssetId + "-" + UUID...");
        WigShowcase s = new WigShowcase();
        s.wigAssetId = wigAssetId;
        s.modelAssetId = modelAssetIds != null && i < modelAssetIds.size() ? modelAssetIds.get(i) : null;
        s.resultImage = saved.url();
        s.status = autoApprove ? "APPROVED" : "DRAFT";
        s.strategyUsed = "customer-manual-import";
        s.createdAt = Instant.now();
        if (autoApprove) { s.reviewedAt = Instant.now(); s.reviewedBy = "admin-import"; }
        s = showcaseRepository.save(s);
        created.add(s);
    }
    if (setFirstPrimary && !created.isEmpty()) {
        showcaseRepository.markPrimary(created.get(0).id);
    }
    return created;
}
```

- [ ] **Step 2: Controller 端点**

```java
@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<List<WigShowcase>> importShowcases(
    @RequestParam Long wigAssetId,
    @RequestParam("files") List<MultipartFile> files,
    @RequestParam(required = false) List<Long> modelAssetIds,
    @RequestParam(defaultValue = "true") boolean autoApprove,
    @RequestParam(defaultValue = "true") boolean setFirstPrimary) { ... }
```

- [ ] **Step 3: 验证**

```bash
curl -s -X POST http://localhost:8080/admin/showcases/import \
  -H "Authorization: Bearer demo-admin" \
  -F "wigAssetId=2" \
  -F "files=@/path/to/IMG_2154_C.jpg" \
  -F "autoApprove=true" | head -c 200
```

Expected: `code:0`, `strategyUsed` 含 manual-import

- [ ] **Step 4: Commit**（用户要求时）

---

### Task 2: GET /admin/showcases/integrity 关联体检

**Files:**
- Modify: `AdminShowcaseController.java` 或新建 `ShowcaseIntegrityService.java`

- [ ] **Step 1: 返回结构**

```json
{
  "wigAssetId": 2,
  "hasProduct": true,
  "productName": "减龄BOBO短发",
  "bareImageUrl": ".../wig-assets/2.jpg",
  "showcaseCount": 10,
  "approvedCount": 10,
  "hasPrimary": true,
  "bareEqualsShowcase": false,
  "missingModelAssetIdCount": 3
}
```

- [ ] **Step 2: 验证**

```bash
curl -s 'http://localhost:8080/admin/showcases/integrity?wigAssetId=2' \
  -H "Authorization: Bearer demo-admin"
```

---

### Task 3: CustomerCatalogImportService + 向导 API

**Files:**
- Create: `CustomerCatalogImportService.java`
- Create: `AdminCatalogImportController.java`

**批量引擎（二选一，计划采用 1）：**

1. **MVP（推荐）**：`execute` 调用 `ProcessBuilder` 跑 `python3 scripts/batch-import-customer-catalog.py`，然后 `reloadFromDisk()` — 零规则漂移，1h 内可演示  
2. **Java 全移植**：复制 v3 分类逻辑 — 更干净，+4h

- [ ] **Step 1: preview API**

```java
// POST /admin/catalog/import/preview
// Body: { "sourcePath": "/Users/.../发型素材-ai测试用-刘经理提供" }
// 校验：目录存在、含 V* 子文件夹，返回：
{
  "styleCount": 31,
  "styles": [
    { "code": "V802", "name": "减龄BOBO短发", "bareFile": "1_C.jpg", "showcaseCount": 10, "category": "短发" }
  ],
  "warnings": ["V801 陈列仅 9 张"]
}
```

Preview 可读目录列文件夹 + 用 Java 移植 `pick_wig_bare_from_names` / `showcase_names_from_v_folder`（仅 preview，轻量）。

- [ ] **Step 2: execute API**

```java
// POST /admin/catalog/import/execute
// Body: { "sourcePath": "...", "replaceExisting": true }
// 1) 调 Python 脚本（cwd=wig-tryon-backend）
// 2) wigAssetRepo.reloadFromDisk(); showcaseRepo.reload(); modelRepo.reload(); catalogRepo.reload();
// 3) 返回 verify-import-v3 摘要
```

- [ ] **Step 3: Repository reload**

在 `WigAssetRepository` / `WigShowcaseRepository` / `ModelAssetRepository` / `CatalogRepository` 增加：

```java
public synchronized void reloadFromDisk() {
    assets.clear(); // 或 products.clear + re-init seed + overlay
    loadFromDataDir();
    // CatalogRepository: re-run overlayCustomerCatalog()
}
```

- [ ] **Step 4: 验证**

```bash
curl -s -X POST http://localhost:8080/admin/catalog/import/execute \
  -H "Authorization: Bearer demo-admin" \
  -H "Content-Type: application/json" \
  -d '{"sourcePath":"/Users/wangguo/Desktop/VS/发型素材-ai测试用-刘经理提供","replaceExisting":true}'
```

Expected: `31 款式, 324 showcase`, 无需重启 JVM

---

### Task 4: 前端 — 工坊「导入现成图」Tab

**Files:**
- Modify: `ShowcaseStudioTab.tsx`
- Modify: `api/admin.ts`

- [ ] **Step 1: adminApi.importShowcases**

```typescript
importShowcases(token, wigAssetId, files: File[], opts?: { modelAssetId?: number; setFirstPrimary?: boolean }) {
  const fd = new FormData();
  fd.append('wigAssetId', String(wigAssetId));
  files.forEach(f => fd.append('files', f));
  fd.append('autoApprove', 'true');
  fd.append('setFirstPrimary', String(opts?.setFirstPrimary ?? true));
  return adminRequest<WigShowcase[]>(token, '/admin/showcases/import', { method: 'POST', body: fd });
}
```

- [ ] **Step 2: UI Tab 切换**

- 默认 Tab = **导入现成图**（演示模式）
- `<input type="file" multiple accept="image/*" />` 最多 15 张
- 上传后刷新 showcase 列表 + 调用 integrity 展示 ✓/✗

- [ ] **Step 3: 人工验证**

打开 `http://localhost:5173/#admin` → 成品图工坊 → 导入 Tab → 选 #02 → 上传 2 张 → 见网格 +2

---

### Task 5: 前端 — CustomerImportWizard（31 款一键导入）

**Files:**
- Create: `pages/admin/CustomerImportWizard.tsx`
- Modify: `AdminPage.tsx`（Dashboard 或 ShowcaseStudio 顶部入口）

**UI 三步：**

```
Step 1  素材根目录路径（文本框，预填默认路径）
Step 2  [预览] → 表格 31 款：SKU / 名称 / 本体文件 / 陈列数 / 分类
Step 3  [确认导入] → 进度 → 结果摘要（31款/324张/12模特）
```

- [ ] **Step 1: 组件 + API**

```typescript
previewCustomerImport(token, sourcePath: string)
executeCustomerImport(token, sourcePath: string, replaceExisting: boolean)
```

- [ ] **Step 2: 挂载入口**

Dashboard「客户素材一键导入」卡片，或 ShowcaseStudio 顶部按钮「批量导入向导」

- [ ] **Step 3: 验证**

向导执行后 → 小程序 `/api/recommend/products` 仍 31 款，管理端 integrity 全绿

---

### Task 6: 端到端验收

- [ ] 单款手工导入 3 张 → 小程序该款 gallery +3  
- [ ] 向导重导客户文件夹 → 31 款，modelAssetId 100%  
- [ ] 试戴日志仍 `WigAsset productImage`  
- [ ] 工坊 AI 生成 Tab 仍可独立使用（不破坏）

---

## 自审（对照 Q1=C）

| 需求 | Task |
|---|---|
| 不 AI 生成，只关联 | Task 1, 3（脚本无 AI） |
| 单款上传现成图 | Task 1, 4 |
| 文件夹一键 31 款 | Task 3, 5 |
| 本体↔陈列↔商品 | Task 2 integrity + 脚本 v3 |
| 小程序可见 | reloadFromDisk，无需重启 |

---

请输入 **`/code`** 开始实现（建议顺序 Task 1→2→3→4→5→6）。

Plan 路径：`docs/superpowers/plans/2026-06-21-admin-showcase-import-wizard.md`
