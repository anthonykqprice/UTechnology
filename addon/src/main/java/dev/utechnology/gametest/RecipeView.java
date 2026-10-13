package dev.utechnology.gametest;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.FluidIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonObject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A recipe reduced to what the design records: inputs as (set of accepted items, count), fluids, circuit,
 * outputs, duration and EU/t. Lets GameTests compare live GTM recipes with design/ expectations.
 */
public record RecipeView(String id, List<Entry> items, Map<String, Long> fluids, Integer circuit,
                         Map<String, Integer> outputs, int duration, long eut) {

    /** One item input: the items it accepts and how many. */
    public record Entry(Set<String> accepts, int count) {

        @Override
        public String toString() {
            String what = accepts.size() == 1 ? accepts.iterator().next() : accepts.size() + " items";
            return count + "x " + what;
        }
    }

    public static RecipeView of(GTRecipe r) {
        List<Entry> items = new ArrayList<>();
        Integer circuit = null;
        for (Content c : r.getInputContents(ItemRecipeCapability.CAP)) {
            Ingredient ing = ItemRecipeCapability.CAP.of(c.getContent());
            Ingredient inner = SizedIngredient.getInner(ing);
            if (inner instanceof IntCircuitIngredient) {
                circuit = IntCircuitBehaviour.getCircuitConfiguration(inner.getItems()[0]);
                continue;
            }
            ItemStack[] stacks = ing.getItems();
            int count = stacks.length > 0 ? stacks[0].getCount() : 0;
            items.add(new Entry(Arrays.stream(stacks).map(s -> key(s.getItem())).collect(Collectors.toCollection(TreeSet::new)), count));
        }
        Map<String, Long> fluids = new TreeMap<>();
        for (Content c : r.getInputContents(FluidRecipeCapability.CAP)) {
            FluidIngredient f = FluidRecipeCapability.CAP.of(c.getContent());
            var stacks = f.getStacks();
            if (stacks.length > 0) fluids.merge(ForgeRegistries.FLUIDS.getKey(stacks[0].getFluid()).toString(), (long) stacks[0].getAmount(), Long::sum);
        }
        Map<String, Integer> outs = new TreeMap<>();
        for (ItemStack s : RecipeHelper.getOutputItems(r)) outs.merge(key(s.getItem()), s.getCount(), Integer::sum);
        return new RecipeView(r.getId().toString(), items, fluids, circuit, outs, r.duration,
                RecipeHelper.getRealEUt(r).voltage());
    }

    /** The design side: items_in maps an item id or "#tag" to a count. */
    public static RecipeView expected(JsonObject e) {
        List<Entry> items = new ArrayList<>();
        e.getAsJsonObject("items_in").entrySet().forEach(x -> items.add(new Entry(accepts(x.getKey()), x.getValue().getAsInt())));
        Map<String, Long> fluids = new TreeMap<>();
        e.getAsJsonObject("fluids_in").entrySet().forEach(x -> fluids.put(x.getKey(), x.getValue().getAsLong()));
        Map<String, Integer> outs = new TreeMap<>();
        e.getAsJsonObject("items_out").entrySet().forEach(x -> outs.put(x.getKey(), x.getValue().getAsInt()));
        Integer circuit = e.has("circuit") ? e.get("circuit").getAsInt() : null;
        int duration = e.has("duration") ? e.get("duration").getAsInt() : -1;
        long eut = e.has("eut") ? e.get("eut").getAsLong() : -1;
        return new RecipeView(e.has("id") ? e.get("id").getAsString() : e.get("design_id").getAsString(), items, fluids,
                circuit, outs, duration, eut);
    }

    public static Set<String> accepts(String idOrTag) {
        if (idOrTag.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(net.minecraft.core.registries.Registries.ITEM, new ResourceLocation(idOrTag.substring(1)));
            Set<String> out = new TreeSet<>();
            BuiltInRegistries.ITEM.getTagOrEmpty(tag).forEach(h -> out.add(key(h.value())));
            return out;
        }
        return new TreeSet<>(Set.of(idOrTag));
    }

    static String key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** Differences from the expected recipe, empty if it matches. Duration/EU/t are skipped when expected is -1. */
    public List<String> differences(RecipeView exp) {
        List<String> d = new ArrayList<>();
        if (!sameItems(items, exp.items)) d.add("item inputs " + items + " != " + exp.items);
        if (!fluids.equals(exp.fluids)) d.add("fluid inputs " + fluids + " != " + exp.fluids);
        if (!Objects.equals(circuit, exp.circuit)) d.add("circuit " + circuit + " != " + exp.circuit);
        if (!outputs.equals(exp.outputs)) d.add("outputs " + outputs + " != " + exp.outputs);
        if (exp.duration >= 0 && duration != exp.duration) d.add("duration " + duration + " != " + exp.duration);
        if (exp.eut >= 0 && eut != exp.eut) d.add("EU/t " + eut + " != " + exp.eut);
        return d;
    }

    private static boolean sameItems(List<Entry> a, List<Entry> b) {
        if (a.size() != b.size()) return false;
        List<Entry> rest = new ArrayList<>(b);
        for (Entry x : a) {
            Optional<Entry> m = rest.stream().filter(y -> y.count == x.count && y.accepts.equals(x.accepts)).findFirst();
            if (m.isEmpty()) return false;
            rest.remove(m.get());
        }
        return true;
    }

    /**
     * True if a bus holding this recipe's inputs could also run {@code other}: every input of {@code other}
     * (items, fluids, circuit) is accepted by one of this recipe's inputs. Amounts are ignored because
     * non-blocking pattern providers let inputs accumulate.
     */
    public boolean canRun(RecipeView other) {
        if (!Objects.equals(other.circuit, circuit) && other.circuit != null) return false;
        if (!fluids.keySet().containsAll(other.fluids.keySet())) return false;
        for (Entry e : other.items) {
            boolean covered = items.stream().anyMatch(x -> !Collections.disjoint(x.accepts, e.accepts));
            if (!covered) return false;
        }
        return true;
    }
}
