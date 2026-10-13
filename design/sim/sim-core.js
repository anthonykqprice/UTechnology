/* Component factory simulator core, v2. Pure functions over the merged design data
   (slice recipe file + multiblocks.yaml). Used by the simulator page (inlined) and node tests. */
const SIM_TIERS = ["ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV"];
const SIM_V = [8, 32, 128, 512, 2048, 8192, 32768, 131072, 524288, 2097152];

const tierIndex = name => SIM_TIERS.indexOf(name);
// lowest tier whose voltage covers the recipe's EU/t
const recipeTier = eut => { let t = 0; while (t < SIM_V.length - 1 && SIM_V[t] < eut) t++; return t; };
// GTM item buses hold (1 + tier)^2 slots (ULV 1, LV 4, MV 9, HV 16)
const busSlots = tier => (1 + Math.min(9, tier)) ** 2;

/* Resolve one machine group into its effective stats. */
function resolveGroup(data, g) {
  const m = data.machines[g.type];
  const warnings = [];
  if (!m) return { ok: false, warnings: [`Unknown machine type ${g.type}`] };
  const count = Math.max(0, Math.floor(g.count || 0));
  if (m.kind !== "multiblock") {
    const tiers = (m.tiers || []).map(tierIndex);
    let tier = tierIndex(g.tier);
    if (!tiers.includes(tier)) { warnings.push(`${g.type} has no ${g.tier} version; using ${SIM_TIERS[tiers[0]]}`); tier = tiers[0]; }
    return { ok: true, multiblock: false, type: g.type, tier, count, parallels: 1, duration: 1, energy: 1, lock: null, locked: false, upkeep: {}, warnings };
  }
  const fw = data.framework;
  const frames = fw.frames;
  const frameKey = frames[g.frame] ? g.frame : Object.keys(frames)[0];
  if (frameKey !== g.frame) warnings.push(`Unknown frame ${g.frame}; using ${frameKey}`);
  const f = frames[frameKey];
  const frameTier = tierIndex(f.tier);
  const tier = tierIndex(f.max_voltage);              // frame caps voltage, whatever the hatches
  const maxBuilt = fw.structure.max_segments_built || 32;
  let built = Math.max(1, Math.floor(g.segments || 1));
  if (built > maxBuilt) { warnings.push(`The structure holds at most ${maxBuilt} segments`); built = maxBuilt; }
  const counted = Math.min(built, f.segment_cap);
  if (built > f.segment_cap) warnings.push(`${title(frameKey)} frame counts ${f.segment_cap} of ${built} segments; a better frame would use them all`);
  let parallels = counted, duration = 1, energy = 1, lockDuration = 1;
  const upkeep = {};
  const defs = fw.module_definitions;
  const used = [];
  for (const k of (g.modules || [])) {
    const mod = defs[k];
    if (!mod) continue;
    if (tierIndex(mod.unlocked_at) > frameTier) { warnings.push(`${title(k)} needs at least a ${mod.unlocked_at} frame`); continue; }
    if (used.includes(k)) { warnings.push(`${title(k)} is already installed; one of each module per machine`); continue; }
    if (used.length >= f.module_slots) { warnings.push(`${title(frameKey)} frame has ${f.module_slots} module slot${f.module_slots > 1 ? "s" : ""}; ${title(k)} ignored`); continue; }
    used.push(k);
    if (k === "specialization_lock") { lockDuration = mod.duration || 0.5; continue; }
    if (mod.duration) duration *= mod.duration;
    if (mod.energy) energy *= mod.energy;
    if (mod.parallels) parallels *= mod.parallels;
    if (mod.upkeep) for (const u in mod.upkeep) upkeep[u] = (upkeep[u] || 0) + mod.upkeep[u];
  }
  const locked = used.includes("specialization_lock");
  const lock = locked ? (g.lock || null) : null;
  if (locked && !lock) warnings.push("Specialization lock has no recipe chosen, so this machine runs nothing");
  return { ok: true, multiblock: true, type: g.type, tier, frame: frameKey, built, counted, count, parallels, duration, energy, lockDuration, lock, locked, upkeep, modules: used, warnings };
}
const title = s => String(s).replace(/_/g, " ").replace(/\b\w/g, c => c.toUpperCase());

/* Does this machine type run this recipe at all (ignoring voltage and locks)? */
function machineRuns(data, type, r) {
  const m = data.machines[type];
  if (!m) return false;
  if (m.kind !== "multiblock") return type === r.machine;
  if (!(m.runs || []).includes(r.machine)) return false;
  if (m.filter === "component") return !!r.component;
  if (m.filter === "not_component") return !r.component;
  return true;
}

