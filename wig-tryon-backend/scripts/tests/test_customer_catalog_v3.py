import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from lib.customer_catalog_v3 import (  # noqa: E402
    MODEL_POOL_META,
    assign_model_for_showcase,
    build_folder_model_map,
    is_model_showcase_filename,
    is_wig_bare_filename,
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

    def test_showcase_includes_dated_c_suffix(self):
        names = ["0_C.png", "7.180584_C.jpg", "7.180601_C.jpg"]
        got = showcase_names_from_v_folder(names, "0_C.png")
        self.assertIn("7.180584_C.jpg", got)
        self.assertNotIn("0_C.png", got)
        names = [
            "1_C.jpg", "正_C.png", "IMG_2154_C.jpg", "IMG_2155_C.jpg",
            "0F6A4318_C.JPG", "1_C.jpg", "副本.jpg",
        ]
        wig = "1_C.jpg"
        got = showcase_names_from_v_folder(names, wig)
        self.assertNotIn("1_C.jpg", got)
        self.assertNotIn("正_C.png", got)
        self.assertIn("IMG_2154_C.jpg", got)
        self.assertIn("0F6A4318_C.JPG", got)
        self.assertEqual(got[0], "IMG_2154_C.jpg")


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
        self.assertGreater(len(set(ids)), 1)


if __name__ == "__main__":
    unittest.main()
