#!/usr/bin/env python3
"""Validate the design files. Exits non-zero on errors and writes docs/validation-report.md.

Static checks (here):
  - every item a recipe, build or machine uses is defined (supplied, made by a recipe, or built)
  - every item and machine has a game ID in design/ids.yaml; durations are whole ticks
  - every intermediate, part and component has a recipe that makes it
  - recipe conflicts within a machine type (the design's recipes and builds):
      error   one recipe's inputs (items, fluids, programmed circuit) are a subset of another's, so a
              bus loaded for the larger one can run the smaller one; neither circuits nor distinct
              buses can separate them
      listed  recipes that share some inputs: separable with distinct input buses
  - bus sizes: recipes with more distinct item inputs than one input bus holds at the lowest tier
    that can run them (listed; such a recipe needs a bigger bus in distinct mode)
Live checks (GameTests in addon/, from the expectations file tools/generate.py writes):
  - every ID exists in the game; stock recipes still match GTM 7.5.3; our recipes loaded as designed;
    our recipes don't conflict with any other recipe of the same type in the game.
"""
import itertools
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import design  # noqa: E402

TIERS = design.TIERS
VOLTS = [8, 32, 128, 512, 2048, 8192, 32768, 131072, 524288, 2097152]


def recipe_tier(eut):
    t = 0
    while t < len(VOLTS) - 1 and VOLTS[t] < eut:
        t += 1
    return t


def bus_slots(tier):
    return (1 + min(9, tier)) ** 2


class Report:
    def __init__(self):
        self.errors, self.warnings, self.sections = [], [], []

    def error(self, msg):
        self.errors.append(msg)

    def warn(self, msg):
        self.warnings.append(msg)


def all_recipes(d):
    """Design recipes and builds, each with a 'kind' label for the report."""
    out = [dict(r, kind="recipe") for r in d.slice["recipes"]]
    out += [dict(r, kind="build", status="new") for r in d.build_recipes()]
    out += [dict(r, kind="build") for r in d.stock_casing_recipes()]
    return out


def check_items(d, rep, recipes):
    items = d.slice["items"]
    made = {}
    for r in recipes:
        for k in r["out"]:
            made.setdefault(k, r["id"])
    used = set()
    for r in recipes:
        for k in list(r.get("in", {})) + list(r.get("fluid_in", {})):
            used.add(k)
            if k not in items and k not in made:
                rep.error(f"{r['id']}: input '{k}' is neither defined in slice items nor made by a recipe or build")
        for k in r["out"]:
            if k not in items and not r.get("_build") and r.get("kind") != "build":
                rep.error(f"{r['id']}: output '{k}' is not defined in slice items")
    for key, m in d.framework["module_definitions"].items():
        for k in (m.get("upkeep") or {}):
            used.add(k)
            if k not in items:
                rep.error(f"module {key}: upkeep '{k}' is not defined in slice items")
    for k, it in items.items():
        if it.get("kind") in ("intermediate", "part", "component", "byproduct") and k not in made:
            rep.error(f"item '{k}' ({it['kind']}) has no recipe that makes it")
    # every referenced key needs a game ID
    for k in sorted(used | set(made) | set(items)):
        if k not in d.ids["items"]:
            rep.error(f"'{k}' has no game ID in design/ids.yaml")
    for k in d.ids["items"]:
        if k not in used and k not in made and k not in items:
            rep.warn(f"design/ids.yaml maps '{k}', which no recipe, build or item uses")


def check_recipes(d, rep, recipes):
    seen = set()
    for r in recipes:
        if r["id"] in seen:
            rep.error(f"duplicate recipe id {r['id']}")
        seen.add(r["id"])
        try:
            d.recipe_type(r["machine"])
        except KeyError as e:
            rep.error(f"{r['id']}: {e}")
        try:
            d.ticks(r["duration"])
        except ValueError as e:
            rep.error(f"{r['id']}: {e}")
        if not r.get("eut", 0) > 0:
            rep.error(f"{r['id']}: EU/t must be positive")
        if not r["out"]:
            rep.error(f"{r['id']}: no outputs")


def input_keys(r):
    keys = set(r.get("in", {})) | {"fluid:" + k for k in r.get("fluid_in", {})}
    if r.get("circuit") is not None:
        keys.add(f"circuit:{r['circuit']}")
    return frozenset(keys)


