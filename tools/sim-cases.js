// Bonus-math test cases for the addon, computed by the simulator core (design/sim/sim-core.js),
// so the addon's math and the simulator can't drift apart. Prints JSON to stdout.
// Called by tools/generate.py; don't edit the output.
const { resolveGroup } = require("../design/sim/sim-core.js");
const data = require("../design/sim/data.json");

const frames = Object.keys(data.framework.frames);
const mods = Object.keys(data.framework.module_definitions);
const maxBuilt = data.framework.structure.max_segments_built;

// module lists: none, each alone, every ordered pair, all three in both orders, and a duplicate
const lists = [[]];
for (const a of mods) lists.push([a]);
for (const a of mods) for (const b of mods) if (a !== b) lists.push([a, b]);
lists.push([...mods], [...mods].reverse(), [mods[0], mods[0]]);

const cases = [];
for (const frame of frames)
  for (let built = 1; built <= maxBuilt + 1; built++)
    for (const modules of lists) {
      const g = resolveGroup(data, { type: "component_assembler", frame, segments: built, modules, count: 1 });
      cases.push({
        frame, built, modules,
        expect: {
          tier: g.tier, built: g.built, counted: g.counted, parallels: g.parallels,
          duration: g.duration, energy: g.energy, lockDuration: g.locked ? g.lockDuration : 1,
          locked: g.locked, installed: g.modules, upkeep: g.upkeep
        }
      });
    }
process.stdout.write(JSON.stringify({ generated: "by tools/generate.py from design/ via design/sim/sim-core.js; do not edit", cases }, null, 0));
