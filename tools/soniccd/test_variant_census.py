#!/usr/bin/env python3
"""Tests for variant_census.py on a synthetic two-stage disassembly.

Run: python3 -I tools/soniccd/test_variant_census.py
"""
from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import variant_census as vc  # noqa: E402

FILES = {
    "make.sh": (
        "assemble 'Level\\Palmtree Panic\\Act 1 Present.asm' '..\\out\\files\\R11A__.MMD' 'a.lst' /e DEMO=0\n"
        "assemble 'Level\\Palmtree Panic\\Act 1 Past.asm' '..\\out\\files\\R11B__.MMD' 'b.lst' /e DEMO=0\n"
        "assemble 'Level\\Palmtree Panic\\Act 1 Present.asm' '..\\out\\files\\DEMO11A.MMD' 'c.lst' /e DEMO=1\n"
    ),
    "src/_Include/System.inc": (
        "FID_R11A\trs.b\t1\t; Palmtree Panic Act 1 Present\n"
        "FID_R11B\trs.b\t1\t; Palmtree Panic Act 1 Past\n"
    ),
    "src/Level/Palmtree Panic/Act 1 Present.asm": (
        'STAGE_TIME\tEQU\t0\n'
        'oSize\tEQU\t$40\n'
        '\tinclude\t"Level/Common.asm"\n'
        '\tinclude\t"Level/Engine.asm"\n'
        '\tinclude\t"Level/Palmtree Panic/Scroll (Act 1 Present).asm"\n'
        '\tinclude\t"Level/Palmtree Panic/Objects/Flower/Main.asm"\n'
    ),
    "src/Level/Palmtree Panic/Act 1 Past.asm": (
        'STAGE_TIME\tEQU\t1\n'
        'oSize\tEQU\t$40\n'
        '\tinclude\t"Level/Common.asm"\n'
        '\tinclude\t"Level/Engine.asm"\n'
        '\tinclude\t"Level/Palmtree Panic/Scroll (Act 1 Past).asm"\n'
    ),
    "src/Level/Common.asm": "Common_Run:\n\tmoveq\t#0,d0 ; comment\n\trts\n",
    "src/Level/Engine.asm": (
        "Engine_Run:\n"
        "\tif STAGE_TIME=0\n"
        "\t\tmove.w\t#1,d0\n"
        "\telse\n"
        "\t\tmove.w\t#2,d0\n"
        "\tendif\n"
        "\tif def(MISSING)\n"
        "\t\tnop\n"
        "\tendif\n"
        "\trts\n"
    ),
    # Same code, only an address label and a zone prefix renamed.
    "src/Level/Palmtree Panic/Scroll (Act 1 Present).asm": (
        "R11A_Scroll:\n\tmove.w\tcamX,d0\nloc_1000:\n\tdbf\td0,loc_1000\n\trts\n"
        "Scroll_Two:\n\tmove.w\tfoo,d0\n\tmove.w\tfoo,d1\n\trts\n"
    ),
    "src/Level/Palmtree Panic/Scroll (Act 1 Past).asm": (
        "R11B_Scroll:\n\tmove.w\tcamX,d0\nloc_2000:\n\tdbf\td0,loc_2000\n\trts\n"
        "Scroll_Two:\n\tmove.w\tbar,d0\n\tmove.w\tbaz,d1\n\trts\n"
    ),
    "src/Level/Palmtree Panic/Objects/Flower/Main.asm": "Flower_Main:\n\trts\n",
    "src/Level/Legacy Padding Tail.asm": "\tdc.b 0\n",
}


def build(root: Path) -> None:
    for rel, text in FILES.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)


class ExpressionTests(unittest.TestCase):
    def test_operators_and_def(self):
        ev = vc.Evaluator({"DEMO": 0, "REGION": 1, "A": 5})
        self.assertEqual(ev.eval("(A<>5)|(DEMO=0)"), 1)
        self.assertEqual(ev.eval("(REGION=1)&(DEMO<>0)"), 0)
        self.assertEqual(ev.eval("def(A)"), 1)
        self.assertEqual(ev.eval("def(B)"), 0)
        self.assertEqual(ev.eval("$10+%11*2"), 22)
        with self.assertRaises(vc.Undefined):
            ev.eval("B=1")

    def test_number_folding(self):
        self.assertEqual(vc.fold_numbers("#9+1,d1"), vc.fold_numbers("#$A,d1"))


class CensusTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.root = Path(cls.tmp.name)
        build(cls.root)
        cls.result = vc.run_census(cls.root, "usa", compare_regions=False)

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_stages_resolved(self):
        self.assertEqual(sorted(self.result["stages"]), ["R11A", "R11B"])
        a = self.result["stages"]["R11A"]["files"]
        self.assertIn("Level/Palmtree Panic/Objects/Flower/Main.asm", a)
        self.assertNotIn("Level/Palmtree Panic/Objects/Flower/Main.asm", self.result["stages"]["R11B"]["files"])
        self.assertEqual(self.result["stages"]["R11A"]["unresolved_includes"], [])

    def test_identical_shared_file(self):
        f = self.result["files"]["Level/Common.asm"]
        self.assertEqual(f["stage_count"], 2)
        self.assertEqual(len(f["classes"]), 1)

    def test_conditional_assembly_splits_classes(self):
        f = self.result["files"]["Level/Engine.asm"]
        self.assertEqual(len(f["classes"]), 2)
        r = self.result["routines_with_differences"]["Engine_Run"]
        self.assertEqual(r["worst_kind"], "instructions-differ")
        self.assertEqual([len(g["stages"]) for g in r["groups"]], [1, 1])

    def test_rename_only_merges_and_conflict_is_flagged(self):
        scroll = self.result["families"]["scroll"]
        self.assertEqual(scroll["file_count"], 2)
        kinds = {n: r["worst_kind"] for n, r in self.result["routines_with_differences"].items()}
        self.assertNotIn("ZONE_Scroll", kinds)          # prefix + address-label rename: identical
        self.assertEqual(kinds["Scroll_Two"], "symbol-operands-differ")

    def test_zone_only_and_objects(self):
        self.assertIn("Level/Palmtree Panic/Objects/Flower/Main.asm",
                      self.result["zone_only_files"]["Palmtree Panic"])
        self.assertIn("flower", self.result["objects"])
        self.assertEqual(self.result["stage_only_files"]["R11A"],
                         ["Level/Palmtree Panic/Act 1 Present.asm",
                          "Level/Palmtree Panic/Objects/Flower/Main.asm",
                          "Level/Palmtree Panic/Scroll (Act 1 Present).asm"])

    def test_demo_build_and_unreferenced_legacy(self):
        self.assertIn("DEMO11A", self.result["demo_builds"])
        self.assertNotIn("Level/Legacy Padding Tail.asm", self.result["files"])
        self.assertEqual(vc.kind_of("Level/Legacy Padding Tail.asm", 3), "padding-legacy data")
        self.assertEqual(vc.kind_of("Level/Tidal Tempest/R41A Legacy Scroll.asm", 518), "legacy-named code")
        self.assertEqual(vc.kind_of("Level/Collision Floor.asm"), "real")

    def test_cli_writes_outputs(self):
        with tempfile.TemporaryDirectory() as out:
            self.assertEqual(vc.main([str(self.root), out, "--no-region-compare"]), 0)
            data = json.loads((Path(out) / "variant-census.json").read_text())
            self.assertEqual(data["meta"]["stage_count"], 2)
            self.assertIn("Engine_Run", (Path(out) / "variant-census.md").read_text())


if __name__ == "__main__":
    unittest.main()
