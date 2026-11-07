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


def is_wig_bare_filename(name: str) -> bool:
    if name.upper().startswith("IMG_"):
        return False
    if name in WIG_BARE_EXACT:
        return True
    return False


def is_model_showcase_filename(name: str) -> bool:
    if "副本" in name:
        return False
    if name.upper().startswith("IMG_"):
        return True
    if re.match(r"0F6A\d+_C\.(jpg|jpeg|png)$", name, re.I):
        return True
    # V801 等：7.180584_C.jpg、7.19（2）0544 1_C.jpg 棚拍佩戴图
    if re.search(r"_C\.(jpg|jpeg|png)$", name, re.I) and name not in WIG_BARE_EXACT:
        return True
    return False


def is_v_folder_showcase_candidate(name: str, wig_bare_name: str) -> bool:
    if "副本" in name:
        return False
    if name in WIG_BARE_EXACT or name == wig_bare_name:
        return False
    return is_model_showcase_filename(name)


def pick_wig_bare_from_names(names: Sequence[str]) -> Optional[str]:
    for fn in WIG_PRIORITY:
        if fn in names:
            return fn
    for n in sorted(names):
        if is_wig_bare_filename(n):
            return n
    return None


def showcase_names_from_v_folder(names: Sequence[str], wig_bare_name: str) -> List[str]:
    imgs = [n for n in names if is_v_folder_showcase_candidate(n, wig_bare_name)]
    imgs.sort(key=lambda n: (0 if n.upper().startswith("IMG_") else 1, n.lower()))
    return imgs[:MAX_GALLERY]


def split_designer_chunk(filenames: Sequence[str]) -> Tuple[Optional[str], List[str]]:
    """设计师/Art chunk：0F6A*_C 且非 IMG 优先作本体，其余作陈列。"""
    bare_candidates = [
        n for n in filenames
        if re.match(r"0F6A\d+_C\.", n, re.I) and not n.upper().startswith("IMG_")
    ]
    bare_candidates.sort()
    bare = bare_candidates[0] if bare_candidates else None
    showcase = [n for n in filenames if n != bare and "副本" not in n]
    showcase.sort(key=lambda n: n.lower())
    return bare, showcase[:MAX_GALLERY]


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
        return ((base - 1 + (showcase_index // 4)) % 12) + 1
    base = CATEGORY_MODEL_BASE.get(category, 1)
    return ((base - 1 + style_id + showcase_index) % 12) + 1
