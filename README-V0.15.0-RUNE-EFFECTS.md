# Runeborn v0.15.0 — Rune Effects

This release adds the first server-side gameplay effects for socketed Runes. Effects are based on validated socket metadata on the equipment; display names and lore are not used as authority.

## Rarity scaling

- Common: 1.0x
- Uncommon: 1.25x
- Rare: 1.5x
- Epic: 2.0x

The multiplier is used by the effect formulas, with caps to avoid excessive stacking.

## Effects

- **Ember (weapon):** Adds bonus magic damage on a successful melee hit. Base bonus is 0.5 damage, multiplied by rarity and capped at 2.0 damage.
- **Frost (weapon):** Chance to apply Slowness I on a successful hit. Base chance is 10% multiplied by rarity, capped at 35%; duration is 30 ticks multiplied by rarity.
- **Verdant (weapon held on kill):** Chance to heal the player after a hostile/entity kill. Base chance is 10% multiplied by rarity, capped at 30%; healing is 1 health point multiplied by rarity, capped at 3.
- **Void (armor):** Chance to prevent a fall-damage event. Base chance is 10% multiplied by the strongest Void Rune worn, capped at 30%. Only the strongest Void Rune worn is used, so wearing multiple Void Runes does not multiply the chance.

## Testing

1. Build with `.\gradlew.bat clean build`.
2. Test Common vs Epic Ember on the same weapon and compare damage.
3. Test Frost proc frequency and duration on a non-player target.
4. Kill mobs while holding a weapon socketed with Verdant and verify occasional healing.
5. Wear armor with Common and Epic Void Runes and test repeated falls from the same height. The effect is probabilistic, so use many attempts.
6. Verify empty/non-socketed equipment grants no effects.
7. Verify Runes with modified display names but without valid Runeborn custom data grant no effects.
8. Regression-test Rune Forge, socket GUI/tooltips, markets, Crowns, parkour, leaderboards, and announcements.

## Important

This package has not been independently compiled or runtime-tested in the assistant environment. Build it locally and test in a backup world before replacing a production server JAR. Rune-word combinations are not included in this release.


## v0.15.1 mapping compatibility fix

- Uses `MobEffects.SLOWNESS` for the Frost effect.
- Iterates equipped armor through `player.getInventory().armor` for Void protection.
