# Prototype manual test

This is the checklist for playing through the prototype on your own computer (Prism Launcher). Everything here
has also been checked by automated tests where a test can see it (docs/progress.md). This list covers what
needs a person: how things look and feel, the UI, AE2, and how long the goal takes.

For each step: do it, compare with **Expect**, and note anything different. If something crashes or looks
wrong, send me `logs/latest.log` from the instance folder (and a screenshot for UI problems).

## 0. Install and start

1. **Get the pack.** Open the pull request on GitHub, go to Checks, then the latest CI run, then its
   Artifacts. Download **modpack** and unzip it to get `UTechnology.mrpack`. (Alternatively, build it
   yourself: `tools/export-pack.sh addon/build/libs/utechnology-0.1.0.jar` after building the addon.)
2. **Import it in Prism.** Add Instance, then Import, and pick `UTechnology.mrpack`. Give it 6 to 8 GB of memory
   and Java 17.
3. **Create a world.** A **superflat** world with cheats on. Creative mode is easiest for building, but switch to
   Survival (`/gamemode survival`) for sections 5 and 8: in Creative, retrofitting doesn't use up kits (so the
   "not enough kits" check can't be seen) and you can't judge whether the kit's materials are enough.
   `/utechkit` needs cheats (op level 2); it gives a fresh set of kits if you run out.
4. **Expect** on joining: a chat line "UTechnology prototype: your kits are in your inventory", and labelled
   shulker boxes: *Materials* (several boxes), *Machines and hatches*, *AE2 autocrafting*, *Power: creative*,
   *Fluids* and *Power: LV-HV generators*. `/utechkit` gives them again.

## 1. First look

- [ ] The **UTechnology** creative tab lists the stator coils, precision bearing, the four core blocks,
  retrofit kits, modules, coil winders, bearing presses and the four multiblock controllers.
- [ ] JEI: search "stator". The **MV Stator Coil** is made in the **Coil Winder** (1 magnetic steel rod + 4
  cupronickel double wire, 10 s, 30 EU/t).
- [ ] JEI: the **MV Electric Motor** needs 2 LV motors, 1 MV stator coil, 2 aluminium rods and 2 copper cables
  (assembler). There is no crafting-table recipe for MV or HV motors.
- [ ] JEI: **Large Wire Factory**, **Large Assembling Factory** and **Large Cutter** are hidden and have no recipes.
- [ ] Placeholder textures: items use GregTech icons (stator coils look like voltage coils), cores use gearbox
  casing textures. Nothing shows the purple and black "missing texture" pattern. **Note any that do.**

## 2. Power and fluids

- [ ] **Creative power:** place a Creative Energy block next to an LV machine and set it to LV. The machine works.
- [ ] **Real power:** place an LV Combustion Generator, put a diesel bucket in its UI, and connect it to an LV
  machine with tin cable. The machine runs at LV. (MV and HV generators go with copper and gold cable.)
- [ ] **Fluids:** place a Creative Tank and put a **rubber bucket** in its slot to set it to rubber. Pipe it, or
  place it next to a machine's fluid input. Set up lubricant the same way, or use the lubricant buckets.

## 3. The chain with single-block machines

With the LV machines from the kit, make: copper wire (wiremill, programmed circuit 1), tin cable (assembler,
rubber), steel rods (lathe), magnetic steel rod (polarizer), then **LV motors** (assembler).

Build recipes show **10 s** in JEI. A machine one tier higher than the recipe overclocks it (GregTech's normal
rule): for example an LV assembler makes a 7 EU/t build in 5 s at 28 EU/t. That's expected.

- [ ] **Coil winder:** assemble an **LV Coil Winder** (assembler: LV hull, 2 LV motors, 4 copper wire, 2 LV
  circuits). Wind an MV stator coil. **Expect** 10 s at 30 EU/t.
- [ ] **MV coil winder** (MV hull, 2 MV motors, 4 cupronickel double wire, 2 MV circuits): winds the HV stator
  coil (10 s, 120 EU/t). An LV coil winder refuses the HV stator coil (it needs MV power).
- [ ] **Bearing press (MV)**: 2 stainless steel rings + 1 stainless steel rod + 50 mB lubricant gives 2 precision
  bearings in 8 s.
- [ ] The machines' screens and JEI show "Coil Winder" and "Bearing Press" as names (not `utechnology.coil_winder`).

## 4. Build the four multiblocks

Every multiblock has the same shape, seen from the front (3 wide, 3 tall):

