Version note: v0.10.5 replaces the unavailable EntityPickupItemEvents API with a server-side ItemEntity mixin.

# Runeborn v0.10.4 — Parkour Inventory Isolation Test

This update is based on the working v0.10.3 rewards/checkpoint project.

## Behavior

- At the start of the race, each participant's complete player inventory is serialized, including armor and offhand slots.
- The backup is written to `world/data/runeborn-event-inventory-backups.json` **before** the player's inventory is cleared.
- Participants race with an empty inventory. Item pickups are disabled for active participants so dropped pearls, elytra, or other items cannot be picked up during the race.
- At `/rbevent end`, online participants have their original inventory restored and are returned to their previous locations.
- If a participant disconnects during the race, their backup remains on disk. If they reconnect while the same event is still active, their race inventory remains empty. If the server restarts, the in-memory event ends and the saved original inventory is restored on join/startup.
- Leaving before the race starts does not change the player's inventory. If a participant leaves during the race, their original inventory is restored.
- If the backup cannot be serialized or saved, the player's inventory is not cleared and the player is removed from the race.

## Test checklist

1. Back up the existing mod JAR and world before testing.
2. Build with Java 25 and the project's Gradle wrapper: `gradlew.bat clean build`.
3. Join the event carrying a distinctive set of items: renamed tools, enchanted armor, offhand item, and several stack sizes.
4. Start the race and confirm the inventory is empty at Tower 1.
5. Confirm an active participant cannot pick up dropped items.
6. Finish or end the event using `/rbevent end`; verify every original item, slot, enchantment, count, durability, armor piece, and offhand item is restored.
7. Test disconnect/reconnect during a race, then test a server restart with a race inventory backup present. Confirm the original inventory is restored without duplicating items.
8. Test a participant leaving before the race starts; their inventory should remain unchanged.

## Safety note

Do not install this on the live server until the build and inventory round-trip tests pass on a test copy. The backup file is intentionally retained if decoding or restoration fails; do not delete `runeborn-event-inventory-backups.json` while it contains entries.
