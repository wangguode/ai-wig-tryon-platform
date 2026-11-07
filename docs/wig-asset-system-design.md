# 假发资产分层 & 运营管理后台 系统化设计

> **状态**：v0.1 草稿（讨论用，未编码）
> **作者**：Cursor + 老板  
> **目标读者**：项目老板（决策） / 后端 / 前端 / 运营  
> **解决的根本问题**：当前喂给 AI 的"假发参考图"是**模特戴假发的成品图**，导致模型被模特的脸/衣服/背景误导，把用户照片整张重画。

---

## 1. 现状与根本问题

### 1.1 当前数据结构（错的）
`WigProduct` 里三个字段全是同一张图（见 `CatalogRepository.java:243-245`）：

```
image           = "模特戴假发的成品图"  // 用户看
refImage        = "模特戴假发的成品图"  // 喂给 AI 当"参考"
seedResultImage = "模特戴假发的成品图"  // 演示模式直接显示
```

### 1.2 试戴生成时发生了什么
1. 用户上传自拍 → 后端拿到 `userImage`
2. 用户选了一款假发 → 后端把 `product.refImage`（**模特戴假发图**）当成"假发参考图"
3. 调 AI：`generateTryon(userImage, refImage, prompt)`
4. AI 收到两张人像，prompt 又写"参考图轮廓"——**它学的是模特那张图的整体构图**（脸/衣/背景），而不是"只换发型"

### 1.3 老板的洞察（核心）
> "你应该传的是**用户的原图 + 真实假发本体图**，不是模特戴假发图。模特戴假发图是给用户看的，不是给模型学的。"

这是质的提升——**展示资产** ≠ **AI 推理资产**，必须分离。

---

## 2. 资产分层抽象（核心设计）

把"假发"这个业务概念拆成 4 类独立资产：

| 编号 | 资产 | 内容 | 谁产生 | 谁看 | 喂给 AI? |
|---|---|---|---|---|---|
| ① | **WigAsset**（假发本体） | 纯假发产品图（白底 / 透明底，无模特、无人脸） | 运营上传 | **运营内部** | **是**（推理用） |
| ② | **ModelAsset**（模特库） | 多种脸型/性别/年龄的纯模特肖像 | 运营上传 | **运营内部** | 是（生成 Showcase 时） |
| ③ | **WigShowcase**（陈列成品图） | ①+②→AI 合成 →"模特戴这款假发的样子" | AI 生成 + 运营审核 | **C 端用户** | 否 |
| ④ | **UserTryon**（用户试戴图） | 用户自拍 + ①→AI 合成 →"我戴上的样子" | 用户触发 AI | 该用户 | 否 |

### 关键洞察

- ①（本体图）是**唯一**喂给图像模型的"假发参考"
- ③ 是用户在选品页面看到的"商品图"，但**对模型不可见**
- ④ 用户私有，不持久化到产品库

---

## 3. 数据模型

### 3.1 新增表 / 实体

```text
WigAsset                        # 假发本体（运营核心资产）
├── id
├── code                        # SKU 编码，运营手动输入
├── name                        # 内部名（运营辨识）
├── productImage                # 假发本体图 URL（白底/透明底，无模特）
├── productImageThumbnail       # 缩略图（管理后台列表用）
├── descriptionForAi            # 喂给 AI 的中文描述（长度/卷直/颜色/分缝/刘海...）
├── category                    # 短发/中长发/长发/卷发
├── colorFamily                 # 自然黑/深棕/亚麻/...
├── tags                        # 风格标签
├── createdAt
└── createdBy                   # 哪个运营上传

ModelAsset                      # 模特库
├── id
├── name                        # 模特命名（小雅/小峰...）
├── portraitImage               # 纯模特肖像 URL
├── gender                      # M/F
├── ageGroup                    # young/mid/elder
├── faceShape                   # 圆脸/方脸/长脸/瓜子
├── tags                        # 职场/学生/宝妈...
└── createdAt

WigShowcase                     # 陈列成品图（一个本体可对多个模特生成多张）
├── id
├── wigAssetId                  # ① 外键
├── modelAssetId                # ② 外键（可空——支持后续直接传"商品图"覆盖）
├── resultImage                 # ③ 成品图 URL
├── status                      # DRAFT / APPROVED / REJECTED
├── isPrimary                   # 是否产品默认陈列图
├── changeScore                 # 自动质量评分（与现有变化分逻辑复用）
├── createdAt
└── reviewedBy / reviewedAt
```

