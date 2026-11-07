#!/usr/bin/env python3
"""导入 v3 后数据契约：本体≠陈列 URL、showcase 必有 modelAssetId。"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
MIN_SHOWCASE = 8


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
        if len(ss) < MIN_SHOWCASE:
            errors.append(f"wigAsset {aid} showcase 仅 {len(ss)} 张 (<{MIN_SHOWCASE})")

    if errors:
        print("FAIL:")
        for e in errors:
            print(" -", e)
        return 1

    avg = len(showcases) / len(assets)
    cats: dict[str, int] = {}
    for c in catalog:
        cats[c["category"]] = cats.get(c["category"], 0) + 1
    print(f"OK: {len(assets)} 款式, {len(showcases)} showcase, {len(models)} 模特, 平均 {avg:.1f} 张/款")
    print("分类:", cats)
    return 0


if __name__ == "__main__":
    sys.exit(main())
