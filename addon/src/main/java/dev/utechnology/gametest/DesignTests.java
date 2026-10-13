package dev.utechnology.gametest;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import dev.utechnology.UTechnology;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * The live game matches design/: IDs exist, stock recipes still match GTM 7.5.3, replaced recipes are gone,
 * our recipes loaded from the generated KubeJS scripts as designed, and they don't conflict with any other
 * recipe of the same type. Expectations come from tools/generate.py.
 */
@GameTestHolder(UTechnology.MOD_ID)
@PrefixGameTestTemplate(false)
public class DesignTests {

    /**
     * Our content that later prototype phases add. These exact IDs (and recipes using them) are skipped until
     * then. Empty once every phase is built.
     */
    static final List<String> NOT_YET_BUILT = List.of();

    static boolean pending(String id) {
        String bare = id.startsWith("#") ? id.substring(1) : id.startsWith("fluid:") ? id.substring(6) : id;
        return NOT_YET_BUILT.stream().anyMatch(p -> id.equals(p) || bare.equals(p));
    }

    static boolean pending(JsonObject recipe) {
        if (pending("recipe_type:" + recipe.get("type").getAsString())) return true;
        for (String k : new String[] { "items_in", "fluids_in", "items_out" })
            for (String id : recipe.getAsJsonObject(k).keySet()) if (pending(id)) return true;
        return false;
    }

    static JsonObject expectations() {
        try (var in = DesignTests.class.getResourceAsStream("/data/utechnology/gametest/expectations.json")) {
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("can't read expectations.json", e);
        }
    }

    static void report(GameTestHelper h, String what, List<String> problems) {
        if (problems.isEmpty()) {
            h.succeed();
            return;
        }
        problems.forEach(p -> UTechnology.LOGGER.error("{}: {}", what, p));
        h.fail(what + ": " + problems.size() + " problem(s), first: " + problems.get(0));
    }

    static boolean exists(String id) {
        if (id.startsWith("recipe_type:")) return GTRegistries.RECIPE_TYPES.containKey(new ResourceLocation(id.substring(12)));
        if (id.startsWith("fluid:")) return ForgeRegistries.FLUIDS.containsKey(new ResourceLocation(id.substring(6)));
        if (id.startsWith("#")) {
            var tag = TagKey.create(Registries.ITEM, new ResourceLocation(id.substring(1)));
            return BuiltInRegistries.ITEM.getTag(tag).map(s -> s.size() > 0).orElse(false);
        }
        var rl = new ResourceLocation(id);
        return ForgeRegistries.ITEMS.containsKey(rl) || ForgeRegistries.BLOCKS.containsKey(rl);
    }

    @GameTest(template = "empty")
    public static void idsExist(GameTestHelper h) {
        List<String> missing = new ArrayList<>();
        for (JsonElement e : expectations().getAsJsonArray("ids"))
            if (!pending(e.getAsString()) && !exists(e.getAsString())) missing.add(e.getAsString() + " does not exist");
        report(h, "ids", missing);
    }

    static GTRecipeType type(String id) {
        return GTRegistries.RECIPE_TYPES.get(new ResourceLocation(id));
    }

    static List<GTRecipe> recipes(GameTestHelper h, String type) {
        return h.getLevel().getRecipeManager().getAllRecipesFor(type(type));
    }

    @GameTest(template = "empty")
    public static void stockRecipesMatchGtm(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        for (JsonElement el : expectations().getAsJsonArray("stock_recipes")) {
            JsonObject e = el.getAsJsonObject();
            RecipeView exp = RecipeView.expected(e);
            List<RecipeView> sameOutput = recipes(h, e.get("type").getAsString()).stream().map(RecipeView::of)
                    .filter(v -> v.outputs().keySet().equals(exp.outputs().keySet())).toList();
            if (sameOutput.stream().anyMatch(v -> v.differences(exp).isEmpty())) continue;
            if (sameOutput.isEmpty()) problems.add(exp.id() + ": no " + e.get("type").getAsString() + " recipe makes " + exp.outputs().keySet());
            else sameOutput.forEach(v -> problems.add(exp.id() + " vs " + v.id() + ": " + String.join("; ", v.differences(exp))));
        }
        report(h, "stock recipes", problems);
    }

