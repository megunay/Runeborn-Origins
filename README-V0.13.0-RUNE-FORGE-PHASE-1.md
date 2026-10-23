# Runeborn v0.13.0 — Rune Forge Phase 1

This phase adds the first server-side Rune Forge loop while retaining existing Runeborn systems.

## How to test

- Use a vanilla **Smithing Table** as the Rune Forge station. Right-click it to open the Rune Forge menu. `/rbforge` opens the same menu from anywhere.
- An admin can grant test materials with `/rbrune give <player> shard <amount>`.
- Keep at least **3 Rune Shards** in your inventory and click **Process Rune Shards**.
- The forge consumes 3 marked Rune Shards and creates one Rune with a server-generated UUID, affinity, property, and rarity.
- Runes are represented by Echo Shards with internal `CUSTOM_DATA` markers. The server validates Rune Shards by item type and marker, not by their display name or lore. Ordinary renamed Amethyst Shards do not count.

## Phase 1 boundaries

Rarity odds: Common 55%, Uncommon 27%, Rare 14%, Epic 4%.

Affinities and initial properties:
- Ember: Might or Focus
- Frost: Guard or Resilience
- Verdant: Vitality or Fortune
- Void: Swiftness or Discovery

This phase only creates and identifies Rune items. Socketing equipment, applying gameplay effects, Rune-word combinations, and natural shard acquisition are not included yet. Those should be separate follow-up phases so they can be balanced and tested without risking the existing economy or marketplace.

## Notes

- The Smithing Table is used to avoid requiring a client-side mod or registering a new custom block/model.
- `/rbrune give` is permission-gated to server administrators and is intended for testing / controlled distribution.
- Existing Crowns, shop, player marketplace, jobs, parkour, leaderboards, and event announcements are otherwise left unchanged.


## v0.13.1 interaction fix

- Normal right-click on a Smithing Table now uses vanilla Smithing Table functionality.
- Sneak + right-click (hold Shift while right-clicking) opens the Rune Forge.
- `/rbforge` continues to open the Rune Forge directly.


## v0.14.2 socket mapping fix

- Replaced `SwordItem`, `AxeItem`, and `ArmorItem` class checks with vanilla `ItemTags` checks for swords, axes, and armor.
- This avoids depending on implementation class names that are absent from the current Minecraft 26.2 mappings.
