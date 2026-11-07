# 演示日资产分层 v3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task (dispatch subagents, or its sequential fallback if subagents are unavailable). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 方案 A（Q1=A，不做 Q2 大库扩量）—— 重跑导入 v3，严格分离「假发本体 vs 模特陈列」，31 款每款 10~15 张 showcase 且 100% 带 `modelAssetId`；C 端试戴仍走 `WigAsset.productImage`，不改 AI 服务。

**Architecture:** 把可测试逻辑抽到 `scripts/lib/customer_catalog_v3.py`（纯函数：文件分类、模特池、modelId 分配）；`batch-import-customer-catalog.py` 只做 I/O 与写 JSON；导入后用 `verify-import-v3.py` 做数据契约校验；后端 `TryonService` / 三模式 **零改动**。

**Tech Stack:** Python 3.11+（stdlib `unittest`）、Spring Boot 3.3 JSON 仓库、现有 React 试戴墙（已支持 `galleryImages` + 系列筛选，无需改前端）

**设计依据:** `docs/demo-asset-catalog-design-v3.md` §3~§5 方案 A

---

## 文件结构（实施前锁定）

| 路径 | 职责 |
|---|---|
| `wig-tryon-backend/scripts/lib/customer_catalog_v3.py` | **新建** 分类/模特池/分配纯函数 |
| `wig-tryon-backend/scripts/lib/__init__.py` | **新建** 空包 |
| `wig-tryon-backend/scripts/tests/__init__.py` | **新建** 空包 |
| `wig-tryon-backend/scripts/tests/test_customer_catalog_v3.py` | **新建** unittest（TDD） |
| `wig-tryon-backend/scripts/batch-import-customer-catalog.py` | **改** 调用 v3 库，写 4 个 JSON |
| `wig-tryon-backend/scripts/verify-import-v3.py` | **新建** 导入后契约校验 |
| `wig-tryon-backend/data/model-assets.json` | **由脚本覆盖** 12 人模特池 |
| `wig-tryon-backend/data/wig-assets.json` | **由脚本覆盖** 仅本体图 |
| `wig-tryon-backend/data/wig-showcases.json` | **由脚本覆盖** 仅陈列 + modelAssetId |
| `wig-tryon-backend/data/customer-catalog.json` | **由脚本覆盖** 31 款元数据 |
| `TryonService.java` / `AiService*` / 前端 | **不改** |

---

## 范围边界（对照设计自检）

| 设计条款 | 本计划 |
|---|---|
| 本体/陈列分离 | Task 1~4 |
| modelAssetId 100% | Task 4~5 |
| 12 人模特池 | Task 3 |
| V 系列 IMG 优先陈列 | Task 1 |
| 设计师/Art 静物 fallback | Task 4 |
| 大库扩量 Q2 | **明确不做** |
| 短发≥10 等扩款 | **不做**（保持 31 款，只修数据质量） |
| 后台演示 / live AI showcase | **不做**（Q1=A） |
| RecommendService 模特去重排序 | **不做**（YAGNI，现有 gallery 够用） |

---

### Task 1: 文件分类纯函数 + 失败测试

**Files:**
- Create: `wig-tryon-backend/scripts/lib/__init__.py`
- Create: `wig-tryon-backend/scripts/tests/__init__.py`
- Create: `wig-tryon-backend/scripts/lib/customer_catalog_v3.py`
- Create: `wig-tryon-backend/scripts/tests/test_customer_catalog_v3.py`

- [ ] **Step 1: 写失败测试**

`wig-tryon-backend/scripts/tests/test_customer_catalog_v3.py`:

