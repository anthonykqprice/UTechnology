package dev.utechnology.upgrade;

import java.util.Map;

/** Test access to UpgradePattern's preview fallback. */
public final class UpgradePatternAccess {

    public static Map<String, int[]> previewLimits() {
        return UpgradePattern.PREVIEW_LIMITS;
    }

    private UpgradePatternAccess() {}
}
