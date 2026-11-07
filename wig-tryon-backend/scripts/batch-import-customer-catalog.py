#!/usr/bin/env python3
"""
方案 A / v3：本体与陈列分离 + 12 人模特池 + modelAssetId。
- V 系列：WIG_PRIORITY 本体；IMG_/0F6A 陈列
- 设计师/Art：陈列=chunk 全部人像；本体从静物池 fallback
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
)

MAT = ROOT.parent / "发型素材-ai测试用-刘经理提供"
STILL_DIR = MAT / "摄影师拍摄静物原图C"
DATA = ROOT / "data"
STORAGE = DATA / "storage"
BASE_URL = "http://localhost:8080/files"

V_META = {
    "V801": ("自然蓬松中短发", "中长发", ["自然", "蓬松", "日常"], "自然蓬松中短发，层次轻盈，适合日常通勤。"),
    "V802": ("减龄BOBO短发", "短发", ["减龄", "BOBO", "圆脸"], "减龄短BOBO，发尾内扣，下巴长度，自然黑，空气感刘海。"),
    "V803": ("经典短直发", "短发", ["干练", "短直发", "职场"], "经典短直发，线条利落，耳下长度，适合职场。"),
    "V804": ("纹理短BOB", "短发", ["BOB", "纹理", "修颜"], "纹理感短BOB，发尾微卷，修饰脸型。"),
    "V805": ("蓬松短卷发", "短发", ["蓬松", "短卷", "活力"], "蓬松短卷发，卷度自然，显发量。"),
    "V806": ("内扣波波头", "短发", ["内扣", "波波", "甜美"], "内扣波波头，发尾向内卷曲，甜美减龄。"),
    "V807": ("轻熟中短发", "中长发", ["轻熟", "中短", "知性"], "轻熟中短发，锁骨上方，柔和层次。"),
    "V808": ("韩系大卷中发", "卷发", ["韩系", "大卷", "浪漫"], "韩系大卷中长发，卷度饱满，浪漫优雅。"),
    "V809": ("法式慵懒卷", "卷发", ["法式", "慵懒卷", "时尚"], "法式慵懒卷，不规则卷度，时尚随性。"),
    "V810": ("羊毛卷中发", "卷发", ["羊毛卷", "复古", "显发量"], "羊毛卷中发，卷度细密，复古显发量。"),
    "V811": ("空气感长卷", "卷发", ["空气卷", "长发", "女神"], "空气感长卷发，卷度大而有弹性，女神范。"),
    "V812": ("锁骨直发", "中长发", ["锁骨发", "直发", "气质"], "锁骨长度直发，发尾微翘，气质温柔。"),
    "V813": ("层次中长发", "中长发", ["层次", "中长发", "修脸"], "层次中长发，面部层次修饰，轻盈不压脸。"),
    "V815": ("优雅长直发", "长发", ["长直发", "黑长直", "经典"], "优雅长直发，及胸长度，顺滑垂坠。"),
    "V816": ("波浪长卷发", "长发", ["波浪", "长卷", "浪漫"], "波浪长卷发，及胸大波浪，浪漫女人味。"),
    "V817": ("自然长卷发", "长发", ["自然卷", "长发", "温柔"], "自然长卷发，卷度柔和，温柔大方。"),
    "V818": ("高层次长发", "长发", ["高层次", "长发", "动感"], "高层次长发，层次丰富，动感时尚。"),
    "V819": ("复古大波浪", "卷发", ["复古", "大波浪", "港风"], "复古大波浪，卷度立体，港风气质。"),
    "V820": ("清爽短碎发", "短发", ["短碎发", "清爽", "中性"], "清爽短碎发，层次碎剪，中性帅气。"),
}

DESIGNER_NAMES = [
    ("设计师-法式慵懒卷", "卷发", ["设计款", "法式", "慵懒"]),
    ("设计师-气质长直", "长发", ["设计款", "长直", "气质"]),
    ("设计师-港风大波浪", "卷发", ["设计款", "港风", "波浪"]),
    ("设计师-日系短发", "短发", ["设计款", "日系", "短发"]),
    ("设计师-欧美大卷", "卷发", ["设计款", "欧美", "大卷"]),
    ("设计师-复古羊毛卷", "卷发", ["设计款", "羊毛卷", "复古"]),
    ("设计师-知性锁骨发", "中长发", ["设计款", "锁骨", "知性"]),
    ("设计师-韩系空气卷", "卷发", ["设计款", "韩系", "空气卷"]),
]

ART_NAMES = [
    ("Art-创意短发", "短发", ["Art款", "创意", "短发"]),
    ("Art-艺术长卷", "长发", ["Art款", "艺术", "长卷"]),
    ("Art-先锋造型", "卷发", ["Art款", "先锋", "造型"]),
    ("Art-时尚特装", "中长发", ["Art款", "时尚", "特装"]),
]

_still_pool: List[Path] = []
_still_idx = 0


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
    return [files[i: i + size] for i in range(0, len(files), size)]


def still_life_fallback(category: str) -> Optional[Path]:
    global _still_idx
    if not _still_pool and STILL_DIR.is_dir():
        _still_pool.extend(list_images(STILL_DIR))
    if not _still_pool:
        return None
    p = _still_pool[_still_idx % len(_still_pool)]
    _still_idx += 1
    return p


def build_model_assets(v_folders: List[Path], folder_model_map: dict[str, int]) -> list:
    now = datetime.now(timezone.utc).isoformat()
    portrait_by_id: dict[int, str] = {}

    for folder in v_folders:
        code = v_code(folder.name)
        if code not in folder_model_map:
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
            "createdAt": now,
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
    folder_model_map: Optional[dict[str, int]],
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


def import_designer_or_art(
    chunks: List[List[Path]],
    names_meta: list,
    line: str,
    code_prefix: str,
    style_id: int,
    showcase_id: int,
    assets: list,
    showcases: list,
    catalog: list,
) -> tuple[int, int]:
    for i, chunk in enumerate(chunks[: len(names_meta)]):
        name, cat, tags = names_meta[i]
        code = f"{code_prefix}-{i + 1:02d}"
        desc = f"{name}，{'设计师精选' if line == '设计款' else '艺术'}造型，适合门店演示。"
        wig_src = still_life_fallback(cat)
        if not wig_src:
            print(f"[SKIP] {name}: 无静物池")
            continue
        gallery = [p for p in chunk if "副本" not in p.name]
        if len(gallery) < MIN_GALLERY:
            print(f"[WARN] {name}: 陈列仅 {len(gallery)} 张")
        style_id += 1
        showcase_id = build_style(
            style_id, code, code, name, cat, tags, desc, line,
            wig_src, gallery[:MAX_GALLERY], None,
            assets, showcases, catalog, showcase_id,
            source_note="still-life-pool",
        )
    return style_id, showcase_id


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

    designer_dir = MAT / "设计师款"
    if designer_dir.is_dir():
        chunks = chunk_files(list_images(designer_dir), 12)
        style_id, showcase_id = import_designer_or_art(
            chunks, DESIGNER_NAMES, "设计款", "DS",
            style_id, showcase_id, assets, showcases, catalog,
        )

    art_dir = MAT / "Art系列"
    if art_dir.is_dir():
        chunks = chunk_files(list_images(art_dir), 11)
        style_id, showcase_id = import_designer_or_art(
            chunks, ART_NAMES, "Art款", "ART",
            style_id, showcase_id, assets, showcases, catalog,
        )

    models = build_model_assets(v_folders, folder_model_map)

    (DATA / "wig-assets.json").write_text(json.dumps(assets, ensure_ascii=False, indent=2), encoding="utf-8")
    (DATA / "wig-showcases.json").write_text(json.dumps(showcases, ensure_ascii=False, indent=2), encoding="utf-8")
    (DATA / "model-assets.json").write_text(json.dumps(models, ensure_ascii=False, indent=2), encoding="utf-8")
    (DATA / "customer-catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\n完成 v3: {len(assets)} 款式, {len(showcases)} 陈列, {len(models)} 模特")


if __name__ == "__main__":
    main()