/* Can group G run recipe r? Returns per-run stats or null. */
function capability(data, G, r, oc) {
  if (!G.ok || G.count < 1) return null;
  if (!machineRuns(data, G.type, r)) return null;
  const rt = recipeTier(r.eut);
  if (G.tier < rt) return null;
  let dmul = G.duration;
  if (G.locked) { if (G.lock !== r.id) return null; dmul *= G.lockDuration; }
  // As GTM 7.5.3 computes it: module multipliers first, each result truncated to whole ticks and EU/t
  // (ContentModifier), then overclocking judged on the modified EU/t (OverclockingLogic): ULV recipes get one
  // overclock fewer, and overclocking stops before a recipe would take under 1 tick.
  const ticks0 = Math.round(r.duration * 20);
  const ticksMod = dmul === 1 ? ticks0 : Math.max(1, Math.floor(ticks0 * dmul));   // GTM: at least 1 tick
  const eutMod = G.energy === 1 ? r.eut : Math.floor(r.eut * G.energy);
  const rtMod = recipeTier(eutMod);
  let n = Math.max(0, G.tier - rtMod - (rtMod === 0 ? 1 : 0));
  while (n > 0 && ticksMod / Math.pow(oc, n) < 1) n--;
  const dur = Math.floor(ticksMod / Math.pow(oc, n)) / 20;   // seconds per run
  const eut = eutMod * Math.pow(4, n);                        // EU/t while running (parallels add none)
  const energyPerRun = eut * dur * 20 / G.parallels;  // EU per run: parallels share one recipe's power (decision free-parallels)
  const perRun = dur / G.parallels;                    // machine-seconds per run with parallels
  return { overclocks: n, dur, eut, energyPerRun, runTime: dur, perRun };
}

function outputsIndex(recipes) {
  const byOut = {};
  for (const r of recipes) { const main = Object.keys(r.out)[0]; if (!byOut[main]) byOut[main] = r; }
  for (const r of recipes) for (const o in r.out) if (!byOut[o]) byOut[o] = r;
  return byOut;
}

/* Expand goals into recipe runs, supplied totals and byproducts. */
function expand(data, goals, recipes) {
  recipes = recipes || data.recipes;
  const byOut = outputsIndex(recipes);
  const order = [], seen = new Set();
  const visit = item => {
    if (seen.has(item)) return; seen.add(item);
    const r = byOut[item];
    if (r) for (const k of Object.keys(r.in || {}).concat(Object.keys(r.fluid_in || {}))) visit(k);
    order.push(item);
  };
  goals.forEach(g => visit(g.item));
  order.reverse(); // consumers before their inputs
  const demand = {};
  goals.forEach(g => { demand[g.item] = (demand[g.item] || 0) + Math.max(0, g.qty || 0); });
  const runs = {}, supplied = {}, made = {}, byproducts = {};
  for (const item of order) {
    const need = demand[item] || 0;
    if (!need) continue;
    const r = byOut[item];
    if (!r) { supplied[item] = (supplied[item] || 0) + need; continue; }
    const k = Math.ceil(need / r.out[item] - 1e-9);
    runs[r.id] = (runs[r.id] || 0) + k;
    for (const [o, q] of Object.entries(r.out)) {
      made[o] = (made[o] || 0) + k * q;
      if (o !== item) byproducts[o] = (byproducts[o] || 0) + k * q;
    }
    for (const [i, q] of Object.entries(r.in || {})) demand[i] = (demand[i] || 0) + k * q;
    for (const [i, q] of Object.entries(r.fluid_in || {})) demand[i] = (demand[i] || 0) + k * q;
  }
  return { runs, supplied, made, byproducts, byOut };
}

/* Materials for a set of supplied items. */
function materials(data, supplied) {
  const raw = {}, fluids = {}, other = {};
  for (const [item, q] of Object.entries(supplied)) {
    const it = data.items[item] || {};
    if (it.fluid) fluids[item] = (fluids[item] || 0) + q;
    else if (it.raw) for (const [mat, a] of Object.entries(it.raw)) raw[mat] = (raw[mat] || 0) + a * q;
    else other[item] = (other[item] || 0) + q;
  }
  return { raw, fluids, other };
}