```python
import unittest
from pathlib import Path
import sys

# scripts/ 加入 path，便于 `from lib.customer_catalog_v3 import ...`
ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from lib.customer_catalog_v3 import (  # noqa: E402
    is_wig_bare_filename,
    is_model_showcase_filename,
    pick_wig_bare_from_names,
    showcase_names_from_v_folder,
)


class TestFileClassification(unittest.TestCase):
    def test_wig_bare_priority_names(self):
        self.assertTrue(is_wig_bare_filename("1_C.jpg"))
        self.assertTrue(is_wig_bare_filename("正_C.png"))
        self.assertFalse(is_wig_bare_filename("IMG_2154_C.jpg"))

    def test_showcase_is_img_or_0f6a_not_priority_bare(self):
        self.assertTrue(is_model_showcase_filename("IMG_2154_C.jpg"))
        self.assertTrue(is_model_showcase_filename("0F6A4318_C.JPG"))
        self.assertFalse(is_model_showcase_filename("1_C.jpg"))

    def test_pick_wig_bare_prefers_zheng_c(self):
        names = ["IMG_1.jpg", "0_C.png", "正_C.png", "1_C.jpg"]
        self.assertEqual(pick_wig_bare_from_names(names), "正_C.png")

    def test_showcase_excludes_wig_bare_and_duplicate(self):
        names = [
            "1_C.jpg", "正_C.png", "IMG_2154_C.jpg", "IMG_2155_C.jpg",
            "0F6A4318_C.JPG", "1_C.jpg", "副本.jpg",
        ]
        wig = "1_C.jpg"
        got = showcase_names_from_v_folder(names, wig)
        self.assertNotIn("1_C.jpg", got)
        self.assertNotIn("正_C.png", got)  # 其它 priority bare 也不进陈列
        self.assertIn("IMG_2154_C.jpg", got)
        self.assertIn("0F6A4318_C.JPG", got)
        self.assertEqual(got[0], "IMG_2154_C.jpg")  # IMG 优先


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd /Users/wangguo/Desktop/VS/wig-tryon-backend
python3 -m unittest discover -s scripts/tests -v
```

Expected: `ModuleNotFoundError: No module named 'lib.customer_catalog_v3'` 或 `ImportError`

- [ ] **Step 3: 最小实现**

`wig-tryon-backend/scripts/lib/customer_catalog_v3.py`:

```python
"""客户素材导入 v3：文件分类与模特分配（纯函数，可 unittest）。"""
from __future__ import annotations

import re
from typing import List, Optional, Sequence, Tuple

WIG_PRIORITY = ["正_C.png", "正_C.jpg", "0_C.png", "0_C.jpg", "1_C.jpg", "1_C.JPG"]
WIG_BARE_EXACT = {
    "0_C.png", "0_C.jpg", "1_C.jpg", "1_C.JPG",
    "正_C.png", "正_C.jpg", "后_C.png", "后_C.jpg",
}
MAX_GALLERY = 15
MIN_GALLERY = 8  # 源素材不足时允许 8 张（设计 §9 兜底）


def is_wig_bare_filename(name: str) -> bool:
    upper = name.upper()
    if upper.startswith("IMG_"):
        return False
    if name in WIG_BARE_EXACT:
        return True
    # 0F6Axxxx_C 在 V 文件夹里多为棚拍佩戴图，仅当命中 WIG_PRIORITY 时才当本体
    return False


def is_model_showcase_filename(name: str) -> bool:
    if "副本" in name:
        return False
    upper = name.upper()
    if upper.startswith("IMG_"):
        return True
    if re.match(r"0F6A\d+_C\.(jpg|jpeg|png)$", name, re.I):
        return True
    return False


def pick_wig_bare_from_names(names: Sequence[str]) -> Optional[str]:
    for fn in WIG_PRIORITY:
        if fn in names:
            return fn
    for n in sorted(names):
        if is_wig_bare_filename(n):
            return n
    return None


def showcase_names_from_v_folder(names: Sequence[str], wig_bare_name: str) -> List[str]:
    bare_set = set(WIG_BARE_EXACT)
    imgs = [
        n for n in names
        if n not in bare_set
        and n != wig_bare_name
        and "副本" not in n
        and (is_model_showcase_filename(n) or n.upper().startswith("IMG_"))
    ]
    imgs.sort(key=lambda n: (0 if n.upper().startswith("IMG_") else 1, n.lower()))
    # 若 IMG 不足，补充其它非 bare 人像（0F6A）
    if len(imgs) < MIN_GALLERY:
        extra = [
            n for n in names
            if n not in bare_set and n != wig_bare_name and "副本" not in n
            and is_model_showcase_filename(n) and n not in imgs
        ]
        imgs.extend(extra)
    return imgs[:MAX_GALLERY]


def split_designer_chunk(filenames: Sequence[str]) -> Tuple[Optional[str], List[str]]:
    """设计师/Art chunk：0F6A*_C 且非 IMG 优先作本体，其余作陈列。"""
    bare_candidates = [n for n in filenames if re.match(r"0F6A\d+_C\.", n, re.I) and not n.upper().startswith("IMG_")]
    bare_candidates.sort()
    bare = bare_candidates[0] if bare_candidates else None
    showcase = [n for n in filenames if n != bare and "副本" not in n]
    showcase.sort(key=lambda n: n.lower())
    return bare, showcase[:MAX_GALLERY]
```

