# Runeborn v0.16.0 — Balance and Stacking (Phase 2)

**Not compiled by the assistant** (no Java 25/Fabric downloads there). Build with `.\gradlew.bat clean build`. The formulas in `RuneBalance.java` were run and checked under plain Java; the in-game wiring is untested.

## Changes

1. **Ember doubled.** Base damage 0.5 → 1.0, cap 2.0 → 4.0. (v0.15.4 confirmed in game that Ember lands: `applied=true`, HP dropped by the bonus.)
2. **Diminishing stacking on weapons.** Two same-family Runes on one weapon: the stronger counts 100%, the other counts 50%.
   Effective multiplier = strongest + 0.5 x second. Order does not matter.
   Applies to Ember, Frost and Verdant (weapon sockets only).
3. **Void unchanged.** Armor still uses only the strongest Void Rune worn (no stacking across pieces).
4. **Debug readout off** (`DEBUG_RUNE_FEEDBACK = false`). Flip it to true to see Ember procs again.

## Values (single Rune)

| Rarity | Mult | Ember dmg | Frost chance / ticks | Verdant chance / heal | Void chance |
|---|---|---|---|---|---|
| Common | 1.0x | 1.0 | 10% / 30 | 10% / 1.0 | 10% |
| Uncommon | 1.25x | 1.25 | 12.5% / 38 | 12.5% / 1.25 | 12.5% |
| Rare | 1.5x | 1.5 | 15% / 45 | 15% / 1.5 | 15% |
| Epic | 2.0x | 2.0 | 20% / 60 | 20% / 2.0 | 20% |

## Two Runes of the same family on one weapon

| Combo | Eff. mult | Ember | Frost chance / ticks | Verdant chance / heal |
|---|---|---|---|---|
| Common + Common | 1.5 | 1.5 | 15% / 45 | 15% / 1.5 |
| Common + Epic | 2.5 | 2.5 | 25% / 75 | 25% / 2.5 |
| Epic + Epic | 3.0 | 3.0 | 30% / 90 | 30% / 3.0 |

Double Epic stays under every cap, so stacking is never flattened.

## In-game checks

1. Ember: one Epic Ember sword vs. the same sword with two Epic Embers (turn the debug readout on): expect about 2.0 vs 3.0 bonus HP loss per hit.
2. Mixed weapon (Common + Epic Ember): expect about 2.5.
3. Frost with two Runes: noticeably more frequent and longer slowness.
4. Void armor with Void on several pieces: still one chance, not multiplied.
5. Regression: Rune Forge, /rbsocket, tooltips, markets, Crowns, claims, parkour.

Tuning values: edit the constants at the top of `RuneBalance.java`.
