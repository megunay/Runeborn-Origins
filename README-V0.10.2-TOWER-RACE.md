# Runeborn Claims v0.10.2 — Tower Race Source Patch

This patch adds the sequential four-tower race to the existing event foundation.

## Race flow
- Players join and are teleported to the configured event lobby (`/rbevent setlobby`).
- `/rbevent start` begins a 5-second countdown while players remain in the lobby.
- At zero, players are individually teleported to Tower 1 and the race timer begins.
- Reaching the central Runestone advances that player to the next tower.
- Returning to the center after Tower 4 marks that player finished and announces their elapsed time.
- `/rbevent end` returns registered participants to their saved pre-event locations.

## Verified landing locations supplied by the server owner
- Tower 1: `876, 138, 386`
- Tower 2: `916, 138, 345`
- Tower 3: `876, 138, 345`
- Tower 4: `916, 138, 385`
- Central Runestone trigger: `896, 142, 365`

Tower coordinates use the dimension configured for the event lobby. Ensure the lobby is in the same dimension as the course. The existing `/rbevent setarena` setting is still required by the current join/start readiness checks, but the tower race teleports to the configured tower coordinates instead of the arena.

## Build and test
1. Build with the same Java/Gradle environment used for the current mod.
2. Back up the current JAR and world before testing.
3. Install the newly built JAR on a test server first.
4. Create/configure an event, set the lobby and arena, then have two test players run `/rbevent join`.
5. Start the event and verify they remain in the lobby for five seconds, then teleport to Tower 1.
6. Have one player reach the center and verify only that player advances to Tower 2; repeat through Tower 4.
7. Return to the center after Tower 4 and verify the finish time announcement.
8. Run `/rbevent end` and verify all online participants return to their original locations.

## Important
This is a source patch, not a verified compiled JAR. Build/runtime behavior still needs to be tested in the user's Minecraft/Fabric environment. No server data or progression files are changed by this source archive.
