# Runeborn v0.17.0 — Rune Words (Phase 3)

**Not compiled by the assistant** (no Java 25/Fabric downloads there). Build with `.\gradlew.bat clean build`. Word detection and all values in `RuneBalance.java` were run and checked under plain Java (37 checks); the in-game wiring is untested.

## Rules

- Words work on **weapons only** (two sockets). Armor has one socket, so it cannot form words yet.
- A word is an **ordered pair**: the Rune socketed first, then the Rune socketed second. The reverse order gives no word.
- **Both Runes must be Rare or Epic.** Common and Uncommon never form a word.
- Word strength = average of the two rarity multipliers (Rare+Rare 1.5, Rare+Epic 1.75, Epic+Epic 2.0).
- The Runes keep their normal individual effects; the word is a bonus on top.
- Words appear in the item tooltip (`Rune Word: Rime (Frost -> Ember)`) and as a nether star in the `/rbsocket` GUI.

## The four words

| Word | Order | Effect | Rare+Rare / Epic+Epic |
|---|---|---|---|
| **Cinder** | Ember then Verdant | Hostile kill: chance to gain Regeneration I | 22.5% for 7.5s / 30% for 10s |
| **Rime** | Frost then Ember | Slowed targets take extra magic damage (counts a fresh Frost proc) | +1.5 / +2.0 damage |
| **Hollow** | Void then Frost | Frost procs also apply Weakness I (same duration as the Slowness) | n/a |
| **Bloom** | Verdant then Void | When Void armor negates a fall, you heal | +1.5 / +2.0 health |

Notes:
- Bloom needs Void **armor** to trigger the avoided fall (Void on a weapon does nothing by itself). Bloom only adds the heal.
- Hollow needs the weapon's Frost Rune to proc, which it has.
- Tunable constants are in `RuneBalance.java` (`CINDER_*`, `RIME_*`, `BLOOM_*`, `WORD_MIN_RARITY_MULTIPLIER`).

## In-game checks

Needs two Rare/Epic Runes of the right families. Use `DEBUG_RUNE_FEEDBACK = true` to see Ember and Rime lines.

1. **Order:** Frost then Ember sword shows `Rune Word: Rime` in the tooltip and GUI. Ember then Frost shows no word.
2. **Rarity gate:** Common or Uncommon in either socket shows no word.
3. **Rime:** hit a mob that is slowed (or wait for a Frost proc); you should see an extra Rime hit. Unslowed mobs get none.
4. **Hollow:** Frost procs should also show a Weakness icon on the mob.
5. **Cinder:** kill zombies; you should sometimes gain Regeneration and see the action-bar line. Cows give nothing.
6. **Bloom:** wear Void armor, hold a Bloom sword, fall repeatedly; avoided falls should heal you.
7. Single-Rune and stacked weapons still behave as in v0.16.0. Regression on markets, Crowns, claims, parkour.

## Known limitations

- Weapons socketed before this version with a valid pair will show the word in the GUI immediately, but their stored tooltip updates only the next time lore is rebuilt (not possible for full items since sockets are permanent).
- Word effects are checked on the main-hand item only.