def check_conflicts(d, rep, recipes):
    by_type = {}
    for r in recipes:
        by_type.setdefault(d.recipe_type(r["machine"]), []).append(r)
    subset, shared = [], []
    for rtype, rs in sorted(by_type.items()):
        for a, b in itertools.combinations(rs, 2):
            ka, kb = input_keys(a), input_keys(b)
            if ka <= kb or kb <= ka:
                small, big = (a, b) if ka <= kb else (b, a)
                subset.append((rtype, small["id"], big["id"]))
                rep.error(f"conflict in {rtype}: {small['id']}'s inputs are all in {big['id']}'s, so a bus loaded "
                          f"for {big['id']} can run {small['id']}. Give them different programmed circuits.")
            elif ka & kb:
                shared.append((rtype, a["id"], b["id"], sorted(ka & kb)))
    return subset, shared


def lowest_machine_tier(d, r):
    """Lowest tier at which a machine in the design can run this recipe (no machine runs at ULV)."""
    need = recipe_tier(r["eut"])
    tiers = []
    for key, m in d.slice["machines"].items():
        if m["kind"] == "multiblock":
            if r["machine"] not in m["runs"]:
                continue
            flt = m.get("filter")
            comp = bool(r.get("component"))
            if (flt == "component" and not comp) or (flt == "not_component" and comp):
                continue
            tiers += [TIERS.index(f["max_voltage"]) for f in d.framework["frames"].values()]
        elif key == r["machine"]:
            tiers += [TIERS.index(t) for t in m["tiers"]]
    ok = [t for t in tiers if t >= need]
    return min(ok) if ok else need


def check_buses(d, rep, recipes):
    rows = []
    for r in recipes:
        n = len(r.get("in", {}))
        t = lowest_machine_tier(d, r)
        if n > bus_slots(t):
            need = t
            while bus_slots(need) < n:
                need += 1
            rows.append((r["id"], n, TIERS[t], bus_slots(t), TIERS[need]))
    return rows


def write_report(rep, subset, shared, buses):
    lines = ["# Validation report", "",
             "Generated by `tools/validate.py` from design/. Do not edit.", "",
             f"Errors: {len(rep.errors)}. Warnings: {len(rep.warnings)}.", ""]
    if rep.errors:
        lines += ["## Errors", ""] + [f"- {e}" for e in rep.errors] + [""]
    if rep.warnings:
        lines += ["## Warnings", ""] + [f"- {w}" for w in rep.warnings] + [""]
    lines += ["## Recipe conflicts", "",
              "Recipes in one machine type that accept the same inputs (CLAUDE.md). Subset conflicts are errors;",
              "this design has " + (f"{len(subset)}." if subset else "none."), "",
              "Recipes that only share some inputs are fine with distinct input buses (a pattern provider in",
              "non-blocking mode feeding its own bus). They would mix in a pooled bus:", "",
              "| Machine | Recipe | Recipe | Shared inputs |", "|---|---|---|---|"]
    lines += [f"| {t} | {a} | {b} | {', '.join(s)} |" for t, a, b, s in shared]
    lines += ["", "## Bus sizes", "",
              "Recipes with more distinct item inputs than one input bus holds at the lowest tier that runs them.",
              "Pooled buses (normal mode) hold them; a distinct bus needs the tier on the right.", "",
              "| Recipe | Item inputs | Lowest machine tier | Bus slots there | Distinct bus needed |", "|---|---|---|---|---|"]
    lines += [f"| {r} | {n} | {t} | {s} | {nt} |" for r, n, t, s, nt in buses]
    text = "\n".join(lines) + "\n"
    path = ROOT / "docs/validation-report.md"
    changed = not path.exists() or path.read_text() != text
    path.write_text(text)
    return changed


def main():
    d = design.load()
    rep = Report()
    recipes = all_recipes(d)
    check_recipes(d, rep, recipes)
    check_items(d, rep, recipes)
    subset, shared = check_conflicts(d, rep, recipes)
    buses = check_buses(d, rep, recipes)
    changed = write_report(rep, subset, shared, buses)
    if "--check" in sys.argv and changed:
        rep.error("docs/validation-report.md was stale (now regenerated; commit it)")
    for w in rep.warnings:
        print("warning:", w)
    for e in rep.errors:
        print("error:", e)
    print(f"validator: {len(rep.errors)} errors, {len(rep.warnings)} warnings, {len(shared)} shared-input pairs, "
          f"{len(buses)} bus-size notes (docs/validation-report.md)")
    sys.exit(1 if rep.errors else 0)


if __name__ == "__main__":
    main()
