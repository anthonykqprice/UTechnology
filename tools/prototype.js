// Prototype test-world numbers from the simulator: what the goal and the starter factory cost in supplied items,
// how much energy the goal takes, and how long it should take in a reference factory. Prints JSON.
// Called by tools/generate.py with design/prototype.yaml (as JSON) as argv[2].
const { simulate, expand, buildCost } = require("../design/sim/sim-core.js");
const data = require("../design/sim/data.json");
const proto = JSON.parse(process.argv[2]);
const slice = JSON.parse(process.argv[3] || "{}"); // slice builds: [{id, in}]

const goals = Object.entries(proto.goal).map(([item, qty]) => ({ item, qty }));

// what the goal costs in supplied items
const goalCost = expand(data, goals).supplied;

// what building the factory and the extra builds costs
const fb = buildCost(data, proto.factory);
const buildRecipes = [];
for (const b of data.framework_builds || []) {
  const id = b.builds === "casing" ? "casing_" + b.frame : b.builds === "retrofit_kit" ? "retrofit_kit_" + b.frame : "module_" + b.builds;
  buildRecipes.push({ id: "build_" + b.id, in: b.in, out: { [id]: b.out || 1 } });
}
for (const b of slice) buildRecipes.push({ id: "build_" + b.id, in: b.in, out: { [b.id]: 1 } });
const extra = expand(data, Object.entries(proto.extra_builds || {}).map(([item, qty]) => ({ item, qty })), buildRecipes.concat(data.recipes)).supplied;

const supplied = {};
for (const part of [goalCost, fb.supplied, extra]) for (const [k, v] of Object.entries(part)) supplied[k] = (supplied[k] || 0) + v;

// the goal in two reference factories, with the real-power kit's generators (4 LV, 2 MV, 2 HV combustion)
const power = 4 * 32 + 2 * 128 + 2 * 512;
const singles = [
  { type: "assembler", tier: "LV", count: 2 }, { type: "assembler", tier: "MV", count: 1 },
  { type: "wiremill", tier: "LV", count: 2 }, { type: "lathe", tier: "LV", count: 2 }, { type: "polarizer", tier: "LV", count: 2 },
  { type: "coil_winder", tier: "LV", count: 1 }, { type: "coil_winder", tier: "MV", count: 1 }, { type: "bearing_press", tier: "MV", count: 1 }
];
const upgraded = singles.concat([
  { type: "component_assembler", frame: "aluminium", segments: 4, modules: [], count: 1 },
  { type: "assembly_hall", frame: "steel", segments: 2, modules: [], count: 1 },
  { type: "wire_drawing_line", frame: "steel", segments: 2, modules: [], count: 1 },
  { type: "turning_line", frame: "steel", segments: 2, modules: [], count: 1 }
]);
const run = f => {
  const r = simulate(data, f, goals, { oc: 2, power });
  return { total_s: r.total, machine_s: r.machineTime, chain_s: r.chainTime, power_s: r.powerTime, limit: r.limit,
           energy_eu: r.energy, errors: r.errors.map(e => e.text) };
};
process.stdout.write(JSON.stringify({
  supplied, goal_supplied: goalCost, power_eut: power,
  estimate: { single_blocks: run(singles), with_multiblocks: run(upgraded) },
  factories: { single_blocks: singles, with_multiblocks: upgraded }
}));
