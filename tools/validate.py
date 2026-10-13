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


def machine_recipes(d, key, recipes):
    """Recipes a multiblock runs (its recipe type and component filter)."""
    m = d.slice["machines"][key]
    out = []
    for r in recipes:
        if r["machine"] not in m["runs"]:
            continue
        comp = bool(r.get("component"))
        if (m.get("filter") == "component" and not comp) or (m.get("filter") == "not_component" and comp):
            continue
        out.append(r)
    return out


def group_keys(r):
    """Inputs that decide whether recipes in one input group can run from each other's items: items and the
    programmed circuit. Fluids are left out: a machine's undyed fluid hatches are shared by every colour group."""
    keys = set(r.get("in", {}))
    if r.get("circuit") is not None:
        keys.add(f"circuit:{r['circuit']}")
    return frozenset(keys)


def covered(keys, others):
    """True if every input of a recipe is also an input of some recipe in others."""
    union = set().union(*others) if others else set()
    return keys <= union


def group_ok(group):
    keys = [group_keys(r) for r in group]
    return not any(covered(k, keys[:i] + keys[i + 1:]) for i, k in enumerate(keys))


def isolation_groups(recipes, tier_of=lambda r: 1):
    """
    Split a machine's recipes into input groups that AE2 can feed without wrong crafts: within a group, no
    recipe's items are all also items of the group's other recipes (or a pattern provider pushing those recipes
    could make it). Greedy, largest first. A group is one input bus (its items must fit).
    """
    def fits(group):
        # one bus holds the group's distinct items: the bus of the group's highest tier, or the smallest bus
        # that holds its largest recipe (recipes too big for their tier's bus are listed under Bus sizes)
        items = {k for r in group for k in r.get("in", {})}
        cap = max([bus_slots(max(tier_of(r) for r in group))] + [len(r.get("in", {})) for r in group])
        return len(items) <= cap

    groups = []
    for r in sorted(recipes, key=lambda r: (-len(group_keys(r)), r["id"])):
        for g in groups:
            cand = g["recipes"] + [r]
            if group_ok(cand) and fits(cand):
                g["recipes"].append(r)
                g["fluids"] = g["fluids"] or bool(r.get("fluid_in"))
                break
        else:
            groups.append({"fluids": bool(r.get("fluid_in")), "recipes": [r]})
    return groups


def check_isolation(d, rep, recipes):
    """
    AE2 feeding per multiblock, as GTM 7.5.3 groups input handlers (RecipeHelper.addToRecipeHandlerMap): a
    distinct bus is its own group and sees no hatch; a dyed bus or hatch belongs to its colour's group; undyed
    hatches and buses join every colour group. So each input group is one bus; recipes with fluids need a dyed
    (colour group) bus, which also sees the machine's undyed fluid hatches. Limits: one bus per group (6 buses),
    and the machine's different fluids must fit its fluid hatches (2; LV-HV hatches hold one fluid each).
    Errors only if even HV buses can't separate the recipes.
    """
    lim = d.framework["hatches"]["limits"]
    max_buses, max_fluid = lim["item_input_bus"]["max"], lim["fluid_input_hatch"]["max"]
    top = max(TIERS.index(f["max_voltage"]) for f in d.framework["frames"].values())
    upkeep = sorted({f for m in d.framework["module_definitions"].values() for f in (m.get("upkeep") or {})})
    rows = []
    for key, m in d.slice["machines"].items():
        if m["kind"] != "multiblock":
            continue
        rs = machine_recipes(d, key, recipes)
        best = isolation_groups(rs, lambda r: top)
        if len(best) > max_buses:
            rep.error(f"{key}: even with {TIERS[top]} buses its recipes need {len(best)} separate input groups for AE2; "
                      f"a machine has at most {max_buses} input buses")
        fluids = sorted({f for r in rs for f in r.get("fluid_in", {})})
        if len(fluids) > max_fluid:
            rep.error(f"{key}: its recipes use {len(fluids)} fluids ({', '.join(fluids)}) but a machine has at most "
                      f"{max_fluid} fluid input hatches")
        with_upkeep = sorted(set(fluids) | set(upkeep))
        if len(with_upkeep) > max_fluid:
            rep.error(f"{key}: with a batch bus it needs {len(with_upkeep)} fluids ({', '.join(with_upkeep)}) but has at "
                      f"most {max_fluid} fluid input hatches")
        start = isolation_groups(rs, lambda r: lowest_machine_tier(d, r))
        rows.append({"machine": key, "best": best, "start": start, "top": TIERS[top], "fluids": fluids})
        if len(start) > max_buses:
            rep.warn(f"{key}: with each recipe's own-tier buses, running all {len(rs)} of its recipes from one machine "
                     f"needs {len(start)} input groups (it has {max_buses} buses); split them over several machines or "
                     f"use bigger buses ({len(best)} groups with {TIERS[top]} buses)")
    return rows


