"""Load the design files and answer the questions the generator and validator ask about them."""
import pathlib

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
TIERS = ["ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV"]


class Design:
    def __init__(self, slice_, mb, ids):
        self.slice = slice_
        self.mb = mb
        self.ids = ids
        self.framework = mb["framework"]

    # ---- IDs
    def game_id(self, key):
        try:
            return self.ids["items"][key]
        except KeyError:
            raise KeyError(f"design/ids.yaml has no ID for '{key}'") from None

    def item_id(self, key):
        gid = self.game_id(key)
        if gid.startswith("fluid:"):
            raise ValueError(f"'{key}' is a fluid, not an item")
        return gid

    def fluid_id(self, key):
        gid = self.game_id(key)
        if not gid.startswith("fluid:"):
            raise ValueError(f"'{key}' is not a fluid (design/ids.yaml: {gid})")
        return gid[len("fluid:"):]

    def item_ingredient(self, key, qty):
        gid = self.item_id(key)
        return gid if qty == 1 else f"{qty}x {gid}"

    def recipe_type(self, machine):
        try:
            return self.ids["recipe_types"][machine]
        except KeyError:
            raise KeyError(f"design/ids.yaml has no recipe type for machine '{machine}'") from None

    def all_game_ids(self):
        return list(self.ids["items"].values()) + ["recipe_type:" + t for t in self.ids["recipe_types"].values()]

    # ---- recipes
    @staticmethod
    def ticks(seconds):
        t = round(seconds * 20)
        if abs(t - seconds * 20) > 1e-6:
            raise ValueError(f"duration {seconds} s is not a whole number of ticks")
        return t

    def builder_id(self, r):
        """The ID passed to GTM's KubeJS recipe builder."""
        prefix = "build/" if r.get("_build") else ""
        return f"utechnology:{prefix}{r['id']}"

    def recipe_id(self, r):
        """The recipe's ID in game: GTM's KubeJS builder puts the recipe type's path in front."""
        prefix = "build/" if r.get("_build") else ""
        return f"utechnology:{self.recipe_type(r['machine']).split(':')[1]}/{prefix}{r['id']}"

    def recipes_with_status(self, status):
        return [r for r in self.slice["recipes"] if r["status"] == status]

    def removed_recipe_ids(self):
        out = []
        for r in self.recipes_with_status("changed"):
            out += self.ids.get("replaces", {}).get(r["id"], [])
        return out

    def build_rule(self):
        return self.mb["build_rule"]

    def _build(self, bid, tier, inputs, output_key, source):
        rule = self.build_rule()
        return {"id": bid, "_build": True, "_source": source, "tier": tier, "machine": rule["machine"],
                "eut": rule["eut"][tier], "duration": rule["duration"], "in": dict(inputs), "out": {output_key: 1}}

    def build_recipes(self):
        """Every build the design defines, except stock casings, as assembler recipes (build_rule)."""
        out = []
        for b in self.slice.get("builds", []):
            r = self._build(b["id"], b["tier"], b["in"], b["id"], "slice1-mechanical.yaml builds")
            if b.get("circuit") is not None:
                r["circuit"] = b["circuit"]
            out.append(r)
        for key, m in self.mb["machines"].items():
            out.append(self._build("core_" + key, m["tier"], m["core"]["in"], m["core"]["id"], f"multiblocks.yaml machines.{key}.core"))
            out.append(self._build("controller_" + key, m["tier"], m["controller"]["in"], "controller_" + key, f"multiblocks.yaml machines.{key}.controller"))
        frames = self.framework["frames"]
        for b in self.mb["builds"]:
            kind = b["builds"]
            if kind == "casing":
                continue  # stock GTM recipe, checked by a GameTest
            if kind == "retrofit_kit":
                r = self._build(b["id"], frames[b["frame"]]["tier"], b["in"], "retrofit_kit_" + b["frame"], "multiblocks.yaml builds")
            else:
                r = self._build(b["id"], b["tier"], b["in"], "module_" + kind, "multiblocks.yaml builds")
            if b.get("circuit") is not None:
                r["circuit"] = b["circuit"]
            out.append(r)
        return out

    def stock_casing_recipes(self):
        """The stock GTM casing recipes as the design records them (not generated; GTM makes them)."""
        out = []
        for b in self.mb["builds"]:
            if b["builds"] == "casing":
                out.append({"id": b["id"], "machine": "assembler", "in": b["in"], "out": {"casing_" + b["frame"]: b.get("out", 1)},
                            "circuit": b.get("circuit"), "eut": b["eut"], "duration": b["duration"], "status": "stock"})
        return out

    def recipe_expectation(self, r):
        e = {
            "type": self.recipe_type(r["machine"]), "design_id": r["id"],
            "items_in": {self.item_id(k): q for k, q in r.get("in", {}).items()},
            "fluids_in": {self.fluid_id(k): q for k, q in r.get("fluid_in", {}).items()},
            "items_out": {self.item_id(k): q for k, q in r["out"].items()},
            "duration": self.ticks(r["duration"]), "eut": r["eut"],
        }
        if r.get("circuit") is not None:
            e["circuit"] = r["circuit"]
        return e


def load():
    d = ROOT / "design"

    def read(name):
        with open(d / name) as f:
            return yaml.safe_load(f)
    return Design(read("slice1-mechanical.yaml"), read("multiblocks.yaml"), read("ids.yaml"))
