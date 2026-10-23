package com.runeborn.claims;

/**
 * Central, Minecraft-free balance rules for Rune effects.
 *
 * All formulas live here so they can be tuned in one place (Phase 2) and unit-checked
 * without a running game. Rarity multipliers are the agreed initial targets:
 * Common 1.0x, Uncommon 1.25x, Rare 1.5x, Epic 2.0x.
 */
public final class RuneBalance {
    private RuneBalance() {}

    // Ember: bonus melee damage.
    public static final double EMBER_BASE_DAMAGE = 0.5;
    public static final double EMBER_MAX_DAMAGE = 2.0;

    // Frost: chance to apply Slowness I on hit.
    public static final double FROST_BASE_CHANCE = 0.10;
    public static final double FROST_MAX_CHANCE = 0.35;
    public static final int FROST_BASE_DURATION_TICKS = 30;

    // Verdant: chance to heal after killing a hostile mob.
    public static final double VERDANT_BASE_CHANCE = 0.10;
    public static final double VERDANT_MAX_CHANCE = 0.30;
    public static final double VERDANT_BASE_HEAL = 1.0;
    public static final double VERDANT_MAX_HEAL = 3.0;

    // Void: chance to negate a fall damage event.
    public static final double VOID_BASE_CHANCE = 0.10;
    public static final double VOID_MAX_CHANCE = 0.30;

    /** Rarity name to multiplier. Unknown rarities return 0 so they grant nothing. */
    public static double rarityMultiplier(String rarity) {
        if (rarity == null) return 0.0;
        return switch (rarity) {
            case "Common" -> 1.0;
            case "Uncommon" -> 1.25;
            case "Rare" -> 1.5;
            case "Epic" -> 2.0;
            default -> 0.0;
        };
    }

    public static double emberDamage(double multiplier) {
        if (multiplier <= 0) return 0.0;
        return Math.min(EMBER_MAX_DAMAGE, EMBER_BASE_DAMAGE * multiplier);
    }

    public static double frostChance(double multiplier) {
        if (multiplier <= 0) return 0.0;
        return Math.min(FROST_MAX_CHANCE, FROST_BASE_CHANCE * multiplier);
    }

    public static int frostDurationTicks(double multiplier) {
        if (multiplier <= 0) return 0;
        return (int) Math.round(FROST_BASE_DURATION_TICKS * multiplier);
    }

    public static double verdantChance(double multiplier) {
        if (multiplier <= 0) return 0.0;
        return Math.min(VERDANT_MAX_CHANCE, VERDANT_BASE_CHANCE * multiplier);
    }

    public static double verdantHeal(double multiplier) {
        if (multiplier <= 0) return 0.0;
        return Math.min(VERDANT_MAX_HEAL, VERDANT_BASE_HEAL * multiplier);
    }

    public static double voidChance(double multiplier) {
        if (multiplier <= 0) return 0.0;
        return Math.min(VOID_MAX_CHANCE, VOID_BASE_CHANCE * multiplier);
    }
}
