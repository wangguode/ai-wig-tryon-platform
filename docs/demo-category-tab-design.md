# 选发型 Tab 分类混乱 — 设计说明（v1 草稿）

> **状态**：待确认（/design 产出，确认前不写实现代码）  
> **关联**：`docs/demo-asset-catalog-design-v3.md`（本体/陈列已修）  
> **页面**：小程序 `SelectHairPage`（选择发型）

---

## 0. 一句话理解

Tab（短发/中长发/长发/卷发）筛选用的是 `WigProduct.category` 单字段，但当前 **分类维度混用（长度 vs 卷度）+ 前端推荐区不跟 Tab 联动 + 同款在推荐区与列表重复出现**，导致用户感觉「Tab 下数据错乱、重复」。

---

## 1. 现状诊断（已核实）

### 1.1 后端数据

| 检查项 | 结果 |
|---|---|
| `/api/recommend/products` 条数 | 31，**id 无重复** |
| category 分布 | 短发 8 / 中长发 6 / 长发 6 / 卷发 11 |
| 卷发 Tab 数据 | **有 11 款**（含 V808~811、设计师卷款等） |

→ **不是 API 返回重复条目**，混乱主要来自 **分类语义 + 前端展示逻辑**。

### 1.2 分类语义冲突（核心）

当前 `V_META` / 导入脚本把 **长度** 和 **卷度** 混在一个 `category` 里：

| 款式 | 当前 category | 用户直觉 |
|---|---|---|
| 波浪长卷发、自然长卷发 | **长发** | 也会点「卷发」Tab 找 |
| 空气感长卷、复古大波浪 | **卷发** | 名字带「长卷」，点「长发」Tab 也会找 |
| 自然蓬松**中短发** | **中长发** | 名字带「短」，点「短发」Tab 也会找 |
| 蓬松**短卷发** | **短发** | 在「卷发」Tab 看不到 |

**结论**：四 Tab 不是互斥维度——「长发 vs 卷发」会交叉，领导/用户必然觉得「长发里有卷发的、卷发里没长发卷的」。

### 1.3 前端 UX 问题（`SelectHairPage.tsx`）

| 问题 | 代码位置 | 现象 |
|---|---|---|
| **「为你推荐」不跟 Tab 过滤** | L238-285 独立渲染 `recommendedCards` | 选「长发」Tab 时，顶部仍展示短发/卷发推荐 |
| **推荐款在下方网格再次出现** | L172-191 过滤列表含全部推荐 id | 同一款上下出现两次 → 「很多重复」 |
| 首页 `HomePage` 仍用 mock 6 条 | 未接 API | 若从首页 Tab 进，与选款页 31 款不一致（次要） |
| 首页传入 `category` 未使用 | `App.tsx` 未传给 `SelectHairPage` | 点首页「短发」进选款页，Tab 仍默认「全部」 |

### 1.4 「看起来重复」

- **逻辑重复**：推荐横滑 + 下方列表同一 `id`（见上）
- **视觉重复**：多款卷/法式/大波浪命名相近（V809 vs 设计师-法式慵懒卷）；封面图同系列棚拍风格接近

---

## 2. 目标（演示日）

1. 点 **短发 / 中长发 / 长发 / 卷发** Tab，列表 **只含该 Tab 语义下的款**，领导一眼能懂  
2. 同一 Tab 内 **不出现上下 duplicate 同一 id**  
3. **不破坏** v3 资产分层（本体/陈列/modelAssetId）  
4. 改动可控，**演示前 1~2 小时可落地**

---

## 3. 方案对比

### 方案 A：长度 Tab + 卷度标签（推荐）

**做法**
- `category` **只表示长度**：`短发 | 中长发 | 长发`（三选一）
- 新增 `hairTexture` 或复用 `tags`：`直发 | 微卷 | 大卷`（可多款）
- 原「卷发」Tab 改为 **「卷发造型」筛选**：`tags` 含 `卷` / `hairTexture != 直发`
- 重跑 `category-audit.csv` 修正 31 款 metadata + 导入脚本

| 优点 | 缺点 |
|---|---|
| 符合用户心智（先长度后卷度） | 需改 catalog 字段 + 前端 Tab 逻辑 |
| 长发 Tab 可含「长直+长卷」 | 卷发 Tab 定义要写清规则 |
| 演示最好讲 | 比纯前端多 30min |

### 方案 B：保持四 Tab，严格互斥重分类

**做法**
- 定死规则：有卷度 → `卷发`（除非长度明确为短）；长直 → `长发`；锁骨 → `中长发`
- 只改 `V_META` + `customer-catalog.json`，前端逻辑不变

| 优点 | 缺点 |
|---|---|
| 改动面小 | 「波浪长卷发」进卷发还是长发仍易争议 |
| 无前端 Tab 结构变化 | 无法同时满足「按长度逛」和「按卷度逛」 |

### 方案 C：仅修前端（最快，不推荐单独用）

