#!/usr/bin/env python3
"""批量导入客户 V 系列素材到 WigAsset + WigShowcase + customer-catalog.json。"""
import json
import re
import shutil
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

ROOT = Path(__file__).resolve().parents[1]
MAT = ROOT.parent / "发型素材-ai测试用-刘经理提供"
DATA = ROOT / "data"
STORAGE = DATA / "storage"
BASE_URL = "http://localhost:8080/files"

WIG_PRIORITY = ["正_C.png", "正_C.jpg", "0_C.png", "0_C.jpg", "1_C.jpg", "1_C.JPG"]
SKIP_SHOWCASE = {"0_C.png", "0_C.jpg", "1_C.jpg", "1_C.JPG", "正_C.png", "正_C.jpg", "后_C.png", "后_C.jpg"}


def parse_folder(name: str) -> dict:
    m = re.match(r"^(V\d+[-\w]*?)(?:（(.+?)）)?$", name.replace(" ", ""))
    code = m.group(1) if m else name
    subtitle = (m.group(2) or "").strip()
    display = f"{code} {subtitle}".strip() if subtitle else code
    cat = "短发" if any(k in display for k in ("BOBO", "bobo", "短", "S1", "S2", "S3", "S5", "S6", "S7", "S9", "S10", "K1", "K2", "K9")) else "中长发"
    if any(k in display for k in ("卷", "Q")):
        cat = "卷发" if "Q" in code else cat
    desc = f"客户素材款 {display}，真人发丝质感，自然贴合头型，适合试戴演示。"
    return {
        "code": code,
        "name": display,
        "category": cat,
        "colorFamily": "自然黑",
        "descriptionForAi": desc,
        "tags": ["客户素材", code.split("-")[0]],
    }


def pick_wig_image(folder: Path) -> Optional[Path]:
    for fn in WIG_PRIORITY:
        p = folder / fn
        if p.exists():
            return p
    return None


def pick_showcase_image(folder: Path) -> Optional[Path]:
    imgs = [p for p in folder.iterdir() if p.suffix.lower() in {".jpg", ".jpeg", ".png"} and p.name not in SKIP_SHOWCASE]
    imgs.sort(key=lambda p: (0 if p.name.upper().startswith("IMG_") else 1, -p.stat().st_size))
    return imgs[0] if imgs else None


def main() -> None:
    if not MAT.is_dir():
        raise SystemExit(f"素材目录不存在: {MAT}")

    folders = sorted([p for p in MAT.iterdir() if p.is_dir() and p.name.startswith("V")])
    if not folders:
        raise SystemExit("未找到 V 系列文件夹")

    (STORAGE / "wig-assets").mkdir(parents=True, exist_ok=True)
    (STORAGE / "showcases").mkdir(parents=True, exist_ok=True)
    DATA.mkdir(parents=True, exist_ok=True)

    now = datetime.now(timezone.utc).isoformat()
    ts = time.time()
    assets = []
    showcases = []
    catalog = []

    for i, folder in enumerate(folders, start=1):
        meta = parse_folder(folder.name)
        wig_src = pick_wig_image(folder)
        if wig_src is None:
            print(f"[SKIP] {folder.name}: 无本体图")
            continue

        ext = wig_src.suffix.lower()
        wig_dst = STORAGE / "wig-assets" / f"{i}{ext}"
        shutil.copy2(wig_src, wig_dst)
        wig_url = f"{BASE_URL}/wig-assets/{i}{ext}"

        assets.append({
            "id": i,
            "code": meta["code"],
            "name": meta["name"],
            "productImage": wig_url,
            "productImageThumbnail": wig_url,
            "descriptionForAi": meta["descriptionForAi"],
            "category": meta["category"],
            "colorFamily": meta["colorFamily"],
            "tags": meta["tags"],
            "createdAt": ts,
            "createdBy": "customer-batch-import",
        })

        showcase_url = wig_url
        show_src = pick_showcase_image(folder)
        if show_src:
            sext = show_src.suffix.lower()
            show_dst = STORAGE / "showcases" / f"{i}-customer{sext}"
            shutil.copy2(show_src, show_dst)
            showcase_url = f"{BASE_URL}/showcases/{i}-customer{sext}"

        showcases.append({
            "id": i,
            "wigAssetId": i,
            "modelAssetId": None,
            "resultImage": showcase_url,
            "status": "APPROVED",
            "isPrimary": True,
            "changeScore": None,
            "prompt": f"客户素材成品图 {meta['name']}",
            "strategyUsed": "customer-import",
            "errorMessage": None,
            "createdAt": now,
            "reviewedAt": now,
            "reviewedBy": "customer-batch-import",
        })

        catalog.append({
            "id": i,
            "name": meta["name"],
            "category": meta["category"],
            "material": "100% 真人发丝",
            "priceMin": 2800,
            "priceMax": 6800,
            "tags": meta["tags"] + ["客户款"],
            "faceTypes": ["圆脸", "方脸", "鹅蛋脸", "长脸"],
            "rating": 4.8,
            "reviews": 88,
            "suitable": "多种脸型",
            "weight": "约 110g",
            "craft": "手工/机制结合",
            "description": meta["descriptionForAi"],
            "colorFamily": meta["colorFamily"],
            "capStructure": "全手织",
            "targetCrowds": ["客户演示"],
            "sceneTags": ["日常", "试戴"],
            "storeIds": [1, 2],
            "hairlineCoverage": 3,
            "whiteHairCoverage": 3,
            "isHot": i <= 5,
            "isNew": True,
        })
        print(f"[OK] {i:02d} {folder.name} -> wig={wig_src.name}, showcase={show_src.name if show_src else 'fallback'}")

    (DATA / "wig-assets.json").write_text(json.dumps(assets, ensure_ascii=False, indent=2), encoding="utf-8")
    (DATA / "wig-showcases.json").write_text(json.dumps(showcases, ensure_ascii=False, indent=2), encoding="utf-8")
    (DATA / "customer-catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n完成: {len(assets)} 本体 + {len(showcases)} 成品图 + {len(catalog)} 商品条目")


if __name__ == "__main__":
    main()