def lowest_machine_tier(d, r):
    """Lowest tier of a multiblock frame that runs this recipe (it's what bus size the player starts with)."""
    need = recipe_tier(r["eut"])
    tiers = []
    for key, m in d.slice["machines"].items():
        if m["kind"] == "multiblock" and r in machine_recipes(d, key, [r]):
            tiers += [TIERS.index(f["max_voltage"]) for f in d.framework["frames"].values()]
    ok = [t for t in tiers if t >= need]
    return min(ok) if ok else need


def check_buses(d, rep, recipes):
    """Recipes run by a multiblock whose distinct item inputs don't fit one input bus of their tier."""
    rows = []
    for r in recipes:
        if not any(r in machine_recipes(d, k, [r]) for k, m in d.slice["machines"].items() if m["kind"] == "multiblock"):
            continue
        n = len(r.get("in", {}))
        t = lowest_machine_tier(d, r)
        if n > bus_slots(t):
            need = t
            while bus_slots(need) < n:
                need += 1
            rows.append((r["id"], n, TIERS[t], bus_slots(t), TIERS[need]))
    return rows


def check_consistency(d, rep, recipes):
    items = d.slice["items"]
    # fluid flags match the IDs
    for k, gid in d.ids["items"].items():
        is_fluid = gid.startswith("fluid:")
        if k in items and bool(items[k].get("fluid")) != is_fluid:
            rep.error(f"'{k}': slice items say fluid={bool(items[k].get('fluid'))}, design/ids.yaml says {gid}")
    for r in recipes:
        for k in r.get("in", {}):
            if d.ids["items"].get(k, "").startswith("fluid:"):
                rep.error(f"{r['id']}: '{k}' is a fluid but listed under item inputs")
        for k in r.get("fluid_in", {}):
            if k in d.ids["items"] and not d.ids["items"][k].startswith("fluid:"):
                rep.error(f"{r['id']}: '{k}' is an item but listed under fluid inputs")
    # changed recipes say which stock recipes they replace
    for r in d.recipes_with_status("changed"):
        if not d.ids.get("replaces", {}).get(r["id"]):
            rep.error(f"{r['id']} is a changed recipe but design/ids.yaml 'replaces' doesn't name the stock recipes it replaces")
    # component recipes (the Component Assembler's filter) make exactly the component items
    comp_out = {k for r in d.slice["recipes"] if r.get("component") for k in r["out"]}
    comp_items = {k for k, it in items.items() if it.get("kind") == "component"}
    if comp_out != comp_items:
        rep.error(f"recipes marked component make {sorted(comp_out)}, but component items are {sorted(comp_items)}")
    # frame materials agree between multiblocks.yaml and ids.yaml
    for key, f in d.framework["frames"].items():
        if d.ids["items"].get("casing_" + key) != f["casing"]:
            rep.error(f"frame {key}: casing {f['casing']} != design/ids.yaml casing_{key}")
        if d.ids["items"].get("frame_" + key) != f["frame_box"]:
            rep.error(f"frame {key}: frame box {f['frame_box']} != design/ids.yaml frame_{key}")


