package dev.utechnology.upgrade;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Base class for every upgradeable multiblock (design/multiblocks.yaml framework). All pattern, segment-count,
 * frame and module logic lives here, so GTM 8.0's pattern API migration touches one class.
 * <p>
 * Structure: front aisle with the controller, 1..32 segments (each with this machine's core block), end aisle
 * whose corners are frame boxes. Every casing and frame box must be the same frame. The frame caps voltage, the
 * number of segments that count, and the module slots.
 * <p>
 * Upgrades live on the machine and are persisted: frame, segment count, modules and the locked recipe. The bonus
 * is computed when the structure forms, a module changes, or the upgrade data reloads, never per recipe.
 */
public class UpgradeableMultiblockMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            UpgradeableMultiblockMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** Index of the repeatable aisle in the pattern: 0 front, 1 segments, 2 end. */
    public static final int SEGMENT_AISLE = 1;
    /** The most module slots any frame can have; slots beyond the frame's count are ignored. */
    public static final int MODULE_SLOTS = 3;

    private final String specKey;

    @Persisted
    private int builtSegments;
    @Persisted
    private String frameKey = "";
    @Persisted
    private final CustomItemStackHandler modules;
    /** The recipe a specialization lock is locked to, or "" before its first recipe. */
    @Persisted
    private String lockedRecipe = "";

    // from the latest successful structure check; copied while holding the shared pattern's lock
    private volatile Checked checked;
    private List<BlockPos> casingPositions = List.of();
    private List<BlockPos> frameBoxPositions = List.of();

    private BonusCalculator.@Nullable Bonus bonus;
    private int bonusVersion = -1;

    private record Checked(int built, String frame, List<BlockPos> casings, List<BlockPos> boxes) {}

    public UpgradeableMultiblockMachine(IMachineBlockEntity holder, String specKey, Object... args) {
        super(holder, args);
        this.specKey = specKey;
        this.modules = new CustomItemStackHandler(MODULE_SLOTS);
        this.modules.setFilter(stack -> UpgradeData.get().moduleByItem(stack.getItem()) != null);
        this.modules.setOnContentsChanged(this::onModulesChanged);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    // ------------------------------------------------------------------ data

    public String getSpecKey() {
        return specKey;
    }

    public @Nullable UpgradeData.Spec spec() {
        return UpgradeData.get().specs().get(specKey);
    }

    public @Nullable UpgradeData.Frame frame() {
        return UpgradeData.get().frames().get(frameKey);
    }

    public String getFrameKey() {
        return frameKey;
    }

    public int getBuiltSegments() {
        return isFormed() ? builtSegments : 0;
    }

    public CustomItemStackHandler getModules() {
        return modules;
    }

    public @Nullable ResourceLocation getLockedRecipe() {
        return lockedRecipe.isEmpty() ? null : new ResourceLocation(lockedRecipe);
    }

    // ------------------------------------------------------------------ structure

    /**
     * GTM memoizes one BlockPattern per machine type and stores the matched repeat counts in it, shared by every
     * controller of that type and checked from two threads. Check and copy under the pattern's monitor.
     */
    @Override
    public boolean checkPattern() {
        BlockPattern pattern = getPattern();
        if (pattern == null) return false;
        synchronized (pattern) {
            var state = getMultiblockState();
            boolean formed = pattern.checkPatternAt(state, false);
            if (!formed) return false;
            var ctx = state.getMatchContext();
            Set<String> frames = ctx.getOrDefault(FramePredicates.FRAMES, Set.of());
            if (frames.size() != 1) {
                state.setError(new PatternStringError("utechnology.structure.mixed_frames"));
                return false;
            }
            checked = new Checked(pattern.getFormedRepetitionCount()[SEGMENT_AISLE], frames.iterator().next(),
                    positions(ctx.getOrDefault(FramePredicates.CASINGS, new LongOpenHashSet())),
                    positions(ctx.getOrDefault(FramePredicates.BOXES, new LongOpenHashSet())));
            return true;
        }
    }

    private static List<BlockPos> positions(LongOpenHashSet set) {
        List<BlockPos> out = new ArrayList<>(set.size());
        set.forEach((long l) -> out.add(BlockPos.of(l)));
        return List.copyOf(out);
    }

    @Override
    public void onStructureFormed() {
        Checked c = checked;
        if (c != null) {
            builtSegments = c.built();
            frameKey = c.frame();
            casingPositions = c.casings();
            frameBoxPositions = c.boxes();
        }
        bonus = null; // before super: it computes the voltage tier from getMaxVoltage, which needs the frame
        super.onStructureFormed();
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        bonus = null;
        casingPositions = List.of();
        frameBoxPositions = List.of();
    }

    // ------------------------------------------------------------------ voltage: capped at the frame

    private long frameVoltage() {
        var f = frame();
        return f == null ? GTValues.V[GTValues.ULV] : GTValues.V[f.stats().maxVoltageTier()];
    }

    @Override
    public long getMaxVoltage() {
        return Math.min(super.getMaxVoltage(), frameVoltage());
    }

    @Override
    public long getOverclockVoltage() {
        return Math.min(super.getOverclockVoltage(), frameVoltage());
    }

    // ------------------------------------------------------------------ bonus

    /** The cached bonus; recomputed only after the structure, the modules or the upgrade data changed. */
    public BonusCalculator.@Nullable Bonus bonus() {
        var data = UpgradeData.get();
        if (bonus == null || bonusVersion != data.version()) {
            var f = frame();
            if (f == null || !isFormed()) return null;
            List<BonusCalculator.Module> mods = new ArrayList<>();
            for (int i = 0; i < modules.getSlots(); i++) {
                var m = data.moduleByItem(modules.getStackInSlot(i).getItem());
                mods.add(modules.getStackInSlot(i).isEmpty() || m == null ? null : m.stats());
            }
            bonus = BonusCalculator.compute(f.stats(), builtSegments, mods);
            bonusVersion = data.version();
        }
        return bonus;
    }

    private void onModulesChanged() {
        bonus = null;
        var data = UpgradeData.get();
        boolean hasLock = false;
        for (int i = 0; i < modules.getSlots(); i++) {
            var m = data.moduleByItem(modules.getStackInSlot(i).getItem());
            if (m != null && m.stats().lock()) hasLock = true;
        }
        if (!hasLock) lockedRecipe = "";
    }

    /** The recipe filter: component recipes only, everything else, or no filter (multiblock spec). */
    public boolean accepts(GTRecipe recipe) {
        var spec = spec();
        if (spec == null) return false;
        if (spec.filter().equals("none")) return true;
        var outs = RecipeHelper.getOutputItems(recipe);
        boolean component = !outs.isEmpty() && spec.componentItems().contains(outs.get(0).getItem());
        return spec.filter().equals("component") == component;
    }

    /** A specialization lock locks to the first recipe the machine runs after it is installed. */
    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (!super.beforeWorking(recipe)) return false;
        var b = bonus();
        if (recipe != null && b != null && b.locked() && lockedRecipe.isEmpty() && recipe.getId() != null)
            lockedRecipe = recipe.getId().toString();
        return true;
    }

    // ------------------------------------------------------------------ right-click: modules and retrofit kits

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand,
                                   BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        var data = UpgradeData.get();
        if (data.frameByKit(held.getItem()) != null) {
            if (!world.isClientSide) message(player, retrofit(player, data.frameByKit(held.getItem())));
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
        if (data.moduleByItem(held.getItem()) != null) {
            if (!world.isClientSide) message(player, installModule(held, player));
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (!world.isClientSide) {
                ItemStack removed = removeLastModule();
                if (removed.isEmpty()) return InteractionResult.PASS;
                player.getInventory().placeItemBackInInventory(removed);
                message(player, Component.translatable("utechnology.module.removed", removed.getHoverName()));
            }
            return InteractionResult.sidedSuccess(world.isClientSide);
        }
        return super.onUse(state, world, pos, player, hand, hit);
    }

    private static void message(Player player, Component c) {
        player.displayClientMessage(c, true);
    }

    /** Installs one module from the held stack if the frame has a free slot for it. */
    public Component installModule(ItemStack held, @Nullable Player player) {
        var data = UpgradeData.get();
        var m = data.moduleByItem(held.getItem());
        var f = frame();
        if (m == null) return Component.translatable("utechnology.module.not_a_module");
        if (f == null || !isFormed()) return Component.translatable("utechnology.machine.not_formed");
        if (m.stats().unlockedTier() > f.stats().tier())
            return Component.translatable("utechnology.module.needs_frame", BonusCalculator.TIERS.get(m.stats().unlockedTier()));
        int used = 0;
        for (int i = 0; i < modules.getSlots(); i++) {
            ItemStack s = modules.getStackInSlot(i);
            if (s.isEmpty()) continue;
            used++;
            if (s.getItem() == held.getItem()) return Component.translatable("utechnology.module.already_installed");
        }
        if (used >= f.stats().moduleSlots())
            return Component.translatable("utechnology.module.no_slot", f.stats().moduleSlots());
        for (int i = 0; i < modules.getSlots(); i++) {
            if (modules.getStackInSlot(i).isEmpty()) {
                modules.setStackInSlot(i, held.copyWithCount(1));
                if (player == null || !player.getAbilities().instabuild) held.shrink(1);
                return Component.translatable("utechnology.module.installed", held.getHoverName());
            }
        }
        return Component.translatable("utechnology.module.no_slot", f.stats().moduleSlots());
    }

    /** Removes the module in the last filled slot and returns it. */
    public ItemStack removeLastModule() {
        for (int i = modules.getSlots() - 1; i >= 0; i--) {
            ItemStack s = modules.getStackInSlot(i);
            if (!s.isEmpty()) {
                modules.setStackInSlot(i, ItemStack.EMPTY);
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    /** How many kits converting this machine takes: one per casings_per_kit casings or frame boxes, rounded up. */
    public int kitsNeeded() {
        var spec = spec();
        int perKit = spec == null ? 8 : spec.casingsPerKit();
        int n = casingPositions.size() + frameBoxPositions.size();
        return (n + perKit - 1) / perKit;
    }

    /**
     * Converts every casing and frame box to the target frame in one action, using the player's kits and giving
     * the old blocks back. Controller, hatches, cores and modules stay. Works up and down.
     */
    public Component retrofit(Player player, UpgradeData.Frame target) {
        var current = frame();
        if (!isFormed() || current == null) return Component.translatable("utechnology.machine.not_formed");
        if (current.key().equals(target.key())) return Component.translatable("utechnology.retrofit.same_frame");
        int need = kitsNeeded();
        boolean creative = player.getAbilities().instabuild;
        if (!creative && countItem(player, target.kit()) < need)
            return Component.translatable("utechnology.retrofit.not_enough_kits", need);
        if (!creative) takeItem(player, target.kit(), need);
        var casings = List.copyOf(casingPositions);
        var boxes = List.copyOf(frameBoxPositions);
        Level level = getLevel();
        for (BlockPos p : casings) level.setBlockAndUpdate(p, target.casing().defaultBlockState());
        for (BlockPos p : boxes) level.setBlockAndUpdate(p, target.frameBox().defaultBlockState());
        give(player, new ItemStack(current.casing().asItem(), casings.size()));
        give(player, new ItemStack(current.frameBox().asItem(), boxes.size()));
        return Component.translatable("utechnology.retrofit.done", need, target.key());
    }

    private static int countItem(Player p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void takeItem(Player p, net.minecraft.world.item.Item item, int count) {
        for (ItemStack s : p.getInventory().items) {
            if (count <= 0) return;
            if (s.is(item)) {
                int t = Math.min(count, s.getCount());
                s.shrink(t);
                count -= t;
            }
        }
    }

    private static void give(Player p, ItemStack stack) {
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(stack.getMaxStackSize());
            p.getInventory().placeItemBackInInventory(part);
        }
    }

    // ------------------------------------------------------------------ UI

    @Override
    public void attachConfigurators(ConfiguratorPanel panel) {
        super.attachConfigurators(panel);
        panel.attachConfigurators(new ModulesConfigurator(modules));
    }

    @Override
    public void addDisplayText(List<Component> text) {
        super.addDisplayText(text);
        if (!isFormed()) return;
        var f = frame();
        var b = bonus();
        if (f == null || b == null) return;
        text.add(Component.translatable("utechnology.display.frame", f.key(), BonusCalculator.TIERS.get(f.stats().maxVoltageTier())));
        text.add(Component.translatable("utechnology.display.segments", b.counted(), b.built(), f.stats().segmentCap()));
        if (b.built() > b.counted())
            text.add(Component.translatable("utechnology.display.over_cap").withStyle(ChatFormatting.YELLOW));
        text.add(Component.translatable("utechnology.display.parallels", b.parallelLimit()));
        if (!b.installed().isEmpty()) text.add(Component.translatable("utechnology.display.modules", String.join(", ", b.installed())));
        for (String why : b.ignored()) text.add(Component.translatable("utechnology.display.ignored", why).withStyle(ChatFormatting.RED));
        if (b.locked()) text.add(Component.translatable("utechnology.display.locked", lockedRecipe.isEmpty() ? "-" : lockedRecipe));
    }
}