`wig-tryon-backend/scripts/lib/__init__.py` 与 `scripts/tests/__init__.py` 内容为空文件。

- [ ] **Step 4: 跑测试确认通过**

```bash
cd /Users/wangguo/Desktop/VS/wig-tryon-backend
python3 -m unittest discover -s scripts/tests -v
```

Expected: `Ran 4 tests ... OK`

- [ ] **Step 5: Commit**

```bash
git add wig-tryon-backend/scripts/lib/ wig-tryon-backend/scripts/tests/
git commit -m "test: 客户素材 v3 文件分类纯函数 + unittest"
```

---

### Task 2: 模特池分配逻辑 + 测试

**Files:**
- Modify: `wig-tryon-backend/scripts/lib/customer_catalog_v3.py`
- Modify: `wig-tryon-backend/scripts/tests/test_customer_catalog_v3.py`

- [ ] **Step 1: 写失败测试**

在 `test_customer_catalog_v3.py` 追加：

```python
from lib.customer_catalog_v3 import (  # noqa: E402
    MODEL_POOL_META,
    assign_model_for_showcase,
    build_folder_model_map,
)


class TestModelAssignment(unittest.TestCase):
    def test_model_pool_has_twelve(self):
        self.assertEqual(len(MODEL_POOL_META), 12)

    def test_folder_map_one_model_per_v_folder(self):
        folders = ["V802", "V803", "V804"]
        m = build_folder_model_map(folders)
        self.assertEqual(len(m), 3)
        self.assertIn("V802", m)
        self.assertGreaterEqual(m["V802"], 1)
        self.assertLessEqual(m["V802"], 12)

    def test_designer_rotates_models_within_style(self):
        ids = [assign_model_for_showcase("DS-01", "卷发", 20, idx, None) for idx in range(6)]
        self.assertGreater(len(set(ids)), 1)  # 6 张图至少 2 个不同模特
```

- [ ] **Step 2: 跑测试确认失败**

```bash
python3 -m unittest discover -s scripts/tests -v
```

Expected: `ImportError: cannot import name 'MODEL_POOL_META'`

- [ ] **Step 3: 实现模特池**

在 `customer_catalog_v3.py` 追加：

```python
MODEL_POOL_META = [
    {"id": 1, "name": "小雅", "gender": "F", "ageGroup": "young", "faceShape": "圆脸", "tags": ["短发", "卷发"]},
    {"id": 2, "name": "小慧", "gender": "F", "ageGroup": "young", "faceShape": "鹅蛋脸", "tags": ["短发", "日常"]},
    {"id": 3, "name": "小岚", "gender": "F", "ageGroup": "young", "faceShape": "长脸", "tags": ["卷发", "时尚"]},
    {"id": 4, "name": "小曼", "gender": "F", "ageGroup": "mid", "faceShape": "方脸", "tags": ["中长发", "职场"]},
    {"id": 5, "name": "小柔", "gender": "F", "ageGroup": "mid", "faceShape": "鹅蛋脸", "tags": ["中长发", "温柔"]},
    {"id": 6, "name": "小琪", "gender": "F", "ageGroup": "mid", "faceShape": "瓜子脸", "tags": ["长发", "气质"]},
    {"id": 7, "name": "兰姐", "gender": "F", "ageGroup": "elder", "faceShape": "圆脸", "tags": ["银发", "遮白"]},
    {"id": 8, "name": "梅姐", "gender": "F", "ageGroup": "elder", "faceShape": "长脸", "tags": ["中长发", "优雅"]},
    {"id": 9, "name": "小峰", "gender": "M", "ageGroup": "young", "faceShape": "方脸", "tags": ["短发", "中性"]},
    {"id": 10, "name": "小凯", "gender": "M", "ageGroup": "mid", "faceShape": "长脸", "tags": ["短发", "干练"]},
    {"id": 11, "name": "小艺", "gender": "F", "ageGroup": "young", "faceShape": "鹅蛋脸", "tags": ["Art款", "创意"]},
    {"id": 12, "name": "小诺", "gender": "F", "ageGroup": "young", "faceShape": "圆脸", "tags": ["设计款", "时尚"]},
]

CATEGORY_MODEL_BASE = {
    "短发": 1,
    "中长发": 4,
    "长发": 6,
    "卷发": 3,
}


def build_folder_model_map(v_folder_codes: Sequence[str]) -> dict[str, int]:
    """每个 V 文件夹绑定一个固定模特（同文件夹 IMG 视为同一模特）。"""
    out: dict[str, int] = {}
    for i, code in enumerate(v_folder_codes):
        out[code] = (i % 12) + 1
    return out


def assign_model_for_showcase(
    folder_key: str,
    category: str,
    style_id: int,
    showcase_index: int,
    folder_model_map: Optional[dict[str, int]],
) -> int:
    if folder_model_map and folder_key in folder_model_map:
        base = folder_model_map[folder_key]
        # 同文件夹内可按 index 微调，使画廊出现 2~3 个邻近模特
        return ((base - 1 + (showcase_index // 4)) % 12) + 1
    base = CATEGORY_MODEL_BASE.get(category, 1)
    return ((base - 1 + style_id + showcase_index) % 12) + 1
```