/* Items needed to build the factory's multiblocks (hatches not counted). */
function buildItems(data, factory) {
  const need = {};
  const add = (id, q) => { if (q > 0) need[id] = (need[id] || 0) + q; };
  const fw = data.framework, mbs = data.multiblocks || {};
  for (const g of factory) {
    const m = data.machines[g.type];
    if (!m || m.kind !== "multiblock") continue;
    const G = resolveGroup(data, g), spec = mbs[g.type];
    if (!G.ok || !spec || G.count < 1) continue;
    const f = fw.frames[G.frame];
    const casingsPerSeg = (fw.segments && fw.segments.casings_per_segment) || 8;
    // front aisle 8 positions and end aisle 5 edge positions (some become hatches; counted as casings),
    // plus 4 frame boxes in the end aisle's corners, plus 8 casings per segment
    const casings = 13 + G.built * casingsPerSeg;
    add("casing_" + G.frame, casings * G.count);
    add("frame_" + G.frame, 4 * G.count);
    add(spec.core.id, G.built * G.count);
    add("controller_" + g.type, G.count);
    for (const k of G.modules) add("module_" + k, G.count);
  }
  return need;
}

/* Recipes for building: casings, cores, controllers, modules (from multiblocks.yaml). */
function buildRecipes(data) {
  const out = [];
  for (const b of (data.framework_builds || [])) {
    const id = b.builds === "casing" ? "casing_" + b.frame : b.builds === "retrofit_kit" ? "retrofit_kit_" + b.frame : "module_" + b.builds;
    out.push({ id: "build_" + b.id, in: b.in, out: { [id]: b.out || 1 } });
  }
  for (const [k, spec] of Object.entries(data.multiblocks || {})) {
    out.push({ id: "build_core_" + k, in: spec.core.in, out: { [spec.core.id]: 1 } });
    out.push({ id: "build_controller_" + k, in: spec.controller.in, out: { ["controller_" + k]: 1 } });
  }
  return out;
}

function buildCost(data, factory) {
  const need = buildItems(data, factory);
  const goals = Object.entries(need).map(([item, qty]) => ({ item, qty }));
  const recipes = buildRecipes(data).concat(data.recipes);
  const e = expand(data, goals, recipes);
  return { need, lvMotors: e.made.motor_lv || 0, ...materials(data, e.supplied), supplied: e.supplied };
}

/* Design checks: recipe conflicts within a machine type, and bus sizes. */
function checks(data) {
  const conflicts = [], bus = [];
  const byMachine = {};
  for (const r of data.recipes) (byMachine[r.machine] = byMachine[r.machine] || []).push(r);
  // A conflict: every input of one recipe (items, fluids, programmed circuit) is also an input of another,
  // so an input bus loaded for the larger recipe can run the smaller one. Only a different circuit fixes it.
  // Same rule as tools/validate.py.
  const keys = r => new Set(Object.keys(r.in || {}).concat(Object.keys(r.fluid_in || {}).map(k => "fluid:" + k), r.circuit != null ? ["circuit:" + r.circuit] : []));
  const subset = (a, b) => [...a].every(k => b.has(k));
  for (const [mach, list] of Object.entries(byMachine)) {
    for (let i = 0; i < list.length; i++) for (let j = i + 1; j < list.length; j++) {
      const a = keys(list[i]), b = keys(list[j]);
      if (subset(a, b) || subset(b, a)) conflicts.push({ machine: mach, recipes: [list[i].id, list[j].id], resolved: false, how: "give them different programmed circuits" });
    }
  }
  // lowest tier at which some machine type can run the recipe (no machine exists at ULV)
  const lowestMachineTier = r => {
    let best = Infinity;
    for (const [k, m] of Object.entries(data.machines)) {
      if (!machineRuns(data, k, r)) continue;
      const tiers = m.kind === "multiblock" ? Object.values(data.framework.frames).map(f => tierIndex(f.max_voltage)) : (m.tiers || []).map(tierIndex);
      for (const t of tiers) if (t >= recipeTier(r.eut)) best = Math.min(best, t);
    }
    return isFinite(best) ? best : recipeTier(r.eut);
  };
  for (const r of data.recipes) {
    // GTM buses have a separate slot for the programmed circuit, so only items count
    const n = Object.keys(r.in || {}).length;
    const t = lowestMachineTier(r);
    if (n > busSlots(t)) {
      let need = t; while (busSlots(need) < n) need++;
      bus.push({ recipe: r.id, inputs: n, tier: SIM_TIERS[t], slots: busSlots(t), singleBusTier: SIM_TIERS[need] });
    }
  }
  return { conflicts, bus };
}

