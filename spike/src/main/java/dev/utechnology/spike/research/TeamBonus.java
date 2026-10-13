package dev.utechnology.spike.research;

/** Precomputed research effects for one team. Immutable. */
public record TeamBonus(double durationMultiplier) {

    public static final TeamBonus NONE = new TeamBonus(1.0);
}
