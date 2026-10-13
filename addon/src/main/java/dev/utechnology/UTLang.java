package dev.utechnology;

import static dev.utechnology.UTechnology.REGISTRATE;

/** Text shown by the upgradeable multiblocks (controller screen, chat messages, pattern errors). */
public final class UTLang {

    public static void init() {
        lang("utechnology.machine.tooltip.upgradeable", "Upgradeable: frame (retrofit kits), segments (build them), modules (controller slots).");
        lang("utechnology.structure.mixed_frames", "All casings and frame boxes must be the same frame material");
        lang("utechnology.recipe.filtered", "This machine doesn't run this kind of recipe");
        lang("utechnology.recipe.no_data", "Upgrade data not loaded");
        lang("utechnology.recipe.locked", "Specialization lock: locked to another recipe");
        lang("utechnology.machine.not_formed", "Form the structure first");
        lang("utechnology.module.not_a_module", "That's not a module");
        lang("utechnology.module.needs_frame", "This module needs at least a %s frame");
        lang("utechnology.module.already_installed", "One of each module per machine");
        lang("utechnology.module.no_slot", "No free module slot (this frame has %s)");
        lang("utechnology.module.installed", "Installed %s");
        lang("utechnology.module.removed", "Removed %s");
        lang("utechnology.retrofit.same_frame", "The machine already has this frame");
        lang("utechnology.retrofit.not_enough_kits", "Retrofitting this machine takes %s kits");
        lang("utechnology.retrofit.done", "Used %s kits: the machine now has a %s frame");
        lang("utechnology.display.frame", "Frame: %s (max %s)");
        lang("utechnology.display.segments", "Segments: %s counted of %s built (frame cap %s)");
        lang("utechnology.display.over_cap", "Build a better frame to use all segments");
        lang("utechnology.display.parallels", "Parallels: up to %s");
        lang("utechnology.display.modules", "Modules: %s");
        lang("utechnology.display.ignored", "Not working: %s");
        lang("utechnology.display.locked", "Locked to: %s");
        lang("utechnology.gui.modules", "Modules");
    }

    private static void lang(String key, String value) {
        REGISTRATE.addRawLang(key, value);
    }

    private UTLang() {}
}