### 3.2 改造 `WigProduct`

```text
WigProduct
+ wigAssetId                  # 关联 ① 本体（必须）
+ primaryShowcaseId           # 关联 ③ 默认陈列图（C 端展示）
- refImage                    # 弃用（向下兼容期保留，跳过读取）
- image                       # 改为 derivedFromShowcase（自动从 primaryShowcaseId 取）
```

### 3.3 存储策略（MVP）

为了不引入数据库迁移成本：
- **MVP**：JSON 文件存储（沿用 `CatalogRepository` 的 mock 数据写法）
  - `wig-tryon-backend/src/main/resources/data/wig-assets.json`
  - `wig-tryon-backend/src/main/resources/data/model-assets.json`
  - `wig-tryon-backend/src/main/resources/data/wig-showcases.json`
- **图片文件**：本地磁盘 `data/uploads/{wig-assets|model-assets|showcases}/<id>.png`，由 `FileController` 暴露
- **未来**：迁 PostgreSQL / 对象存储，接口不变

---

## 4. 角色与权限

### 4.1 角色定义

| 角色 | 能做什么 | 不能做什么 |
|---|---|---|
| **C 端用户** | 上传自拍、选假发（看 ③）、生成 ④ | 看不到 ① ②，看不到管理后台 |
| **运营** | 上传 ① ②、生成 ③、审核 ③、管理产品 | 看不到其他用户的 ④ |
| **管理员**（MVP=运营） | 同上 | — |

### 4.2 鉴权方案（MVP 简化）

不引入完整账号体系，先用**单一管理员 token**：

```yaml
# application.yml
app:
  admin:
    token: ${WIG_ADMIN_TOKEN:}    # 环境变量；空则关闭管理后台
    enabled: ${WIG_ADMIN_ENABLED:false}
```

请求侧：

```
Authorization: Bearer ${admin-token}
```

后端拦截器 `AdminAuthInterceptor` 只放行 `/admin/**`，token 不匹配返回 401。
**未来**：换成"运营账号 + 角色 + 操作日志"。

---

## 5. 运营管理后台 UI 流程

### 5.1 信息架构

```
/admin                     登录页（输入 token）
/admin/dashboard           概览（资产数 / 待审核 Showcase 数）
/admin/wig-assets          ① 假发本体管理
  /admin/wig-assets/new      上传 + 录入元数据
  /admin/wig-assets/:id      详情 + 已生成的 Showcase 列表
/admin/model-assets        ② 模特库管理
  /admin/model-assets/new    上传 + 标注脸型/性别
/admin/showcase-studio     ③ 成品图工坊（核心生产入口）
/admin/products            产品上架（绑定 ① 和 ③）
```

### 5.2 关键流程：生成一款新假发的全链路