- [ ] **Step 4: 跑测试确认通过**

Expected: `Ran 7 tests ... OK`

- [ ] **Step 5: Commit**

```bash
git add wig-tryon-backend/scripts/lib/customer_catalog_v3.py wig-tryon-backend/scripts/tests/test_customer_catalog_v3.py
git commit -m "feat: v3 模特池 12 人与 showcase modelAssetId 分配规则"
```

---

### Task 3: 导入契约校验脚本

**Files:**
- Create: `wig-tryon-backend/scripts/verify-import-v3.py`

- [ ] **Step 1: 写校验脚本（导入前跑应失败）**

`wig-tryon-backend/scripts/verify-import-v3.py`:

```python
#!/usr/bin/env python3
"""导入 v3 后数据契约：本体≠陈列 URL、showcase 必有 modelAssetId。"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"


def main() -> int:
    assets = json.loads((DATA / "wig-assets.json").read_text(encoding="utf-8"))
    showcases = json.loads((DATA / "wig-showcases.json").read_text(encoding="utf-8"))
    models = json.loads((DATA / "model-assets.json").read_text(encoding="utf-8"))
    catalog = json.loads((DATA / "customer-catalog.json").read_text(encoding="utf-8"))

    errors = []
    if len(assets) != 31:
        errors.append(f"wig-assets 应为 31，实际 {len(assets)}")
    if len(models) < 12:
        errors.append(f"model-assets 应 >= 12，实际 {len(models)}")

    null_model = [s["id"] for s in showcases if not s.get("modelAssetId")]
    if null_model:
        errors.append(f"showcase 缺 modelAssetId: {null_model[:5]}... 共 {len(null_model)} 条")

    by_wig: dict[int, list] = {}
    for s in showcases:
        by_wig.setdefault(s["wigAssetId"], []).append(s)

    asset_map = {a["id"]: a["productImage"] for a in assets}
    for aid, ss in by_wig.items():
        bare = asset_map.get(aid)
        if not bare:
            errors.append(f"wigAsset {aid} 无 productImage")
            continue
        for s in ss:
            if s.get("resultImage") == bare:
                errors.append(f"showcase {s['id']} resultImage 与本体相同 wigAsset={aid}")
        if len(ss) < 8:
            errors.append(f"wigAsset {aid} showcase 仅 {len(ss)} 张 (<8)")

    if errors:
        print("FAIL:")
        for e in errors:
            print(" -", e)
        return 1

    avg = len(showcases) / len(assets)
    print(f"OK: {len(assets)} 款式, {len(showcases)} showcase, {len(models)} 模特, 平均 {avg:.1f} 张/款")
    cats = {}
    for c in catalog:
        cats[c["category"]] = cats.get(c["category"], 0) + 1
    print("分类:", cats)
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

Fix syntax - `MIN_OK := 8` is wrong in if. Use:

```python
MIN_OK = 8
...
if len(ss) < MIN_OK:
```

- [ ] **Step 2: 跑校验（当前 v2 数据应 FAIL）**

```bash
cd /Users/wangguo/Desktop/VS/wig-tryon-backend
python3 scripts/verify-import-v3.py
```

Expected: `FAIL: ... showcase 缺 modelAssetId ...` 以及可能的 `resultImage 与本体相同`

- [ ] **Step 3: Commit**

```bash
git add wig-tryon-backend/scripts/verify-import-v3.py
git commit -m "chore: 导入 v3 数据契约校验脚本"
```

---

### Task 4: 重写 batch-import-customer-catalog.py（v3 主流程）

**Files:**
- Modify: `wig-tryon-backend/scripts/batch-import-customer-catalog.py`（全文替换为 v3）

- [ ] **Step 1: 替换导入脚本**

完整 `wig-tryon-backend/scripts/batch-import-customer-catalog.py`:

```python
#!/usr/bin/env python3
"""
方案 A / v3：本体与陈列分离 + 12 人模特池 + modelAssetId。
- V 系列：WIG_PRIORITY 本体；IMG_/0F6A 陈列
- 设计师/Art：chunk 内拆本体/陈列；无本体时从静物池 fallback
输出：wig-assets.json / wig-showcases.json / model-assets.json / customer-catalog.json
"""
import json
import re
import shutil
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import List, Optional

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))