    @GameTest(template = "empty")
    public static void replacedAndDisabledRecipesAreGone(GameTestHelper h) {
        RecipeManager rm = h.getLevel().getRecipeManager();
        List<String> problems = new ArrayList<>();
        for (JsonElement e : expectations().getAsJsonArray("removed_recipes"))
            if (rm.byKey(new ResourceLocation(e.getAsString())).isPresent()) problems.add(e.getAsString() + " still exists");
        Set<String> disabled = new HashSet<>();
        expectations().getAsJsonArray("disabled_multiblocks").forEach(e -> disabled.add(e.getAsString()));
        for (var r : rm.getRecipes()) {
            var out = r.getResultItem(h.getLevel().registryAccess());
            if (!out.isEmpty() && disabled.contains(BuiltInRegistries.ITEM.getKey(out.getItem()).toString()))
                problems.add(r.getId() + " still makes " + BuiltInRegistries.ITEM.getKey(out.getItem()));
        }
        for (var t : GTRegistries.RECIPE_TYPES)
            for (GTRecipe r : h.getLevel().getRecipeManager().getAllRecipesFor(t))
                for (String o : RecipeView.of(r).outputs().keySet())
                    if (disabled.contains(o)) problems.add(r.getId() + " still makes " + o);
        report(h, "removed recipes", problems);
    }

    /** Nothing but our changed recipe makes an item it replaces (catches a wrong removal ID). */
    @GameTest(template = "empty")
    public static void replacedItemsOnlyFromOurRecipes(GameTestHelper h) {
        Map<String, String> replaced = new HashMap<>();
        expectations().getAsJsonObject("replaced_outputs").entrySet()
                .forEach(e -> replaced.put(e.getKey(), e.getValue().getAsString()));
        List<String> problems = new ArrayList<>();
        for (var r : h.getLevel().getRecipeManager().getRecipes()) {
            Set<String> outs = new HashSet<>();
            if (r instanceof GTRecipe gt) outs.addAll(RecipeView.of(gt).outputs().keySet());
            else {
                var out = r.getResultItem(h.getLevel().registryAccess());
                if (!out.isEmpty()) outs.add(BuiltInRegistries.ITEM.getKey(out.getItem()).toString());
            }
            for (String o : outs)
                if (replaced.containsKey(o) && !replaced.get(o).equals(r.getId().toString()))
                    problems.add(r.getId() + " makes " + o + ", which only " + replaced.get(o) + " should make");
        }
        report(h, "replaced items", problems);
    }

    @GameTest(template = "empty")
    public static void ourRecipesLoadedAsDesigned(GameTestHelper h) {
        RecipeManager rm = h.getLevel().getRecipeManager();
        List<String> problems = new ArrayList<>();
        for (JsonElement el : expectations().getAsJsonArray("our_recipes")) {
            JsonObject e = el.getAsJsonObject();
            if (pending(e)) continue;
            RecipeView exp = RecipeView.expected(e);
            var r = rm.byKey(new ResourceLocation(exp.id()));
            if (r.isEmpty() || !(r.get() instanceof GTRecipe gt)) {
                problems.add(exp.id() + " is missing (did the KubeJS script load?)");
                continue;
            }
            if (!gt.getType().equals(type(e.get("type").getAsString())))
                problems.add(exp.id() + " has type " + gt.getType() + ", expected " + e.get("type").getAsString());
            RecipeView.of(gt).differences(exp).forEach(d -> problems.add(exp.id() + ": " + d));
        }
        report(h, "our recipes", problems);
    }

    /** No other recipe of the same type can run from a bus loaded for one of ours, or the other way round. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void ourRecipesDoNotConflict(GameTestHelper h) {
        List<String> problems = new ArrayList<>();
        Set<String> ours = new HashSet<>();
        JsonArray list = expectations().getAsJsonArray("our_recipes");
        list.forEach(e -> ours.add(e.getAsJsonObject().get("id").getAsString()));
        Map<String, List<RecipeView>> byType = new HashMap<>();
        for (JsonElement el : list) {
            JsonObject e = el.getAsJsonObject();
            if (pending(e)) continue;
            String t = e.get("type").getAsString();
            List<RecipeView> all = byType.computeIfAbsent(t, k -> recipes(h, k).stream().map(RecipeView::of).toList());
            RecipeView mine = all.stream().filter(v -> v.id().equals(e.get("id").getAsString())).findFirst().orElse(null);
            if (mine == null) continue; // reported by ourRecipesLoadedAsDesigned
            for (RecipeView other : all) {
                if (other.id().equals(mine.id())) continue;
                if (mine.canRun(other)) problems.add("a bus loaded for " + mine.id() + " can run " + other.id());
                if (!ours.contains(other.id()) && other.canRun(mine))
                    problems.add("a bus loaded for " + other.id() + " can run " + mine.id());
            }
        }
        report(h, "recipe conflicts", problems);
    }
}
