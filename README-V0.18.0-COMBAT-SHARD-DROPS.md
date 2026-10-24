# Runeborn v0.18.0 — Combat Shard Drops (Phase 4, part 1)

**Not compiled by the assistant** (no Java 25/Fabric downloads there). Build with `.\gradlew.bat clean build`. Drop tiers, amounts and the hourly cap in `RuneBalance.java` were run and checked under plain Java; the in-game wiring is untested.

## How it works

Killing a hostile mob (vanilla mob category `monster`) can drop genuine Rune Shards (the same item the Rune Forge uses). Shards go straight to your inventory, or drop at your feet if it is full. An action-bar line confirms it.

| Mob max health | Examples | Drop chance | Shards |
|---|---|---|---|
| under 30 | zombie, skeleton, creeper, spider | 4% | 1 |
| 30 to 99 | enderman, piglin brute | 12% | 1 |
| 100 and up | ravager, wither, warden, ender dragon | 100% | 2 to 4 |

Expected yield: about 40 shards per 1000 normal kills, so roughly one Rune (3 shards) per 75 normal kills before any tough mobs.

## Protections

- **Hourly cap:** at most 20 combat shards per player per rolling hour (in memory, resets on restart). This blunts mob farms.
- **No drops** in parkour events, in creative or spectator mode, or for player kills. Passive animals never drop shards.
- Admin `/rbrune give <player> shard <amount>` is unaffected.

## In-game checks

1. Kill about 50 zombies: expect roughly 2 shards, with the action-bar line each time.
2. Kill cows/pigs: never any shards.
3. Fill your inventory, then kill mobs until a drop: the shard should land at your feet.
4. Kill an enderman a number of times: noticeably more drops than zombies.
5. Kill a boss-tier mob (ravager, warden, wither): always 2 to 4 shards.
6. Spawn lots of mobs and farm: after 20 shards in an hour, no more drops until the hour passes.
7. Join a parkour event and kill a mob: no drop.
8. Creative-mode kills: no drops.
9. Regression: Rune Forge still takes 3 shards, markets, Crowns, claims, parkour.

## Tuning

All constants (`SHARD_DROP_CHANCE_*`, `TOUGH_MOB_MAX_HEALTH`, `BOSS_MOB_MAX_HEALTH`, `SHARD_HOURLY_CAP`) are at the top of the shard section in `RuneBalance.java`.

## Known limitations

- Mob-spawner and farm mobs cannot be told apart from natural ones; the hourly cap is the only protection.
- The hourly counter is not saved across restarts.
- Exploration and crafting sources are still to come (Phase 4, next parts).