def write_report(rep, subset, shared, buses, isolation):
    lines = ["# Validation report", "",
             "Generated by `tools/validate.py` from design/. Do not edit.", "",
             f"Errors: {len(rep.errors)}. Warnings: {len(rep.warnings)}.", ""]
    if rep.errors:
        lines += ["## Errors", ""] + [f"- {e}" for e in rep.errors] + [""]
    if rep.warnings:
        lines += ["## Warnings", ""] + [f"- {w}" for w in rep.warnings] + [""]
    lines += ["## Recipe conflicts", "",
              "Two recipes in one machine type conflict when every input of one (items, fluids, programmed circuit) is",
              "also an input of the other: an input bus loaded for the larger recipe can run the smaller one, and only a",
              "different programmed circuit fixes that. These are errors; this design has " + (f"{len(subset)}." if subset else "none."), "",
              "## Feeding the multiblocks with AE2", "",
              "A pattern provider in non-blocking mode pushes each recipe's inputs into the machine. If one recipe's items",
              "are all also items of other recipes fed into the same bus, AE2's pushes for those recipes can make it",
              "instead. So recipes are split into input groups, one input bus each. How GTM 7.5.3 groups a machine's",
              "inputs: a **distinct** bus is a group of its own and sees no fluid hatch; a **dyed** bus (spray can) forms",
              "its colour's group, and every **undyed** hatch joins every colour group. So: recipes with fluids, and every",
              "recipe while a batch bus is installed (lubricant), go on dyed buses, one colour per group, with the",
              "machine's fluids in undyed hatches (one fluid per LV-HV hatch); item-only recipes can use distinct",
              "buses. A machine has at most 6 input buses and 2 fluid hatches. Groups below are found greedily; any",
              "grouping that respects the rule works.", ""]
    for row in isolation:
        for label, groups, note in (("with each recipe's own-tier bus", row["start"], ""),
                                    (f"with {row['top']} buses", row["best"], "")):
            lines += [f"**{row['machine']}**, {label}: {len(groups)} groups"
                      + (f"; fluids in undyed hatches: {', '.join(row['fluids'])}" if row["fluids"] else ""), ""]
            for i, g in enumerate(groups, 1):
                kind = "dyed bus (uses fluids)" if g["fluids"] else "distinct or dyed bus"
                lines.append(f"{i}. {kind}: {', '.join(r['id'] for r in g['recipes'])}")
            lines.append("")
    lines += ["## Bus sizes", "",
              "Multiblock recipes with more distinct item inputs than one input bus of their tier holds (GTM buses",
              "hold (1 + tier)^2 slots: LV 4, MV 9, HV 16; the programmed circuit has its own slot). At that tier",
              "they need a bigger bus to be fed on their own.", "",
              "| Recipe | Item inputs | Lowest machine tier | Bus slots there | Bus that holds it |", "|---|---|---|---|---|"]
    lines += [f"| {r} | {n} | {t} | {s} | {nt} |" for r, n, t, s, nt in buses]
    lines += ["", "## Recipes that share some inputs", "",
              "Not conflicts: each still needs inputs the other doesn't have. Listed for reference.", "",
              "| Machine type | Recipe | Recipe | Shared inputs |", "|---|---|---|---|"]
    lines += [f"| {t} | {a} | {b} | {', '.join(s)} |" for t, a, b, s in shared]
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
    check_consistency(d, rep, recipes)
    subset, shared = check_conflicts(d, rep, recipes)
    isolation = check_isolation(d, rep, recipes)
    buses = check_buses(d, rep, recipes)
    changed = write_report(rep, subset, shared, buses, isolation)
    if "--check" in sys.argv and changed:
        rep.error("docs/validation-report.md was stale (now regenerated; commit it)")
    for w in rep.warnings:
        print("warning:", w)
    for e in rep.errors:
        print("error:", e)
    print(f"validator: {len(rep.errors)} errors, {len(rep.warnings)} warnings (docs/validation-report.md)")
    sys.exit(1 if rep.errors else 0)


if __name__ == "__main__":
    main()
