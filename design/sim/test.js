const assert = require("assert");
const { simulate, expand, resolveGroup, checks, buildCost, buildItems } = require("./sim-core.js");
const data = require("./data.json");
const S = { oc: 2, power: 1e9 };
const near = (a, b, msg) => assert(Math.abs(a - b) < 1e-6 * Math.max(1, Math.abs(b)), `${msg}: got ${a}, want ${b}`);

// 1. one HV motor now expands all the way to ingots
let e = expand(data, [{ item: "motor_hv", qty: 1 }]);
assert.strictEqual(e.runs.motor_lv, 6); assert.strictEqual(e.runs.motor_mv, 3);
// magnetic steel rods: 6 (LV motors) + 3 (MV stators) + 1 (HV stator) = 10, each from 1 steel rod
assert.strictEqual(e.runs.rod_magnetic_steel, 10);
// steel rods: 12 (LV motors) + 10 (for magnetic) = 22, one ingot each (harder rods)
assert.strictEqual(e.runs.rod_steel, 22); assert.strictEqual(e.supplied.ingot_steel, 22);
assert.strictEqual(e.byproducts.dust_small_steel, 44);
// copper wire: 24 for LV motors + 12 for 12 copper cables (6 MV motors x2)... LV motors need tin cable
assert.strictEqual(e.runs.wire_copper_single, Math.ceil((24 + 6) / 2));
assert.strictEqual(e.supplied.rubber_fluid, (12 + 6 + 2) * 144); // tin, copper, silver cables

// 2. ULV recipe (copper wire, 7 EU/t) in an LV wiremill: one overclock
let r = simulate(data, [{ type: "wiremill", tier: "LV", count: 1 }], [{ item: "wire_copper_single", qty: 2 }], S);
near(r.total, 3.15 / 2, "ulv oc time"); near(r.energy, 28 * (3.15 / 2) * 20, "ulv oc energy");

// 3. component assembler: aluminium frame caps at 8; 10 segments built counts 8
let G = resolveGroup(data, { type: "component_assembler", frame: "aluminium", segments: 10, modules: [], count: 1 });
assert.strictEqual(G.counted, 8); assert.strictEqual(G.parallels, 8); assert.strictEqual(G.tier, 2);
assert(G.warnings.some(w => /counts 8 of 10/.test(w)), G.warnings.join("|"));

// 4. lock on LV motor at MV frame: 10 s? no, LV motor is 5 s at 30 EU/t -> MV machine 2.5 s, lock 0.5 -> 1.25 s; 8 parallels
r = simulate(data, [{ type: "component_assembler", frame: "aluminium", segments: 8, modules: ["specialization_lock"], lock: "motor_lv", count: 1 }], [{ item: "motor_lv", qty: 8 }],
  { oc: 2, power: 1e9 });
// inputs (rods, cables, wire) have no machine here, so only motor_lv is scheduled
near(r.machineTime, 1.25, "locked CA");

// 5. assembly hall runs cables but not components; component assembler the reverse
r = simulate(data, [{ type: "assembly_hall", frame: "steel", segments: 1, modules: [], count: 1 }], [{ item: "cable_tin_single", qty: 1 }, { item: "motor_lv", qty: 1 }], S);
assert(r.errors.some(x => x.recipe === "motor_lv")); assert(!r.errors.some(x => x.recipe === "cable_tin_single"));

// 6. efficiency coil: EU/t x0.6, duration x1.25 (0.75 energy per craft); steel frame can't take it (MV module)
G = resolveGroup(data, { type: "turning_line", frame: "steel", segments: 2, modules: ["efficiency_coil"], count: 1 });
assert(G.warnings.some(w => /needs at least a MV frame/.test(w)));
r = simulate(data, [{ type: "turning_line", frame: "aluminium", segments: 1, modules: ["efficiency_coil"], count: 1 }], [{ item: "rod_steel", qty: 1 }], S);
// lathe 16 EU/t is LV; MV machine -> 1 OC: duration 5.6*1.25/2, EU/t 16*0.6*4
near(r.total, 5.6 * 1.25 / 2, "eff time"); near(r.energy, 16 * 0.6 * 4 * (5.6 * 1.25 / 2) * 20, "eff energy");

// 7. batch bus doubles parallels with upkeep
G = resolveGroup(data, { type: "component_assembler", frame: "stainless_steel", segments: 16, modules: ["batch_bus"], count: 1 });
assert.strictEqual(G.parallels, 32);

// 8. checks: no conflicts in this slice; pistons and robot arms exceed an LV bus
const c = checks(data);
assert.strictEqual(c.conflicts.length, 0, JSON.stringify(c.conflicts));
const busIds = c.bus.map(b => b.recipe).sort();
assert.deepStrictEqual(busIds, ["piston_hv", "piston_lv", "piston_mv", "robot_arm_hv", "robot_arm_lv", "robot_arm_mv"]);
assert(c.bus.every(b => b.singleBusTier === "MV"));
// a synthetic conflict is detected (its inputs are a subset of copper wire's, circuit 1), and a different circuit resolves it
const d2 = JSON.parse(JSON.stringify(data));
d2.recipes.push({ id: "x1", machine: "wiremill", eut: 7, duration: 1, in: { ingot_copper: 1 }, out: { wire_copper_single: 1 } });
let c2 = checks(d2); assert.strictEqual(c2.conflicts.length, 1); assert.strictEqual(c2.conflicts[0].resolved, false);
d2.recipes[d2.recipes.length - 1].circuit = 2; c2 = checks(d2); assert.strictEqual(c2.conflicts.length, 0);

// one of each module per machine (decision one-module-per-type)
G = resolveGroup(data, { type: "component_assembler", frame: "stainless_steel", segments: 4, modules: ["batch_bus", "batch_bus"], count: 1 });
assert.strictEqual(G.parallels, 8); assert(G.warnings.some(w => /already installed/.test(w)));

// 9. build cost: one 4-segment steel turning line
const need = buildItems(data, [{ type: "turning_line", frame: "steel", segments: 4, modules: [], count: 1 }]);
assert.strictEqual(need.casing_steel, 13 + 32); assert.strictEqual(need.spindle_unit, 4); assert.strictEqual(need.controller_turning_line, 1);
const bc = buildCost(data, [{ type: "turning_line", frame: "steel", segments: 4, modules: [], count: 1 }]);
// cores: 4 x (2 motors + 1 piston) + controller (2 motors + 1 piston) = 15 LV motors
assert.strictEqual(bc.lvMotors, 15);
console.log("all tests passed");
