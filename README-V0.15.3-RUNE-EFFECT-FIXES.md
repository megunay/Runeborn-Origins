# Runeborn v0.15.3 — Rune Effect Fixes

Fixes found by code review of v0.15.2. **Not compiled or run in the Minecraft environment by the assistant** (no Java 25 / Fabric / Mojang downloads available there). Build locally with `.\gradlew.bat clean build` and test on a backup world.

## Changes

1. **Ember now actually applies (probable bug fix).** The bonus is a second hit right after the main hit, while the target is in vanilla invulnerability frames. Vanilla ignores a follow-up hit that is not larger than the previous one, so the 0.5–2.0 magic bonus was likely swallowed. The timer is now cleared for the bonus hit and restored afterwards.
2. **Weapon effects (Ember, Frost) are melee-only.** Previously any damage whose owner was the player (arrows, tridents) triggered them with whatever was held. Shield-blocked or zero-damage hits no longer trigger them either.
3. **Verdant only heals on hostile mob kills** (mob category `monster`), not animals or players, matching the design.
4. **Balance values moved into `RuneBalance.java`.** All base values, caps and rarity multipliers are in one Minecraft-free class (Phase 2 can tune or wire to config there). Values are unchanged from v0.15.2.
5. Version bumped to 0.15.3.

## Current values (unchanged)

| Rarity | Mult | Ember dmg | Frost chance / ticks | Verdant chance / heal | Void chance |
|---|---|---|---|---|---|
| Common | 1.0x | 0.50 | 10% / 30 | 10% / 1.0 | 10% |
| Uncommon | 1.25x | 0.625 | 12.5% / 38 | 12.5% / 1.25 | 12.5% |
| Rare | 1.5x | 0.75 | 15% / 45 | 15% / 1.5 | 15% |
| Epic | 2.0x | 1.00 | 20% / 60 | 20% / 2.0 | 20% |

Balance note for Phase 2: Ember is 0.25 to 0.5 hearts, so it is very subtle next to vanilla sword damage.

## Verified by the assistant

`RuneBalance` formulas were compiled and run under plain Java: rarity scaling, caps, zero/unknown/lowercase rarity returning nothing, Epic > Common, and an Epic Frost proc rate of about 20% over 200k trials. All passed.

## In-game test checklist

1. Ember: hit a zombie with Common vs Epic Ember on the same weapon type and compare hits-to-kill. If Ember still does nothing, report it with the server log.
2. Ember/Frost: shoot a mob with a bow while holding an Ember/Frost sword. No bonus should apply.
3. Frost/Ember: hit a mob that is blocking with a shield. No proc on fully blocked hits.
4. Frost: ~20 hits on a non-player target with Epic Frost; expect about 4 slowness procs.
5. Verdant: kill zombies (can heal) and cows/pigs (never heals). Take damage first so healing is visible.
6. Void: wear Void armor and repeat a fall many times; some falls should do no damage. Unsocketed armor should always take damage.
7. Unsocketed gear and renamed (fake) Runes give no effects.
8. Regression: Rune Forge, /rbsocket, tooltips, Crowns, both markets, claims, parkour, leaderboards, announcements.

## Known limitations

- Ember's bonus hit leaves vanilla's "last hurt" amount at the bonus value, which can make a second hit inside the 10-tick window slightly stronger than vanilla. Watch for this in testing.
- Two Runes of the same family on one weapon use only the stronger one (no stacking yet; Phase 2).
- If compilation fails, the likely spots are `invulnerableTime` (Ember) and `getType().getCategory().getName()` (Verdant); both are mapping-sensitive in 26.2.
