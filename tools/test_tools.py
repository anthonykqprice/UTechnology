"""Unit tests for the generator and validator (run by tools/check.sh)."""
import copy
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import design  # noqa: E402
import validate  # noqa: E402


class ValidatorTests(unittest.TestCase):
    def setUp(self):
        self.d = design.load()

    def run_checks(self, d):
        rep = validate.Report()
        recipes = validate.all_recipes(d)
        validate.check_recipes(d, rep, recipes)
        validate.check_items(d, rep, recipes)
        validate.check_consistency(d, rep, recipes)
        validate.check_conflicts(d, rep, recipes)
        validate.check_isolation(d, rep, recipes)
        return rep

    def test_design_is_clean(self):
        self.assertEqual(self.run_checks(self.d).errors, [])

    def test_subset_conflict_is_an_error_and_a_circuit_fixes_it(self):
        d = copy.deepcopy(self.d)
        d.slice["recipes"].append({"id": "x", "status": "new", "machine": "wiremill", "eut": 7, "duration": 1,
                                   "in": {"ingot_copper": 1}, "out": {"wire_copper_single": 1}})
        # wire_copper_single uses circuit 1, so the new recipe (no circuit) is a subset of it
        self.assertTrue(any("conflict" in e and "x" in e for e in self.run_checks(d).errors))
        d.slice["recipes"][-1]["circuit"] = 3
        self.assertFalse(any("conflict" in e for e in self.run_checks(d).errors))

    def test_kits_are_kept_apart_from_casings(self):
        d = copy.deepcopy(self.d)
        for b in d.mb["builds"]:
            b.pop("circuit", None) if b["builds"] == "retrofit_kit" else None
        errs = self.run_checks(d).errors
        self.assertTrue(any("retrofit_kit_steel" in e and "casing_steel" in e for e in errs), errs)

    def test_unknown_input_and_missing_id(self):
        d = copy.deepcopy(self.d)
        d.slice["recipes"][0]["in"]["unobtainium"] = 1
        errs = self.run_checks(d).errors
        self.assertTrue(any("unobtainium" in e and "neither defined" in e for e in errs))
        self.assertTrue(any("unobtainium" in e and "no game ID" in e for e in errs))

    def test_too_many_fluids_is_an_error(self):
        d = copy.deepcopy(self.d)
        # a third fluid in the Assembly Hall's recipes: more fluids than fluid hatches
        d.slice["items"]["water"] = {"kind": "supplied", "fluid": True}
        d.ids["items"]["water"] = "fluid:minecraft:water"
        d.slice["recipes"].append({"id": "y", "status": "new", "machine": "assembler", "eut": 7, "duration": 1,
                                   "circuit": 21, "in": {"plate_steel": 1}, "fluid_in": {"water": 1, "lubricant": 1},
                                   "out": {"cable_tin_single": 1}})
        rep = self.run_checks(d)
        self.assertTrue(any("fluid input hatches" in e for e in rep.errors), rep.errors)

    def test_changed_recipe_needs_replaces(self):
        d = copy.deepcopy(self.d)
        d.ids["replaces"].pop("motor_mv")
        self.assertTrue(any("motor_mv is a changed recipe" in e for e in self.run_checks(d).errors))

    def test_fluid_mismatch(self):
        d = copy.deepcopy(self.d)
        d.ids["items"]["lubricant"] = "gtceu:lubricant"
        self.assertTrue(any("'lubricant'" in e for e in self.run_checks(d).errors))

    def test_frame_ids_agree(self):
        d = copy.deepcopy(self.d)
        d.framework["frames"]["steel"]["casing"] = "gtceu:steel_brick_casing"
        self.assertTrue(any("frame steel: casing" in e for e in self.run_checks(d).errors))

    def test_fractional_ticks_rejected(self):
        with self.assertRaises(ValueError):
            design.Design.ticks(0.01)


class DesignTests(unittest.TestCase):
    def test_build_rule(self):
        d = design.load()
        builds = {b["id"]: b for b in d.build_recipes()}
        self.assertEqual(builds["coil_winder_lv"]["eut"], 7)           # LV build at ULV
        self.assertEqual(builds["retrofit_kit_aluminium"]["eut"], 30)  # MV frame kit at LV
        self.assertEqual(builds["module_batch_bus"]["eut"], 120)       # HV module at MV
        self.assertEqual(builds["retrofit_kit_aluminium"]["circuit"], 7)
        self.assertNotIn("casing_steel", builds)                       # stock GTM recipe

    def test_recipe_ids(self):
        d = design.load()
        r = next(r for r in d.slice["recipes"] if r["id"] == "stator_coil_mv")
        self.assertEqual(d.builder_id(r), "utechnology:stator_coil_mv")
        self.assertEqual(d.recipe_id(r), "utechnology:coil_winder/stator_coil_mv")


class PackTests(unittest.TestCase):
    def test_addon_dev_mods_match_the_pack(self):
        """The addon's GameTests and dev client run the exact mod files the pack ships (gradle.properties)."""
        root = pathlib.Path(__file__).resolve().parent.parent
        props = dict(l.split("=", 1) for l in (root / "addon/gradle.properties").read_text().splitlines()
                     if "=" in l and not l.startswith("#"))
        for prop, toml in [("kubejs_modrinth", "kubejs"), ("rhino_modrinth", "rhino"),
                           ("architectury_modrinth", "architectury-api"), ("emi_modrinth", "emi")]:
            text = (root / f"pack/mods/{toml}.pw.toml").read_text()
            self.assertIn(f'version = "{props[prop].strip()}"', text, f"{prop} differs from pack/mods/{toml}.pw.toml")

    def test_emi_is_pinned(self):
        """GTM 7.5.3 patches EMI internals (docs/mods.md); packwiz update must not move EMI untested."""
        root = pathlib.Path(__file__).resolve().parent.parent
        self.assertIn("pin = true", (root / "pack/mods/emi.pw.toml").read_text())


if __name__ == "__main__":
    unittest.main()