from lib.customer_catalog_v3 import (  # noqa: E402
    MAX_GALLERY,
    MIN_GALLERY,
    MODEL_POOL_META,
    assign_model_for_showcase,
    build_folder_model_map,
    pick_wig_bare_from_names,
    showcase_names_from_v_folder,
    split_designer_chunk,
)

MAT = ROOT.parent / "发型素材-ai测试用-刘经理提供"
STILL_DIR = MAT / "摄影师拍摄静物原图C"
DATA = ROOT / "data"
STORAGE = DATA / "storage"
BASE_URL = "http://localhost:8080/files"

# 保留 v2 的 V_META / DESIGNER_NAMES / ART_NAMES（原文件 L29-67，实施时原样复制）
# ... V_META, DESIGNER_NAMES, ART_NAMES 常量块不变 ...

def v_code(folder_name: str) -> str:
    m = re.match(r"(V\d+)", folder_name.replace(" ", ""))
    return m.group(1) if m else folder_name


def list_images(folder: Path) -> List[Path]:
    imgs = [p for p in folder.iterdir() if p.is_file() and p.suffix.lower() in {".jpg", ".jpeg", ".png"}]
    imgs.sort(key=lambda p: p.name.lower())
    return imgs


def copy_asset(src: Path, dst: Path) -> str:
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    return f"{BASE_URL}/{dst.relative_to(STORAGE).as_posix()}"


def chunk_files(files: List[Path], size: int) -> List[List[Path]]:
    files = sorted(files, key=lambda p: p.name.lower())
    return [files[i : i + size] for i in range(0, len(files), size)]


_still_pool: List[Path] = []
_still_idx = 0


def still_life_fallback(category: str) -> Optional[Path]:
    global _still_idx
    if not _still_pool and STILL_DIR.is_dir():
        _still_pool.extend(list_images(STILL_DIR))
    if not _still_pool:
        return None
    p = _still_pool[_still_idx % len(_still_pool)]
    _still_idx += 1
    return p


def build_model_assets(v_folder_codes: List[str], folder_model_map: dict) -> list:
    """从各 V 文件夹 IMG 代表图 + 元数据生成 12 人 model-assets.json。"""
    now = datetime.now(timezone.utc).isoformat()
    portrait_by_id: dict[int, str] = {}
    mat = MAT

    for code in v_folder_codes:
        folder = next((p for p in mat.iterdir() if p.is_dir() and p.name.startswith(code)), None)
        if not folder:
            continue
        mid = folder_model_map[code]
        if mid in portrait_by_id:
            continue
        img = next((p for p in list_images(folder) if p.name.upper().startswith("IMG_")), None)
        if img:
            url = copy_asset(img, STORAGE / "model-assets" / f"{mid}{img.suffix.lower()}")
            portrait_by_id[mid] = url

    out = []
    for meta in MODEL_POOL_META:
        mid = meta["id"]
        url = portrait_by_id.get(mid) or f"{BASE_URL}/model-assets/placeholder-{mid}.jpg"
        out.append({
            "id": mid,
            "name": meta["name"],
            "portraitImage": url,
            "gender": meta["gender"],
            "ageGroup": meta["ageGroup"],
            "faceShape": meta["faceShape"],
            "tags": meta["tags"],
            "createdAt": time.time(),
            "createdBy": "customer-batch-import-v3",
        })
    return out