```
┌──────────────────────────────────────────────────────────────────────┐
│  Step 1  上传"光秃秃假发本体图"                                      │
│  └─→ POST /admin/wig-assets   返回 wigAssetId                        │
│                                                                      │
│  Step 2  进入 Showcase Studio                                        │
│  └─→ 左侧：选 wigAssetId    右侧：选 1~N 个 modelAssetId             │
│                                                                      │
│  Step 3  点击"AI 生成成品图"                                         │
│  └─→ POST /admin/showcases/generate                                  │
│      Body: { wigAssetId, modelAssetIds:[...] }                       │
│      → 后端为每个模特并发调用 AI，本质上和用户试戴是同一套流程：     │
│         模特照片 + 假发本体图 → AI                                   │
│      → 每张成品图自动落 status=DRAFT                                 │
│                                                                      │
│  Step 4  审核                                                        │
│  └─→ 运营 review 多张候选图，选 1~N 张点击"通过 + 设为主图"          │
│      POST /admin/showcases/:id/approve  { isPrimary: true }          │
│                                                                      │
│  Step 5  上架产品                                                    │
│  └─→ POST /admin/products                                            │
│      Body: { wigAssetId, primaryShowcaseId, name, price, tags... }   │
│      → C 端立即可见                                                  │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.3 UI 风格

复用现有前端的 dark theme + glass card，单独 `/admin` 路由，**不混入用户主流程**。组件：
- `AdminLoginPage`
- `AdminLayout`（左侧菜单 + 右侧内容区）
- `WigAssetListPage` / `WigAssetUploadModal`
- `ModelAssetListPage`
- `ShowcaseStudioPage`（核心：双栏选择 + 生成 + 审核）
- `ProductBindPage`

---

## 6. AI 调用层改造

### 6.1 用户试戴流程（C 端，本次主要修复点）

```
旧：tryonRequest → product.refImage（=模特成品图） → AI
新：tryonRequest → product.wigAssetId → wigAsset.productImage（=本体图） → AI
```

`TryonService.runGeneration` 改造：

```java
// 伪代码
WigProduct product = catalog.findById(productId);
WigAsset asset = wigAssetRepo.findById(product.wigAssetId);
if (asset == null || asset.productImage == null) {
    // 兼容老数据：找不到本体图就回退到 product.image，并打 WARN
    fallback();
}
String wigImageForAi = asset.productImage;       // ← 关键变化
String wigDescription = asset.descriptionForAi;  // 用于 prompt 拼接
```

### 6.2 Showcase 生成流程（运营端，新流程）

```
showcaseRequest → modelAsset.portraitImage + wigAsset.productImage → AI
```

复用现有 `AiService.generateTryon` 抽象——**它本来就是"两张图融合"的语义**，只不过左图换成"模特照片"而非"用户照片"，整个 AI 层不用改接口。

### 6.3 Prompt 调整

`PromptBuilder` 新增专用方法：

```java
// 用户试戴：第一张图=用户自拍，第二张图=纯假发本体（已无人脸干扰）
public String tryonPromptWithBareWig(WigProduct, FaceAnalysis, ...) {
    "第一张图是用户本人的自拍，第二张图是一款假发本体（无模特）。
     请保留第一张图中人物的全部信息（脸/衣/背景），
     仅把头发区域替换为第二张图所示的假发，匹配其颜色/长度/卷直/分缝/刘海。"
}

// 运营 Showcase 生成：第一张图=模特，第二张图=本体
public String showcasePromptWithBareWig(ModelAsset, WigAsset) {
    "第一张图是模特，第二张图是假发本体。
     请保留模特的脸/衣/背景，仅把头发替换为第二张图的假发样式..."
}
```

### 6.4 三模式（real/seed/mock）一致性（硬约束）

| 模式 | 用户试戴 | Showcase 生成 |
|---|---|---|
| **real** | 调 AI，本体图作为参考 | 调 AI，本体图 + 模特图 |
| **seed** | 找该 wigAsset 的 primaryShowcase 的 resultImage 作"演示效果"返回 | 直接复制 wigAsset.productImage 作 placeholder（不真生成） |
| **mock** | 占位图 | 占位图 |

---

## 7. 后端 API 接口

### 7.1 运营端（鉴权 `Authorization: Bearer ${token}`）

```
POST   /admin/wig-assets                  multipart: file + meta
GET    /admin/wig-assets                  分页列表
GET    /admin/wig-assets/{id}             详情
DELETE /admin/wig-assets/{id}

POST   /admin/model-assets                multipart: file + meta
GET    /admin/model-assets
DELETE /admin/model-assets/{id}

POST   /admin/showcases/generate          { wigAssetId, modelAssetIds[] }
GET    /admin/showcases?wigAssetId=...    候选图列表
POST   /admin/showcases/{id}/approve      { isPrimary }
POST   /admin/showcases/{id}/reject

