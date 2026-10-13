package dev.utechnology.gametest;

import com.gregtechceu.gtceu.api.registry.GTRegistries;

import dev.utechnology.UTechnology;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Every item, block and recipe type of ours has its name and model in the jar. The game test server never
 * loads client assets, so without this a forgotten tools/run-datagen.sh would only show up in the client.
 */
@GameTestHolder(UTechnology.MOD_ID)
@PrefixGameTestTemplate(false)
public class AssetTests {

    static boolean resource(String path) {
        return AssetTests.class.getResource("/" + path) != null;
    }

    @GameTest(template = "empty")
    public static void namesAndModelsExist(GameTestHelper h) {
        JsonObject lang;
        try (var in = AssetTests.class.getResourceAsStream("/assets/utechnology/lang/en_us.json")) {
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            h.fail("no en_us.json: " + e);
            return;
        }
        List<String> problems = new ArrayList<>();
        for (var e : ForgeRegistries.BLOCKS.getEntries()) {
            var id = e.getKey().location();
            if (!id.getNamespace().equals(UTechnology.MOD_ID)) continue;
            if (!lang.has("block." + id.toLanguageKey())) problems.add("no name for block " + id);
            if (!resource("assets/utechnology/blockstates/" + id.getPath() + ".json")) problems.add("no blockstate for " + id);
        }
        for (var e : ForgeRegistries.ITEMS.getEntries()) {
            var id = e.getKey().location();
            if (!id.getNamespace().equals(UTechnology.MOD_ID)) continue;
            if (!lang.has("item." + id.toLanguageKey()) && !lang.has("block." + id.toLanguageKey())) problems.add("no name for item " + id);
            if (!resource("assets/utechnology/models/item/" + id.getPath() + ".json")) problems.add("no item model for " + id);
        }
        for (var t : GTRegistries.RECIPE_TYPES) {
            if (t.registryName.getNamespace().equals(UTechnology.MOD_ID) && !lang.has(t.registryName.toLanguageKey()))
                problems.add("no name for recipe type " + t.registryName);
        }
        if (!lang.has("itemGroup.utechnology.utechnology")) problems.add("no name for the creative tab");
        DesignTests.report(h, "assets", problems);
    }
}
