// Bonus-math test cases for the addon, computed by the simulator core (design/sim/sim-core.js),
// so the addon's math and the simulator can't drift apart. Prints JSON to stdout.
// Called by tools/generate.py; don't edit the output.
const { resolveGroup, capability } = require("../design/sim/sim-core.js");
const data = require("../design/sim/data.json");
const ids = JSON.parse(process.argv[2] || "{}"); // design name -> game id, for upkeep fluids

const frames = Object.keys(data.framework.frames);
const mods = Object.keys(data.framework.module_definitions);
const maxBuilt = data.framework.structure.max_segments_built;

// module lists: none, each alone, ordered pairs, all three in both orders, duplicates (refused),
// and four entries (more than any frame's slots)
const lists = [[]];
for (const a of mods) lists.push([a]);
for (const a of mods) for (const b of mods) if (a !== b) lists.push([a, b]);
lists.push([...mods], [...mods].reverse());
for (const a of mods) lists.push([a, a]);
lists.push([...mods, mods[0]], [mods[2], mods[2], mods[1], mods[0]]);

const fluid = k => (ids[k] || k).replace(/^fluid:/, "");
const stats = g => ({
  tier: g.tier, built: g.built, counted: g.counted, parallels: g.parallels,
  duration: g.duration, energy: g.energy, lockDuration: g.locked ? g.lockDuration : 1,
  locked: g.locked, installed: g.modules,
  upkeepPerParallel: Object.entries(g.upkeep).map(([k, v]) => ({ fluid: fluid(k), amount: v }))
});

const groupCases = [];
for (const frame of frames)
  for (let built = 1; built <= maxBuilt; built++)
    for (const modules of lists) {
      const g = resolveGroup(data, { type: "component_assembler", frame, segments: built, modules, count: 1 });
      groupCases.push({ frame, built, modules, expect: stats(g) });
    }

// per-recipe cases: the modifier order (parallels, then modules, then overclocking) on real recipes
const sample = ["wire_copper_single", "rod_steel", "motor_lv", "motor_hv", "conveyor_lv"].map(id => data.recipes.find(r => r.id === id));
const recipeCases = [];
for (const frame of frames)
  for (const modules of [[], ["efficiency_coil"], ["specialization_lock"], ["batch_bus"], ["efficiency_coil", "batch_bus"]])
    for (const r of sample) {
      const g = resolveGroup(data, { type: "component_assembler", frame, segments: 3, modules, lock: r.id, count: 1 });
      const c = capability(data, Object.assign({}, g, { type: r.machine, multiblock: true }), r, 2);
      recipeCases.push({
        frame, segments: 3, modules, recipe: r.id, recipe_eut: r.eut, recipe_ticks: Math.round(r.duration * 20),
        expect: c ? { overclocks: c.overclocks, ticks: c.dur * 20, eut: c.eut, parallels: g.parallels } : null
      });
    }

const lines = ['{"generated": "by tools/generate.py from design/ via design/sim/sim-core.js; do not edit",', ' "cases": ['];
groupCases.forEach((c, i) => lines.push("  " + JSON.stringify(c) + (i < groupCases.length - 1 ? "," : "")));
lines.push(" ],", ' "recipe_cases": [');
recipeCases.forEach((c, i) => lines.push("  " + JSON.stringify(c) + (i < recipeCases.length - 1 ? "," : "")));
lines.push(" ]", "}");
process.stdout.write(lines.join("\n"));