**做法**
- 推荐区 `recommendedCards` 同样 `activeCategory` 过滤
- 下方列表 `excludeIds = recommendedCards.map(h => h.id)` 去重
- Tab 切换时隐藏推荐区或显示「当前分类推荐」

| 优点 | 缺点 |
|---|---|
| 30 分钟 | **不解决**「长发 Tab 里卷发名/卷发 Tab 里中发」的 metadata 问题 |
| 零后端 | 领导仍可能质疑分类 |

**推荐：A + C 组合** — C 立刻消除重复与 Tab 穿透；A 从根上修正分类语义。

---

## 4. 推荐方案详细设计（A + C）

### 4.1 数据模型（最小扩展）

```text
WigProduct / customer-catalog.json
├── category          # 仅长度：短发 | 中长发 | 长发
├── tags              # 保留，增加规范：直发 / 微卷 / 大卷 / 羊毛卷 ...
└── (可选) hairTexture # 直发 | 卷发 — 若不想改 tags 可加字段
```

**31 款重分类原则（示例）**

| 原 category | 典型款式 | 新 category | 必加 tag |
|---|---|---|---|
| 卷发 | 法式慵懒卷、羊毛卷中发 | 中长发 或 长发（按发尾长度） | 大卷/羊毛卷 |
| 长发 | 波浪长卷发、自然长卷发 | 长发 | 大卷 |
| 短发 | 蓬松短卷发 | 短发 | 短卷 |
| 中长发 | 自然蓬松中短发 | 中长发 | （可标 蓬松） |

**「卷发 Tab」前端定义（不再等于 category=卷发）**

```typescript
const isCurlyStyle = (p: HairStyle) =>
  p.tags?.some(t => /卷|波浪|羊毛|慵懒/.test(t)) || p.category === '卷发'; // 过渡期兼容旧值
```

### 4.2 前端 `SelectHairPage` 改动

1. **Tab 定义**

```typescript
const LENGTH_TABS = ['全部', '短发', '中长发', '长发'];
const STYLE_TABS = ['卷发造型']; // 或 chips：直发 | 卷发
```

2. **过滤函数**（单一 `filterProducts(products, { length, curlyOnly })`）

3. **推荐区联动**
   - `recommendedCardsFiltered = recommendedCards.filter(p => passFilter(p, activeTab))`
   - 若过滤后为空 → 隐藏推荐区或显示「当前分类暂无 AI 推荐，请下方自选」

4. **列表去重**

```typescript
const recommendedIdsInView = new Set(recommendedCardsFiltered.map(x => x.hair.id));
const filteredHairs = products.filter(passFilter).filter(p => !recommendedIdsInView.has(p.id));
```

5. **卡片角标**：显示 `category` + 主卷度 tag（如「长发 · 大卷」），减少误读

### 4.3 导入 / 运维

- 新增 `scripts/audit-category-v4.py`：输出 CSV + 校验「category 仅三长度」
- 更新 `batch-import-customer-catalog.py` 中 `V_META` / `DESIGNER_NAMES` 的 category 列
- `verify-import-v3.py` 增加：`category in {短发,中长发,长发}`（迁移完成后）

### 4.4 不改动的部分

- `TryonService` / 本体图推理链路  
- showcase / modelAssetId 结构  
- `RecommendService` 推荐算法（可选后续按 category 加权）

---

## 5. 演示验收标准

| # | 操作 | 预期 |
|---|---|---|
| 1 | 选「短发」Tab | 仅 8~10 款，无及胸长发封面 |
| 2 | 选「卷发造型」 | 含短卷+长卷，但 **不含** 纯直发（V815 优雅长直发不出现） |
| 3 | 选「长发」 | 含长直+长卷，不出现 BOBO/短碎发 |
| 4 | 有推荐时 | 同一 id 不在推荐横滑与下方网格各出现一次 |
| 5 | 切换 Tab | 推荐区随 Tab 变化或收起 |

---

## 6. 风险

| 风险 | 兜底 |
|---|---|
| 重分类与业务同事认知不一致 | 演示前打印 31 款对照表给领导「长度+卷度二维」说明 |
| 旧缓存 mock 6 条 | 选款页已接 API；可选同步修 HomePage 接 API |
| 过渡期 API 仍返回 category=卷发 | 前端 `isCurlyStyle` 兼容旧值 |

---

## 7. 待确认（请先拍板 1 题）

### Q1 — Tab 最终语义采用哪种？

- **A.** 方案 A+C：三长度 Tab +「卷发造型」筛选（**推荐**，最符合「短发/中长/长/卷都要准」）
- **B.** 方案 B+C：保持四 Tab 互斥，只重排 metadata + 修前端重复
- **C.** 方案 C  alone：只修重复和推荐穿透，分类 metadata 演示后再说

---

**请回复 Q1 选项（A/B/C）。确认后进入 `/plan` 拆任务；确认前不写实现代码。**
