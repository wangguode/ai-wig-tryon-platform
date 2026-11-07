# 成品图工坊 — 客户现成素材手工关联设计（v1 草稿）

> **状态**：已实现（方案 C）— 见 `/code` 交付  
> **背景**：客户素材库已有棚拍图（`_C` 本体 + `IMG_` 佩戴图），**不需要 AI 生成**；需在管理端把「假发本体 ↔ 成品陈列 ↔ 模特 ↔ 上架商品」关联清楚，小程序 C 端才能正确展示。  
> **关联**：`docs/wig-asset-system-design.md`、`batch-import-customer-catalog.py` v3

---

## 0. 一句话理解

在 **成品图工坊** 增加「**导入现成图**」能力：运营选中一款 **WigAsset（本体）**，批量上传客户 **成品棚拍图** 作为 **WigShowcase**，可选绑定 **ModelAsset**，审核设主图后 C 端立即可见；**试戴 AI 仍只用本体图**，不用成品图。

---

## 1. 现状：管理端现在能做什么、不能做什么

| 能力 | 入口 | 现状 |
|---|---|---|
| 上传假发本体 | `#admin` → 假发本体 | ✅ 有，`POST /admin/wig-assets` |
| 上传模特 | `#admin` → 模特库 | ✅ 有 |
| **上传客户成品图（不经过 AI）** | 成品图工坊 | ❌ **没有** — 只有「选模特 → AI 生成」 |
| 审核 / 设主图 / 删除 showcase | 成品图工坊 | ✅ 有（针对已入库的 showcase） |
| 批量导入客户素材 | 命令行脚本 | ✅ `batch-import-customer-catalog.py` v3（31 款已跑过） |
| 上架到小程序商品列表 | 管理端 | ⚠️ 依赖 `customer-catalog.json` 里有对应 `WigProduct`（id = wigAssetId） |

**结论**：关联逻辑 **数据模型已支持**（`WigShowcase.modelAssetId` 注释写明可空），但 **UI/API 缺「手工导入成品图」**；目前只能靠 Python 脚本离线关联。

---

## 2. 目标链路（你要的「只关联、不生成」）

```
客户素材文件夹
├── 1_C.jpg / 正_C.png     ──→  WigAsset.productImage     （AI 试戴参考，C 端不可见）
└── IMG_*.jpg / 棚拍佩戴图   ──→  WigShowcase.resultImage   （C 端封面 + 画廊）
         │                              │
         │                              ├── wigAssetId  → 本体
         │                              └── modelAssetId → 模特（可选）
         ▼
WigProduct（商品库，id = wigAssetId）
         ▼
小程序 /api/recommend/products → image + galleryImages
```

**硬约束（不变）**：
- C 端试戴：`用户自拍 + WigAsset.productImage`（见 `TryonService`）
- C 端选品：`WigShowcase` 审核通过且 `isPrimary` 的图作封面

---

## 3. 管理端操作流程设计（推荐）

### 3.1 单款手工关联（运营日常）

在 **成品图工坊** 增加第二模式：**「导入现成图」**（与「AI 生成」Tab 并列）

| 步骤 | 操作 | 系统行为 |
|---|---|---|
| 1 | 左侧选一款 **假发本体**（或先在「假发本体」页上传 `_C` 静物图） | 加载该 id 已有 showcase 列表 |
| 2 | 点 **「上传客户成品图」**，多选 1~15 张 JPG/PNG | 文件存 `storage/showcases/`，创建 `WigShowcase` |
| 3 | （可选）每张图下拉选 **模特**；不选则 `modelAssetId=null` | 允许客户图里已含模特、未单独建模特库 |
| 4 | 上传完成默认 `status=APPROVED`（或 DRAFT + 一键全审） | 演示可省审核步 |
| 5 | 选一张点 **「设为主图」** | `isPrimary=true`，同 wigAssetId 其余自动取消主图 |
| 6 | 确认 **商品库** 有对应 `WigProduct`（同 id） | 无则提示「去商品上架」或向导内一步补全 |

刷新小程序 → 封面 + 画廊更新。

### 3.2 批量关联（客户交付 31 款文件夹）

两种路径（见 §5 方案对比）：

- **继续用脚本**（已验证）：`python3 scripts/batch-import-customer-catalog.py` — 适合一次性 31 款
- **管理端批量向导**（待开发）：选本地文件夹根目录 → 按 V801 子文件夹规则自动拆本体/陈列 → 预览 → 一键写入

### 3.3 关联关系怎么「维护好」

| 关系 | 字段 | 校验规则 |
|---|---|---|
| 本体 ↔ 商品 | `WigProduct.wigAssetId` = `WigAsset.id` | 1:1，id 建议相同 |
| 本体 ↔ 陈列 | `WigShowcase.wigAssetId` | 1:N，N=10~15 |
| 陈列 ↔ 模特 | `WigShowcase.modelAssetId` | 可选；有则后台可筛选「同模特其他款」 |
| 陈列 ↔ C 端封面 | `isPrimary` + `status=APPROVED` | 每 wigAssetId 恰好 1 张主图 |
| 陈列 ≠ AI 输入 | — | 校验：`resultImage` URL ≠ `productImage` URL |

管理端 **关联面板**（选中某本体时右侧展示）：