def build_style(
    style_id: int,
    folder_key: str,
    code: str,
    name: str,
    category: str,
    tags: List[str],
    desc: str,
    line: str,
    wig_src: Path,
    gallery_srcs: List[Path],
    folder_model_map: Optional[dict],
    assets: list,
    showcases: list,
    catalog: list,
    showcase_id: int,
    source_note: Optional[str] = None,
) -> int:
    wig_url = copy_asset(wig_src, STORAGE / "wig-assets" / f"{style_id}{wig_src.suffix.lower()}")
    now = datetime.now(timezone.utc).isoformat()

    assets.append({
        "id": style_id,
        "code": code,
        "name": name,
        "productImage": wig_url,
        "productImageThumbnail": wig_url,
        "descriptionForAi": desc,
        "category": category,
        "colorFamily": "自然黑",
        "tags": tags + [line],
        "createdAt": time.time(),
        "createdBy": "customer-batch-import-v3",
    })

    if not gallery_srcs:
        raise ValueError(f"{name} 无陈列图，违反 v3 契约")

    for idx, src in enumerate(gallery_srcs[:MAX_GALLERY]):
        url = copy_asset(src, STORAGE / "showcases" / f"{style_id}-{idx + 1}{src.suffix.lower()}")
        model_id = assign_model_for_showcase(folder_key, category, style_id, idx, folder_model_map)
        showcases.append({
            "id": showcase_id,
            "wigAssetId": style_id,
            "modelAssetId": model_id,
            "resultImage": url,
            "status": "APPROVED",
            "isPrimary": idx == 0,
            "changeScore": None,
            "prompt": f"{name} 陈列 {idx + 1}",
            "strategyUsed": "customer-import-v3",
            "errorMessage": None,
            "createdAt": now,
            "reviewedAt": now,
            "reviewedBy": "customer-batch-import-v3",
        })
        showcase_id += 1

    catalog.append({
        "id": style_id,
        "name": name,
        "category": category,
        "material": "100% 真人发丝",
        "priceMin": 2800 + (style_id % 5) * 400,
        "priceMax": 5800 + (style_id % 5) * 600,
        "tags": tags + [line, "客户款"],
        "faceTypes": ["圆脸", "方脸", "鹅蛋脸", "长脸"],
        "rating": round(4.6 + (style_id % 4) * 0.1, 1),
        "reviews": 60 + style_id * 3,
        "suitable": "多种脸型",
        "weight": "约 110g",
        "craft": "手工/机制结合",
        "description": desc,
        "colorFamily": "自然黑",
        "capStructure": "全手织",
        "targetCrowds": ["客户演示", line.replace("款", "")],
        "sceneTags": ["日常", "试戴", "门店"],
        "storeIds": [1, 2],
        "hairlineCoverage": 3 if "短" in category else 2,
        "whiteHairCoverage": 3,
        "isHot": style_id <= 8,
        "isNew": line in ("设计款", "Art款"),
        "styleLine": line,
        "galleryCount": min(len(gallery_srcs), MAX_GALLERY),
        "sourceNote": source_note,
    })
    print(f"[OK] #{style_id:02d} {name} | 本体={wig_src.name} | 陈列={min(len(gallery_srcs), MAX_GALLERY)}")
    return showcase_id


