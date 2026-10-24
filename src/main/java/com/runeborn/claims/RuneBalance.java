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

    /** Shows an action-bar line on each Ember proc. Set to false before a public release. */
    public static final boolean DEBUG_RUNE_FEEDBACK = false;

    // Ember: bonus melee damage.
    public static final double EMBER_BASE_DAMAGE = 1.0;
    public static final double EMBER_MAX_DAMAGE = 4.0;

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

    /** Share of its value that each additional same-family Rune contributes (strongest counts fully). */
    public static final double STACK_SECONDARY_SHARE = 0.5;

    /**
     * Diminishing stacking for same-family Runes on one item: the strongest counts 100%,
     * the next one counts {@link #STACK_SECONDARY_SHARE}. Weapons have at most two sockets.
     * Returns 0 when there are no valid Runes.
     */
    public static double stackedMultiplier(double... multipliers) {
        double first = 0, second = 0;
        for (double m : multipliers) {
            if (m <= 0) continue;
            if (m > first) { second = first; first = m; }
            else if (m > second) { second = m; }
        }
        return first + STACK_SECONDARY_SHARE * second;
    }

    // ---- Rune words (weapons only: two sockets, inserted in order) ----

    /** Both Runes must be at least this strong (Rare = 1.5x, Epic = 2.0x) to form a word. */
    public static final double WORD_MIN_RARITY_MULTIPLIER = 1.5;

    public enum RuneWord {
        CINDER("Cinder", "Ember", "Verdant", "Hostile kills may grant Regeneration"),
        RIME("Rime", "Frost", "Ember", "Slowed targets take extra damage"),
        HOLLOW("Hollow", "Void", "Frost", "Frost procs also apply Weakness"),
        BLOOM("Bloom", "Verdant", "Void", "Avoiding fall damage heals you");

        public final String displayName;
        public final String first;
        public final String second;
        public final String description;

        RuneWord(String displayName, String first, String second, String description) {
            this.displayName = displayName;
            this.first = first;
            this.second = second;
            this.description = description;
        }
    }

    /** A word that is currently active, with its strength (average of the two rarity multipliers). */
    public record ActiveWord(RuneWord word, double strength) {}

    /**
     * Detects a Rune word from the first two sockets, in insertion order.
     * Returns null unless the exact ordered pair matches and both Runes are Rare or Epic.
     */
    public static ActiveWord detectWord(String family0, String rarity0, String family1, String rarity1) {
        double m0 = rarityMultiplier(rarity0);
        double m1 = rarityMultiplier(rarity1);
        if (m0 < WORD_MIN_RARITY_MULTIPLIER || m1 < WORD_MIN_RARITY_MULTIPLIER) return null;
        if (family0 == null || family1 == null) return null;
        for (RuneWord w : RuneWord.values()) {
            if (w.first.equals(family0) && w.second.equals(family1)) {
                return new ActiveWord(w, (m0 + m1) / 2.0);
            }
        }
        return null;
    }

    // Cinder: chance on hostile kill to gain Regeneration I.
    public static final double CINDER_BASE_CHANCE = 0.15;
    public static final double CINDER_MAX_CHANCE = 0.30;
    public static final int CINDER_BASE_TICKS = 100;

    // Rime: extra magic damage when the target is slowed.
    public static final double RIME_BASE_DAMAGE = 1.0;
    public static final double RIME_MAX_DAMAGE = 3.0;

    // Bloom: heal when a fall is negated by Void armor.
    public static final double BLOOM_BASE_HEAL = 1.0;
    public static final double BLOOM_MAX_HEAL = 3.0;

    public static double cinderChance(double strength) {
        if (strength <= 0) return 0.0;
        return Math.min(CINDER_MAX_CHANCE, CINDER_BASE_CHANCE * strength);
    }

    public static int cinderRegenTicks(double strength) {
        if (strength <= 0) return 0;
        return (int) Math.round(CINDER_BASE_TICKS * strength);
    }

    public static double rimeDamage(double strength) {
        if (strength <= 0) return 0.0;
        return Math.min(RIME_MAX_DAMAGE, RIME_BASE_DAMAGE * strength);
    }

    public static double bloomHeal(double strength) {
        if (strength <= 0) return 0.0;
        return Math.min(BLOOM_MAX_HEAL, BLOOM_BASE_HEAL * strength);
    }

    // ---- Rune Shard drops from combat (Phase 4) ----

    /** Max health at or above which a hostile mob counts as tough / boss-tier. */
    public static final double TOUGH_MOB_MAX_HEALTH = 30.0;
    public static final double BOSS_MOB_MAX_HEALTH = 100.0;

    public static final double SHARD_DROP_CHANCE_NORMAL = 0.04;
    public static final double SHARD_DROP_CHANCE_TOUGH = 0.12;
    public static final double SHARD_DROP_CHANCE_BOSS = 1.0;
    public static final int SHARD_BOSS_MIN = 2;
    public static final int SHARD_BOSS_MAX = 4;

    /** Per-player cap on combat shards per rolling hour, to blunt mob farms. */
    public static final int SHARD_HOURLY_CAP = 20;
    public static final long SHARD_WINDOW_MILLIS = 60L * 60L * 1000L;

    public static double shardDropChance(double mobMaxHealth) {
        if (mobMaxHealth >= BOSS_MOB_MAX_HEALTH) return SHARD_DROP_CHANCE_BOSS;
        if (mobMaxHealth >= TOUGH_MOB_MAX_HEALTH) return SHARD_DROP_CHANCE_TOUGH;
        return SHARD_DROP_CHANCE_NORMAL;
    }

    /** Shards for a successful drop. roll01 is a uniform random in [0,1). Boss-tier gives 2 to 4. */
    public static int shardDropAmount(double mobMaxHealth, double roll01) {
        if (mobMaxHealth >= BOSS_MOB_MAX_HEALTH) {
            int span = SHARD_BOSS_MAX - SHARD_BOSS_MIN + 1;
            int offset = (int) Math.floor(Math.max(0.0, Math.min(0.999999999, roll01)) * span);
            return SHARD_BOSS_MIN + offset;
        }
        return 1;
    }

    /** Shards actually granted after applying the hourly cap. */
    public static int shardsAllowed(int alreadyThisWindow, int wanted) {
        return Math.max(0, Math.min(wanted, SHARD_HOURLY_CAP - Math.max(0, alreadyThisWindow)));
    }

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