```
本体 #02  V802 · 减龄BOBO
├── 本体图：[缩略图] 1_C.jpg
├── 商品：WigProduct #02 ✓
├── 陈列：10 张（已通过 10 / 主图 1）
└── 模特：M02 小慧（3 张）、未标注（7 张）
```

---

## 4. 需要新增的 API（设计，未实现）

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/admin/showcases/import` | multipart：`files[]` + `wigAssetId` + 可选 `modelAssetId` / 每张 meta JSON |
| `POST` | `/admin/showcases/import-batch` | `{ wigAssetId, items: [{ fileUrl或path, modelAssetId }] }` 脚本/向导用 |
| `POST` | `/admin/showcases/{id}/set-primary` | 已有 approve+isPrimary，可复用 |
| `GET` | `/admin/showcases/integrity?wigAssetId=` | 返回本体/陈列/商品是否齐全、URL 是否撞车 |
| `POST` | `/admin/products` | （可选 P1）从 WigAsset 一键生成 WigProduct 上架 |

**import 请求示例（设计）**：

```json
POST /admin/showcases/import
Content-Type: multipart/form-data

wigAssetId=2
files=@IMG_2154_C.jpg
files=@IMG_2155_C.jpg
modelAssetIds=2,2,null,null   // 与 files 顺序对应，可空
autoApprove=true
setFirstPrimary=true
```

**WigShowcase 写入字段**：

```text
strategyUsed = "customer-manual-import"
status       = APPROVED（或 DRAFT）
modelAssetId = 可选
resultImage  = /files/showcases/...
```

三模式：手工导入 **不调 AiService**，与 real/seed/mock 试戴无关。

---

## 5. 三方案对比

| 维度 | **A. 工坊加「上传现成图」**（推荐） | B. 仅脚本 + 工坊只读审核 | C. 完整批量向导（选文件夹） |
|---|---|---|---|
| 做法 | API import + 工坊 UI 多图上传 | 继续 v3 脚本；工坊删/审/主图 | A + 扫描 `V8xx` 文件夹一键导入 |
| 运营能否纯点界面 | ✅ 单款可以 | ❌ 批量仍要跑命令 | ✅ 批量也可 |
| 开发量 | ~2~3h | ~0（现状） | ~1d |
| 演示友好 | ★★★★★ | ★★★（要讲脚本） | ★★★★★ |
| 适合今天 | **P0 单款上传即可演示** | 已可用 | 演示后 |

**推荐：A（P0）+ 保留 v3 脚本做批量**；C 作为 P1。

---

## 6. 成品图工坊 UI 改造（方案 A）

```
┌─────────────────────────────────────────────────────────────┐
│  成品图工坊    [ AI 生成 ]  [ 导入现成图 ★默认演示 ]          │
├──────────────┬──────────────────────────────────────────────┤
│ 假发本体列表 │  ① 选中 #02 减龄BOBO                          │
│  #01 ...     │  ② [ 选择文件 ] 支持多选，最多 15 张           │
│  #02 ●       │  ③ 可选：统一指定模特 / 每张单独指定             │
│  #03 ...     │  ④ [ 上传并关联 ] → 进度条 → 列表刷新          │
│              │  ⑤ 已有陈列：网格 + 设主图 / 删除               │
│              │  ⑥ 完整性：✓本体 ✓商品 ✓主图 ✓陈列≠本体        │
└──────────────┴──────────────────────────────────────────────┘
```

**与「AI 生成」区隔离**：导入 Tab 不展示模特多选 + 生成按钮，避免误点 AI。

---

## 7. 你现在不用等新功能也能做的事（临时）

在 **方案 A 未开发前**：

1. **已导入的 31 款**：`#admin` → 成品图工坊 → 选本体 → **查看/删重复 showcase / 设主图**（不需 AI）
2. **新款或重导**：在项目根执行  
   `python3 wig-tryon-backend/scripts/batch-import-customer-catalog.py`  
   然后重启后端
3. **只换本体图**：假发本体页 → 编辑/upload 覆盖 `productImage`
4. **小程序看不到新款**：检查 `data/customer-catalog.json` 是否有同 id 商品

---

## 8. 验收标准

| # | 操作 | 预期 |
|---|---|---|
| 1 | 工坊导入 3 张客户佩戴图到某本体 | showcase +3，`strategyUsed=customer-manual-import` |
| 2 | 设主图 | 小程序该款封面变为新主图 |
| 3 | 试戴该款 | 日志 `使用 WigAsset productImage`，非 showcase URL |
| 4 | 完整性检查 | 本体 URL ≠ 任一 showcase URL |
| 5 | 删重复 showcase | 画廊张数减少，无重复 id |

---

## 9. 待确认（请先拍板 1 题）

### Q1 — 管理端主工作方式？

- **A.** 方案 A：工坊加「导入现成图」（单款多图上传）+ 脚本保留批量 — **推荐**
- **B.** 不加 UI，继续只用 v3 脚本 + 工坊审核/删图
- **C.** 方案 A + C：还要「选客户文件夹一键导入 31 款」向导

---

**请回复 Q1（A/B/C）。确认后进入 `/plan` 拆实现任务；确认前不写代码。**

设计文档：`docs/admin-showcase-manual-import-design.md`