def main() -> None:
    if not MAT.is_dir():
        raise SystemExit(f"素材目录不存在: {MAT}")

    for sub in ("wig-assets", "showcases", "model-assets"):
        (STORAGE / sub).mkdir(parents=True, exist_ok=True)
    DATA.mkdir(parents=True, exist_ok=True)

    v_folders = sorted([p for p in MAT.iterdir() if p.is_dir() and p.name.startswith("V")])
    v_codes = [v_code(f.name) for f in v_folders]
    folder_model_map = build_folder_model_map(v_codes)

    assets, showcases, catalog = [], [], []
    showcase_id = 1
    style_id = 0

    for folder in v_folders:
        code = v_code(folder.name)
        meta = V_META.get(code)
        if meta:
            name, cat, tags, desc = meta
        else:
            name, cat, tags, desc = code, "中长发", ["客户素材"], f"客户款 {folder.name}"
        names = [p.name for p in list_images(folder)]
        bare_name = pick_wig_bare_from_names(names)
        if not bare_name:
            print(f"[SKIP] {folder.name}: 无本体图")
            continue
        wig_src = folder / bare_name
        show_names = showcase_names_from_v_folder(names, bare_name)
        if len(show_names) < MIN_GALLERY:
            print(f"[WARN] {folder.name}: 陈列仅 {len(show_names)} 张")
        gallery = [folder / n for n in show_names]
        style_id += 1
        showcase_id = build_style(
            style_id, code, code, name, cat, tags, desc, "V系列",
            wig_src, gallery, folder_model_map,
            assets, showcases, catalog, showcase_id,
        )

    # 设计师款 / Art（逻辑同 v3 split_designer_chunk + still_life_fallback）
    # ... 实施时按 v2 循环结构，替换 wig/gallery 选取 ...

    models = build_model_assets(v_codes, folder_model_map)

    (DATA / "wig-assets.json").write_text(json.dumps(assets, ensure_ascii=False, indent=2), encoding="utf-8")
    (DATA / "wig-showcases.json").write_text(json.dumps(showcases, ensure_ascii=False, indent=2), encoding="utf-8")
    (DATA / "model-assets.json").write_text(json.dumps(models, ensure_ascii=False, indent=2), encoding="utf-8")
    (DATA / "customer-catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n完成 v3: {len(assets)} 款式, {len(showcases)} 陈列, {len(models)} 模特")


if __name__ == "__main__":
    main()
```

**实施注意:** Task 4 写文件时必须把 `V_META` / `DESIGNER_NAMES` / `ART_NAMES` 从当前 v2 脚本 **原样复制** 进新脚本，并补全设计师/Art 两个 `for` 循环（用 `split_designer_chunk` + `still_life_fallback`），不得留 `# ...` 省略。

设计师/Art 循环参考实现：

```python
    designer_dir = MAT / "设计师款"
    if designer_dir.is_dir():
        chunks = chunk_files(list_images(designer_dir), 12)
        for i, chunk in enumerate(chunks[: len(DESIGNER_NAMES)]):
            name, cat, tags = DESIGNER_NAMES[i]
            code = f"DS-{i + 1:02d}"
            desc = f"{name}，设计师精选造型，发丝自然有层次，适合门店演示。"
            bare_name, show_names = split_designer_chunk([p.name for p in chunk])
            note = None
            if bare_name:
                wig_src = next(p for p in chunk if p.name == bare_name)
                gallery = [p for p in chunk if p.name != bare_name]
            else:
                fb = still_life_fallback(cat)
                if not fb:
                    print(f"[SKIP] {name}: 无本体且无静物池")
                    continue
                wig_src = fb
                gallery = chunk
                note = "still-life-pool"
            if len(gallery) < MIN_GALLERY:
                print(f"[WARN] {name}: 陈列 {len(gallery)} 张")
            style_id += 1
            showcase_id = build_style(
                style_id, code, code, name, cat, tags, desc, "设计款",
                wig_src, gallery[:MAX_GALLERY], None,
                assets, showcases, catalog, showcase_id, note,
            )
```

- [ ] **Step 2: 执行导入**

```bash
cd /Users/wangguo/Desktop/VS/wig-tryon-backend
python3 scripts/batch-import-customer-catalog.py
```

Expected: `完成 v3: 31 款式, 250+ 陈列, 12 模特`（具体张数取决于 IMG 数量，通常 250~330）

- [ ] **Step 3: 跑契约校验**

```bash
python3 scripts/verify-import-v3.py
```

Expected: `OK: 31 款式, ... showcase 缺 modelAssetId 为 0`

- [ ] **Step 4: Commit**

```bash
git add wig-tryon-backend/scripts/batch-import-customer-catalog.py wig-tryon-backend/data/
git commit -m "feat: 客户素材导入 v3 — 本体/陈列分离 + 模特关联"
```

---

### Task 5: 重启后端 + API 验证（C 端链路）

**Files:** 无代码变更（仅运维验证）

- [ ] **Step 1: 重启 Spring Boot**

