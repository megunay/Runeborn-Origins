# Runeborn v0.19.0 — Exploration Shards (Phase 4, part 2)

**Not compiled by the assistant** (no Java 25/Fabric downloads there). Build with `.\gradlew.bat clean build`. Chances, amounts and structure matching in `RuneBalance.java` were run and checked under plain Java (27 checks); the in-game wiring is untested.

## How it works

The first time a player opens a **generated loot chest** (one that still has a vanilla loot table), there is a chance of a bonus Rune Shard added straight to the inventory (or dropped at their feet if full). The vanilla loot is untouched. Each chest pays out at most once, because opening it clears its loot table.

| Structure | Chance | Shards |
|---|---|---|
| Ancient city, stronghold, bastion, end city, woodland mansion, trial chambers chests | 50% | 1 or 2 |
| Dungeon, abandoned mineshaft chests, desert pyramid, jungle temple, nether fortress, ruined portal, shipwreck, buried treasure, pillager outpost, igloo | 20% | 1 |
| Villages, spawn bonus chest, any other chest | 0% | none |

## Protections

- The bonus is granted only after the chest **actually opened** (checked a tick later), so claim protection that blocks opening cannot hand out free shards.
- No bonus in creative, spectator or parkour events.
- Player-placed chests have no loot table, so they never pay out.

## In-game checks

1. Open several dungeon chests (monster spawner rooms): about 1 in 5 should give a shard and an action-bar line.
2. Open an ancient city or stronghold chest: about half should give 1 or 2 shards.
3. Open a chest, close it, open again: no second bonus.
4. Open village chests and a spawn bonus chest: never a bonus.
5. Open a loot chest inside someone else's claim where you are denied: no bonus.
6. Fill your inventory and open a chest that procs: the shard lands at your feet.
7. Creative-mode and parkour: no bonus.
8. Regression: combat shards, Rune Forge, markets, Crowns, claims, parkour.

## Known limitations

- **Minecart chests** (mineshaft carts) are entities, not blocks, so they are not covered yet.
- Barrels and other containers with loot tables count if their table matches the list.
- If compilation fails, the likely spots are `container.getLootTable().identifier()` and `level.getBlockEntity(...) instanceof RandomizableContainer`; both are mapping-sensitive in 26.2.
- No per-player limit on exploration shards, since each chest is one-time.