| Part | Layout | Blocks |
|---|---|---|
| Front | controller in the middle | the other 8: frame casings or hatches |
| Segment (1 to 32 of them) | the machine's **core** in the middle | 8 frame casings around it |
| End | **frame boxes** in the 4 corners | the other 5: frame casings or hatches |

The **steel** frame uses Solid Steel Machine Casings and Steel Frame Boxes. Every multiblock needs at least
1 maintenance hatch (the kit's are auto-maintenance), 1 energy hatch, 1 input bus and 1 output bus, all in
casing positions of the front or end. Fluid input hatches (up to 2) are optional. JEI's multiblock preview on
each controller shows the shape.

| Machine | Core | Runs |
|---|---|---|
| **Component Assembler** | Assembly Arm Housing | component assembler recipes (motors, pistons, conveyors, robot arms) |
| **Assembly Hall** | Assembly Bench Unit | all other assembler recipes (cables, hulls, casings, our builds) |
| **Wire Drawing Line** | Drawing Die Unit | wiremill recipes |
| **Turning Line** | Spindle Unit | lathe recipes |

Controllers and cores are assembler recipes (see JEI). Each core takes components, for example the Spindle
Unit takes 2 LV motors, 1 LV piston and 1 steel frame box.

- [ ] Build a **Turning Line** with a steel frame and 2 segments. **Expect:** it forms; the controller screen
  says *Frame: Steel (max LV)*, *Segments: 2 counted of 2 built (frame cap 4)*, *Parallels: up to 2*.
- [ ] Give it 4 steel ingots. **Expect** 2 rods per run, each run 5.6 s, at 16 EU/t: **parallels don't add
  power**. Jade on the controller shows the recipe progress.
- [ ] Build the **Wire Drawing Line** (2 segments), the **Assembly Hall** (2 segments) and the **Component
  Assembler** (4 segments) the same way.
- [ ] The **Assembly Hall** refuses motors (component recipes), and the **Component Assembler** refuses cables.
- [ ] Add a **5th segment** to the steel Component Assembler. **Expect:** still formed, *4 counted of 5 built*,
  and a yellow line "Build a better frame to use all segments".
- [ ] Break a casing in a segment. The machine unforms. Put it back and it forms again.
- [ ] Mixing frames (one aluminium casing in a steel machine) stops it forming.
- [ ] **Voltage cap:** put an **HV** energy hatch on a steel machine. **Expect:** it still runs at most LV
  recipes. The HV motor recipe (120 EU/t) won't run until the frame is aluminium or better.

Do this section in **Survival**. The path is steel to aluminium to stainless steel, then back down to
aluminium (a downgrade uses kits of the lower frame). The kit has materials for that path with an 8-segment
Component Assembler.

- [ ] Make **Aluminium Retrofit Kits** in an assembler: 24 aluminium plates + 4 aluminium frame boxes + a
  **programmed circuit 7** (30 EU/t, so it needs the MV assembler). The circuit keeps the kit recipe apart from
  the casing recipe, which uses circuit 6.
- [ ] Right-click the formed steel **Component Assembler** with the kits. **Expect:** a message above the hotbar
  (not in chat) "Used N kits: the frame is now Aluminium". N is one kit per 8 casings and frame boxes, rounded
  up: 7 for the 4 + 1 segments from section 4 (13 + 40 casings + 4 frame boxes = 57). All casings and the 4
  corner frame boxes turn to aluminium (Frostproof casings). You get the old steel casings and frame boxes
  back. Hatches, controller and cores stay.
- [ ] With too few kits nothing changes, and a message says how many are needed. Retrofitting is refused while
  the machine is running a recipe (wait for it to finish), and goes one frame at a time (steel to aluminium to
  stainless steel, and back down the same way).
- [ ] **Expect** after the upgrade: *Frame: Aluminium (max MV)*, segment cap 8, 2 module slots, and all 5
  segments counted. The HV motor recipe now runs.
- [ ] **Stainless Steel Retrofit Kits** (made the same way; 120 EU/t, MV assembler) take it to HV: *Frame:
  Stainless Steel (max HV)*, 16 segments, 3 module slots.
- [ ] **Downgrade:** right-click the stainless steel machine with Aluminium Retrofit Kits. It goes back to
  aluminium and returns the stainless steel blocks. Leave it on aluminium for sections 7 and 8. (Steel
  Retrofit Kits exist too, from steel plates and frames, for going back to steel.)

## 6. Modules

Modules go in the **Modules** tab on the side of the controller's screen (3 slots), or right-click the
controller with a module. Sneak + right-click with an empty hand takes the last module out.

- [ ] **Slots follow the frame:** steel 1, aluminium 2, stainless 3. Right-click install refuses extra
  modules, a second copy of the same module, and modules your frame is too low for (efficiency coil needs MV,
  batch bus needs HV). A module put straight into a slot it can't use shows a red "Not working" line in the
  controller screen.
- [ ] **Specialization Lock** (LV): the machine locks to the next recipe it runs; that recipe runs **2x faster**;
  other recipes are refused ("locked to another recipe"). Removing the lock unlocks it.
- [ ] **Efficiency Coil** (MV): recipes take **25% longer** and draw **0.6x EU/t**, about 25% less energy per
  craft. (GTM rounds EU/t down to whole numbers.)
- [ ] **Batch Bus** (HV): **doubles parallels** and uses **10 mB lubricant per item crafted**. Put lubricant in an
  undyed fluid hatch. Read section 7 first: with a batch bus, inputs must come through **dyed** buses.

## 7. AE2 autocrafting

Set up an ME network from the AE2 kit (controller, drive, cells, the creative energy cell, terminals, cables).
Encode patterns in the pattern encoding terminal (JEI's "+" button transfers a recipe).

How GregTech decides which inputs a recipe may use (GTM 7.5.3):
- An input bus set to **distinct** (the toggle in the bus's screen) is on its own: a recipe uses only that
  bus. It can't see fluid hatches.
- A bus **dyed** with a spray can (the kit has red, blue and green) forms a group with everything else of that
  colour, **plus every undyed hatch**.

So, for the prototype:
- **Item-only recipes** (motors, pistons, robot arms, rods, wires): one pattern provider per input bus, in
  **non-blocking mode**, facing a **distinct** bus.
- **Recipes with fluids** (conveyors, cables): the provider faces a **dyed** bus, and the fluid (rubber) sits in
  an **undyed** fluid hatch kept full from a creative tank. Encode those patterns with the item inputs only.
- Recipes that could steal each other's inputs must go to different buses. `docs/validation-report.md`
  ("Feeding the multiblocks with AE2") lists which recipes can share a bus.

Products come back into AE2 the usual GregTech way: turn on the **output bus's auto-output** (screwdriver or
its screen) facing an ME interface or a pattern provider on the network.

Checks:
- [ ] **Fill the parallels:** an aluminium Component Assembler with 8 segments, fed LV motor patterns
  by a non-blocking provider into a distinct **MV** input bus (the LV motor needs 4 slots; an MV bus has
  9). Request 64 LV motors. **Expect:** runs at **8 parallels** each (the screen shows "Parallels: up to 8";
  JEI/Jade show the running recipe), with no stuck items and no wrong crafts.
- [ ] Add segments while it is working. **Expect:** the next run uses the new parallel count with no
  re-encoding.
- [ ] **Conveyors** through a dyed bus, with rubber in an undyed hatch, run correctly next to motors in a
  distinct bus on the same machine.
- [ ] Note: pistons and robot arms have 5 item inputs, so they don't fit one LV input bus (4 slots). Use an
  MV bus.

## 8. The goal: 4 each of HV robot arms, HV conveyors, HV pistons

Make **4 HV robot arms, 4 HV conveyors and 4 HV pistons** from the kit's ingots, using what you built. Use AE2
autocrafting if you can. Time it, from requesting the goal to having everything (not counting the time
spent building machines).

- [ ] The kit has enough materials for the goal plus the starter factory, with 50% spare.
- [ ] **Compare with the simulator** (`docs/prototype-estimate.md`). With single-block machines only, it
  estimates about **33 minutes**. With the multiblocks (aluminium Component Assembler with 4 segments, no
  modules; steel Assembly Hall, Wire Drawing Line and Turning Line with 2 each) it estimates about **17
  minutes**. Both use the real generators (1408 EU/t), and the kit's diesel covers either run with 50% spare
  (build the factory on creative power). Your Component Assembler probably has more segments by now, which
  should be faster. The simulator assumes AE2 keeps every machine fed, so set-up and hand-feeding make the real
  time longer. **Please note your time, your machines (frames, segments, modules), and what slowed you down.**

## 9. Saving

- [ ] Quit to the title screen and reopen the world. **Expect:** multiblocks still formed with the same frame,
  segments, modules and lock; running recipes resume.

## What to send back

- Your times for section 8, and anything that differed from **Expect**.
- `logs/latest.log` after a crash or anything odd.
- Opinions: does upgrading feel worth it, are kits too cheap or dear, is the batch bus's AE2 setup too fiddly?
