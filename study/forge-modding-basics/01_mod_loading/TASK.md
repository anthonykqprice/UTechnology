# Task 01: How Forge Finds and Starts a Mod

## Metadata
- Generated: 2026-10-18
- Difficulty: Introductory
- Prerequisite Task: None
- Related LEARNING_PLAN Item: Phase 01 / 01. Mod metadata, the main mod class and the two event buses

## Objective

Understand the path from "a jar in the mods folder" to "the mod's code is running": how Forge
discovers a mod, which class it starts, and how the mod asks to be told about things that happen
later. You will learn this by reading the spike mod in this repo, not by writing code.

## Background & Concept Explanation

**Why a loader exists.** Minecraft itself has no idea mods exist. Forge sits between the game and
the mods: it scans the mods folder, reads each mod's description, checks that everything it needs
is present, and then starts each mod in a sensible order. Without a loader, every mod would have to
patch the game on its own and they would trample each other.

**Metadata before code.** Forge reads a plain text file inside each jar before it runs any of the
mod's code. That file says who the mod is (its ID, name, version) and what it depends on. This lets
Forge refuse to start with a clear error ("mod X needs mod Y version Z") instead of crashing halfway
through. It also lets Forge decide load order. The file uses TOML, a simple config format.

**Build-time placeholders.** Some values in that metadata file are not written literally. The build
tool (Gradle) fills them in when it packages the jar, so a value like the mod's version lives in
one place and is copied wherever it is needed.

**The entry point.** Once Forge trusts the metadata, it needs to know which Java class to start.
Java has *annotations*: tags written with an `@` above a class or method that carry extra
information. Forge looks for a specific annotation and creates one object of the tagged class.
Creating it runs that class's *constructor*, the special method that runs when an object is made.
That constructor is where a mod sets itself up.

**Some Java you will meet.** A *package* (the `package ...;` line) is like a folder-based namespace.
`import` lines bring names from other packages into scope. `static` means a value belongs to the
class itself rather than to one object, so there is exactly one of it. `final` means it cannot be
reassigned. The `(SomeEvent e) -> ...` syntax is a *lambda*: a small unnamed function passed as a
value, like an arrow function or closure in other languages. `this::methodName` is a *method
reference*, a shorter way to pass an existing method as a function.

**Events and why there are two buses.** A mod cannot know in advance when the server starts, when
a player joins, or when it is time to register blocks. Instead it *subscribes*: "call this function
when that happens." An *event bus* is the dispatcher that delivers those calls. Forge keeps two of
them. One carries events about the mod's own setup during loading (one per mod). The other carries
events about the running game, shared by every mod. Knowing which bus an event is on matters,
because listening on the wrong one means your code silently never runs.

## Tasks

1. Open `spike/src/main/resources/META-INF/mods.toml`. Identify what each section describes, and
   what the `[[dependencies...]]` entries tell Forge to do. Answer Q1 and Q2 in `REPORT.md`.
2. The file contains values like `${mod_id}`. Find where in `spike/` those values are actually
   defined and how they reach the file. Answer Q3.
3. Open `spike/src/main/java/dev/utechnology/spike/UTSpike.java`. Find the line that tells Forge
   "this is the class to start," and explain how the mod ID in that line is connected to the one in
   `mods.toml`. Answer Q4.
4. In the constructor, find every place the mod subscribes to an event. For each one, say which bus
   it uses and what it wants to be told about. Answer Q5 and Q6.
5. Open `spike/src/main/java/dev/utechnology/spike/SpikeGTAddon.java`. It is never mentioned in
   `UTSpike.java`, yet it runs. Form a hypothesis for how it gets found. Answer Q7.

## Completion Criteria

- [ ] All seven questions in `REPORT.md` have an answer in your own words.
- [ ] Q3 names the specific file(s) and the build step that fill in the `${...}` values.
- [ ] Q5 lists every event subscription in the `UTSpike` constructor with the correct bus for each.
- [ ] Q6 explains, with one example from the spike, what would go wrong if a listener were put on
      the other bus.
- [ ] You can explain the whole load path (jar → metadata → entry class → listeners) out loud in
      under a minute without looking at your notes.

## Hint Log

<!-- Claude appends here when providing hints -->
<!-- Format: - YYYY-MM-DD: "{Summary of the question asked as a hint}" -->

## Notes

<!-- For the user to record freely -->
<!-- Points where stuck, insights, questions, etc. -->