/* Full simulation. settings: {oc: 2|4, power: EU/t available}. */
function simulate(data, factory, goals, settings) {
  const oc = settings.oc === 4 ? 4 : 2;
  const groups = factory.map(g => resolveGroup(data, g));
  const { runs, supplied, made, byproducts, byOut } = expand(data, goals);
  const recipes = Object.fromEntries(data.recipes.map(r => [r.id, r]));
  const errors = [];
  const caps = {};
  for (const id in runs) {
    const r = recipes[id];
    caps[id] = groups.map((G, gi) => ({ gi, cap: capability(data, G, r, oc) })).filter(x => x.cap);
    if (!caps[id].length) {
      const t = SIM_TIERS[recipeTier(r.eut)];
      const who = Object.keys(data.machines).filter(k => machineRuns(data, k, r)).map(title).join(" or ");
      errors.push({ recipe: id, text: `Nothing in the factory can run ${title(id)}. It needs a ${who} at ${t} or above.` });
    }
  }

  // list scheduling of divisible work: big chunks first, each to the group that finishes it earliest
  const load = groups.map(() => 0);
  const assign = {};
  const chunks = [];
  for (const id in runs) {
    if (!caps[id].length) continue;
    const total = runs[id], size = Math.max(1, Math.ceil(total / 200));
    for (let done = 0; done < total; done += size) chunks.push({ id, n: Math.min(size, total - done) });
  }
  const best = id => Math.min(...caps[id].map(x => x.cap.perRun / groups[x.gi].count));
  chunks.sort((a, b) => b.n * best(b.id) - a.n * best(a.id));
  for (const c of chunks) {
    let pick = null, finish = Infinity;
    for (const { gi, cap } of caps[c.id]) {
      const f = load[gi] + c.n * cap.perRun / groups[gi].count;
      if (f < finish - 1e-12) { finish = f; pick = gi; }
    }
    load[pick] = finish;
    (assign[c.id] = assign[c.id] || {})[pick] = ((assign[c.id] || {})[pick] || 0) + c.n;
  }

  let energy = 0;
  const upkeep = {};
  const groupStats = groups.map((G, gi) => ({ gi, load: load[gi], energy: 0, runs: {}, peakEut: 0 }));
  for (const id in assign) for (const gi in assign[id]) {
    const n = assign[id][gi], cap = caps[id].find(x => x.gi === +gi).cap, G = groups[gi];
    energy += n * cap.energyPerRun;
    groupStats[gi].energy += n * cap.energyPerRun;
    groupStats[gi].runs[id] = n;
    groupStats[gi].peakEut = Math.max(groupStats[gi].peakEut, cap.eut * G.count); // parallels draw no extra power
    for (const u in G.upkeep) upkeep[u] = (upkeep[u] || 0) + G.upkeep[u] * n;
  }

  const chainMemo = {};
  const chain = item => {
    if (item in chainMemo) return chainMemo[item];
    const r = byOut[item];
    if (!r || !caps[r.id] || !caps[r.id].length) return (chainMemo[item] = 0);
    const step = Math.min(...caps[r.id].map(x => x.cap.runTime));
    let sub = 0;
    for (const k of Object.keys(r.in || {})) sub = Math.max(sub, chain(k));
    return (chainMemo[item] = step + sub);
  };
  const chainTime = Math.max(0, ...goals.map(g => chain(g.item)));

  const machineTime = Math.max(0, ...load);
  const powerTime = settings.power > 0 ? energy / (settings.power * 20) : Infinity;
  let total = machineTime, limit = "machines";
  if (chainTime > total) { total = chainTime; limit = "chain"; }
  if (powerTime > total) { total = powerTime; limit = "power"; }
  let bottleneck = null;
  if (limit === "machines") { let mx = -1; load.forEach((l, gi) => { if (l > mx) { mx = l; bottleneck = gi; } }); }

  const mats = materials(data, supplied);
  for (const u in upkeep) mats.fluids[u] = (mats.fluids[u] || 0) + upkeep[u];

  return {
    groups, groupStats, runs, assign, caps, made, supplied, byproducts, errors,
    raw: mats.raw, fluids: mats.fluids, other: mats.other,
    energy, machineTime, chainTime, powerTime, total, limit, bottleneck,
    avgDraw: total > 0 ? energy / (total * 20) : 0,
    peakDraw: groupStats.reduce((a, s) => a + s.peakEut, 0)
  };
}

if (typeof module !== "undefined") module.exports = { simulate, expand, resolveGroup, capability, recipeTier, busSlots, checks, buildCost, buildItems, machineRuns, SIM_TIERS };