POST   /admin/products                    上架（绑定 wigAssetId + primaryShowcaseId）
PUT    /admin/products/{id}
DELETE /admin/products/{id}
```

### 7.2 C 端（无鉴权变更）

无新增接口；`/api/products/...` 返回时把 `image` 字段映射为 `primaryShowcase.resultImage`，**对前端透明**。

### 7.3 统一返回体

继续遵守 `ApiResponse<T>`（项目硬约束之一）。

---

## 8. 前端实现

### 8.1 新增页面（运营端）

| 路径 | 组件 | 备注 |
|---|---|---|
| `/admin` | `AdminLoginPage` | token 输入框 |
| `/admin/dashboard` | `AdminDashboard` | 简单数字面板 |
| `/admin/wig-assets` | `WigAssetListPage` + `WigAssetUploadModal` | |
| `/admin/model-assets` | `ModelAssetListPage` + `ModelAssetUploadModal` | |
| `/admin/showcase-studio` | `ShowcaseStudioPage` | 核心页 |
| `/admin/products` | `ProductBindPage` | 上架 |

路由：用 React 状态机分发即可（项目硬约束：不引入 React Router），新增 `view='admin'` 一级状态分支。
鉴权：`localStorage.adminToken`，在 fetch 拦截层注入 `Authorization` header。

### 8.2 用户端改动（最小）

仅一处：`SelectHairPage` 当前用 `product.image` 展示——改为读取后端返回的 `primaryShowcase.resultImage`，**字段层面已被后端封装**，前端代码几乎无感。

---

## 9. 落地分阶段（MVP-first）

| 阶段 | 内容 | 预估 | 阻塞用户演示？ |
|---|---|---|---|
| **P0** | WigAsset 数据模型 + JSON 仓库 + 老数据迁移脚本（`refImage` → 占位本体图）| 0.5d | 否（向下兼容） |
| **P0** | TryonService 切到本体图（缺时回退老 refImage） | 0.5d | **解决当前痛点** |
| **P0** | PromptBuilder 用 `tryonPromptWithBareWig` | 0.2d | 同上 |
| **P1** | AdminAuthInterceptor + 6 个 admin REST 接口 | 1d | 否 |
| **P1** | `/admin` 前端最小页面（上传本体 + 列表） | 1d | 否 |
| **P2** | ModelAsset + Showcase Studio | 1.5d | 否 |
| **P3** | 审核工作流 + 上架页 | 1d | 否 |

**P0 是本次解决"试戴效果差"的最短路径**——只需要本体图入库 + 后端切字段，不需要等管理后台 UI。

---

## 10. 风险与边界

### 10.1 老数据迁移
- 现有 9 款产品都没有"假发本体图"——需要一次性补录
- **过渡方案**：写个一次性脚本，对每款现有产品，用 AI 抠图（DashScope `wanx-background-generation` 或本地 `rembg`）从 `image`（模特成品图）抠出"假发部分"并裁剪，作为初始 `wigAsset.productImage`
- **承认局限**：自动抠图初版会有瑕疵（耳侧、肩部残留），运营后续手动替换

### 10.2 鉴权
- MVP token 暴露在前端 localStorage，**不能用于公网**——演示期 OK，上线前必须换账号体系
- `WIG_ADMIN_ENABLED=false` 时 `/admin/**` 全部 404，避免演示环境意外暴露

### 10.3 假发本体图的来源
- 客户提供（最理想）
- 网爬同款假发（版权风险）
- 从模特图抠图（质量妥协）

### 10.4 三模式硬约束（`.cursor/rules/00-core.mdc`）
- `MockAiService` / `SeedAiService` / `DashScopeAiService` / `YunwuOpenAiAiService` 都需在 `generateTryon` 接受**新含义的"参考图 = 本体图"** 不变接口签名，零改动
- **只需要改 caller**（`TryonService` 取本体图传入），AI 服务实现可以不动

### 10.5 运营图片质量
- 假发本体图必须是白底 / 透明底 / 纯色背景，**否则模型仍会被背景误导**
- 上传时给运营校验：检测背景复杂度、是否有人脸（用 DashScope `qwen-vl-max` 简单分类）

---

## 11. 不可破坏的硬约束（写给自己 + 后续协作者）

1. **AiService 接口签名不变**：本次改动只动 caller，不动 4 个 AI 实现的接口
2. **三模式一致**：real/seed/mock 都要支持"取本体图"逻辑，缺资产时按规则降级
3. **PromptBuilder 客户痛点指令保留**：消蜡像感 / 发丝真实 / 刘海 / 发际线 / 遮白发——`tryonPromptWithBareWig` 必须包含这些
4. **离线降级生命线不破坏**：`/admin` 关闭时演示路径完全可用
5. **C 端无需感知本次架构变化**：返回字段稳定

---

## 12. 命名 & 文件清单（计划修改 / 新增）

### 后端新增
- `model/WigAsset.java`
- `model/ModelAsset.java`
- `model/WigShowcase.java`
- `repository/WigAssetRepository.java`
- `repository/ModelAssetRepository.java`
- `repository/WigShowcaseRepository.java`
- `controller/AdminWigAssetController.java`
- `controller/AdminModelAssetController.java`
- `controller/AdminShowcaseController.java`
- `controller/AdminProductController.java`
- `config/AdminAuthInterceptor.java`
- `config/AdminProperties.java`
- `service/ShowcaseService.java`（生成 + 审核业务）
- `resources/data/wig-assets.json`
- `resources/data/model-assets.json`
- `resources/data/wig-showcases.json`

### 后端修改
- `service/TryonService.java`（切本体图）
- `service/ai/PromptBuilder.java`（新增 `tryonPromptWithBareWig`）
- `repository/CatalogRepository.java`（去掉 `refImage = image`）
- `model/WigProduct.java`（加 `wigAssetId` / `primaryShowcaseId`）
- `application.yml`（加 `app.admin.*`）

### 前端新增
- `pages/admin/AdminLoginPage.tsx`
- `pages/admin/AdminLayout.tsx`
- `pages/admin/WigAssetListPage.tsx`
- `pages/admin/ModelAssetListPage.tsx`
- `pages/admin/ShowcaseStudioPage.tsx`
- `pages/admin/ProductBindPage.tsx`
- `api/admin.ts`
- `hooks/useAdminAuth.ts`

### 前端修改
- `App.tsx`（一级状态分支增加 `view === 'admin'`）

---

## 13. 待你确认的关键决策点

| # | 问题 | 候选 | 倾向 |
|---|---|---|---|
| Q1 | 数据存储 | A. JSON 文件（MVP 一致风格） / B. 立刻上 SQLite | **A** |
| Q2 | 鉴权 | A. 单 token / B. 账号系统 | **A**（演示期） |
| Q3 | 老 9 款产品的本体图怎么办 | A. AI 抠图脚本 / B. 运营手动补录 / C. 跳过老数据，新数据严格走新流程 | **C 优先** + **A 作为补充** |
| Q4 | 落地顺序 | A. 先做 P0 修当前痛点 / B. 一次性 P0~P3 做完 | **A**（先解决"效果差"） |
| Q5 | 模特库范围 | A. 5~10 个固定模特 / B. 运营自由扩充 | **A 起步**，结构上支持 B |

---

## 附录 A：与"现状"的对比图

```
当前数据流（错）：
  user.jpg ──┐
            ▼
  AI ◀── product.refImage（=模特戴假发成品图） ←─ 同一张图被滥用
            ▲
            └── 模型被"模特图"的脸/衣/背景带偏，重画整张

新数据流（对）：
  user.jpg ──────────────────────────┐
                                    ▼
  AI ◀── wigAsset.productImage（白底纯假发）
                                    ▲
       Showcase ─→ product.primaryShowcase ─→ 给用户看
                       ▲
       (模特.jpg + wigAsset.productImage) → AI → 提前生成
```

## 附录 B：响应客户原话

> "我需要的是用户的图片上只替换选择的假发的发型部分呀！"

——本设计的目标就是这一句。让 AI 收到的"参考"是**纯假发**，模型才不会去重画用户。