```bash
lsof -ti :8080 | xargs kill -9 2>/dev/null; sleep 2
cd /Users/wangguo/Desktop/VS/wig-tryon-backend
export YUNWU_API_KEY='你的key'   # 演示 real 模式时
export AI_MODE=seed               # 汇报前建议 seed 保稳；real 抽检另测
export WIG_ADMIN_ENABLED=true WIG_ADMIN_TOKEN=demo-admin
mvn -q spring-boot:run
```

Expected 日志:

```
[WigAssetRepository] 初始化完成，共 31 条假发本体
[WigShowcaseRepository] 初始化完成，共 XXX 条成品图
[CatalogRepository] 已加载客户商品 catalog 覆盖 31 条
```

- [ ] **Step 2: 验证 products API**

```bash
curl -s 'http://localhost:8080/api/recommend/products' | python3 - <<'PY'
import json, sys
d = json.load(sys.stdin)["data"]
print("products", len(d))
g = [len(x.get("galleryImages") or []) for x in d]
print("gallery avg/min/max", round(sum(g)/len(g),1), min(g), max(g))
# 封面不应等于本体 URL（抽样 id=2 V802）
p = next(x for x in d if x["id"] == 2)
print("V802 cover ends with", p["image"].split("/")[-1])
PY
```

Expected: `products 31`, `gallery avg` ≥ 8, 封面路径含 `showcases/` 而非仅 `wig-assets/2.jpg`

- [ ] **Step 3: 验证试戴仍用本体（日志级）**

提交一次试戴后在 backend 日志中应出现:

```
[TryonService] 使用 WigAsset productImage productId=2, assetId=2
```

**不应出现** `refImage` 回退 WARN（除非某款缺 asset）。

- [ ] **Step 4: Commit**（若仅数据/脚本，此步可跳过）

---

### Task 6: 小程序演示冒烟（Q1=A 验收清单）

**Files:** 无代码变更

- [ ] **Step 1: 启动前端**

```bash
cd /Users/wangguo/Desktop/VS/ai-hairstyle-mini-program-ui && npm run dev
```

打开 `http://localhost:5173`

- [ ] **Step 2: 人工检查 5 项**

| # | 操作 | 预期 |
|---|---|---|
| 1 | 试戴墙 → 点「设计款」chip | 列表仅设计款，≥8 款 |
| 2 | 点「短发」chip | 与系列 chip 可叠加筛选 |
| 3 | 点进「减龄BOBO短发」放大 | 底部「款式素材 · N 张」横滑，N≥8，图为模特佩戴非静物 |
| 4 | 上传自拍 → 选 2 款试戴 | seed 模式下 15~30s 内出结果 |
| 5 | 切换「V系列」与「Art款」 | 封面视觉差异明显 |

- [ ] **Step 3: 汇报话术（30 秒）**

> 「您看到的商品图是模特佩戴效果；AI 试戴时系统会自动改用假发本体产品图，只替换您头发区域，不会照搬模特的脸和背景。」

---

## 自审（Plan vs 设计 v3）

| 设计需求 | 任务 |
|---|---|
| §3.1 V 文件夹本体/IMG 陈列 | Task 1, 4 |
| §3.2 设计师静物 fallback | Task 4 |
| §4 模特池 + modelAssetId | Task 2, 4 |
| §5 方案 A 不重跑 AI | Task 4~5 |
| §7.1 小程序演示 | Task 6 |
| §7.2 后台演示 | **跳过**（Q1=A） |
| §6 分类扩到 ≥10 | **跳过**（不做 Q2） |
| TryonService 不改 | 全文 |

**Placeholder 扫描:** 仅 Task 4 主脚本中 `V_META` 块标注「原样复制」——实施时必须粘贴完整常量，不得提交带 `# ...` 的可运行脚本。

---

## 执行方式

Plan 已保存至 `docs/superpowers/plans/2026-06-21-demo-asset-catalog-v3.md`。

**两种执行选项：**

1. **Subagent-Driven（推荐）** — 按 Task 1→6 逐任务派发子 agent，每任务后 review  
2. **Inline Execution** — 当前会话连续执行，Task 4 完成后暂停给你看 `verify-import-v3.py` 输出

请输入 **`/code`** 开始实现（默认 Inline Execution，从 Task 1 开始）。
