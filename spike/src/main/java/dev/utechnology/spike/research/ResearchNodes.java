package dev.utechnology.spike.research;

import java.util.Set;

/** Hardcoded for the spike. The real tree will be data pack JSON generated from design/. */
public final class ResearchNodes {

    public static final String TEST_DURATION = "test_duration_minus_20";

    public static boolean exists(String node) {
        return TEST_DURATION.equals(node);
    }

    static TeamBonus compute(Set<String> nodes) {
        double duration = 1.0;
        if (nodes.contains(TEST_DURATION)) duration *= 0.8;
        return new TeamBonus(duration);
    }

    private ResearchNodes() {}
}
