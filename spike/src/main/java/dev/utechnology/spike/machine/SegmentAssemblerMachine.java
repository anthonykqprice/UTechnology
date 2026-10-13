package dev.utechnology.spike.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

/**
 * Assembler multiblock whose parallel count equals the number of middle segments built.
 */
public class SegmentAssemblerMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            SegmentAssemblerMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    /** Index of the repeatable aisle in the pattern (0 = front, 1 = middle, 2 = end). */
    public static final int MIDDLE_AISLE = 1;

    /** Segments counted by the most recent successful pattern check. */
    private volatile int checkedSegments;
    /** Segments of the formed structure. Persisted so a reloaded, still-formed machine knows its size. */
    @Persisted
    private int segments;

    public static long checkCount; // test instrumentation only

    public SegmentAssemblerMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /**
     * GTM memoizes one BlockPattern per machine definition, and BlockPattern stores the repeat counts of the last
     * check in a shared field (formedRepetitionCount). Checks run on the main thread and on GTM's async
     * structure-check thread, each holding only its own controller's lock. Serialize on the shared pattern and copy
     * the count while we hold it.
     */
    @Override
    public boolean checkPattern() {
        BlockPattern pattern = getPattern();
        if (pattern == null) return false;
        synchronized (pattern) {
            checkCount++;
            boolean formed = pattern.checkPatternAt(getMultiblockState(), false);
            checkedSegments = formed ? pattern.getFormedRepetitionCount()[MIDDLE_AISLE] : 0;
            return formed;
        }
    }

    @Override
    public void onStructureFormed() {
        segments = checkedSegments;
        super.onStructureFormed();
    }

    @Override
    public void onStructureInvalid() {
        segments = 0;
        super.onStructureInvalid();
    }

    public int getSegments() {
        return isFormed() ? segments : 0;
    }
}
