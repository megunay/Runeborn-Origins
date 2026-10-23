# Runeborn v0.10.0 — Event Management Foundation

This is a server-side event-management foundation for the Runeborn Fabric 26.2 mod. It does not yet implement minigame-specific winner detection or Crown rewards.

## Commands

- `/rbevent` or `/rbevent status` — show configured event, state, participant count, and whether lobby/arena are set.
- `/rbevent create <name>` — OP/admin creates or replaces the configured event (only when no event participants/session are active).
- `/rbevent setlobby` — save the admin's current location and dimension as the lobby.
- `/rbevent setarena` — save the admin's current location and dimension as the arena.
- `/rbevent join` — register and teleport to the lobby.
- `/rbevent leave` — leave and return to the saved pre-event location.
- `/rbevent start` — OP/admin starts a 10-second countdown; online participants are moved to the arena at the end.
- `/rbevent end` — OP/admin ends the event and returns online participants to their pre-event locations.

Administrative commands use the Minecraft command permission level used by the rest of Runeborn's admin commands. Players do not need a client-side mod.

## Data and limitations

- Event name, lobby, arena, dimension, coordinates, and rotation are saved in `world/data/runeborn-events.json`.
- The active participant list and each participant's return location are kept in memory only.
- Do not restart the server or dimension/world setup during a live event in this first version. If the server stops during an event, participant return locations are not recovered automatically.
- This version is event administration only. It does not yet detect a winner, implement parkour checkpoints, isolate an arena, or award Crowns.
- One event configuration is supported at a time.

## Build

Use the project's Java/Gradle setup and run:

```powershell
.\gradlew.bat clean build
```

Do not replace the live server JAR until the build succeeds and the in-game test plan passes.

## Suggested test sequence

1. Build with `clean build`.
2. Start a backup/test server and verify `/rbevent status` works before configuration.
3. As OP, run `/rbevent create Parkour`.
4. Stand at the desired lobby and run `/rbevent setlobby`.
5. Stand at the desired arena and run `/rbevent setarena`.
6. Restart the test server and verify `/rbevent status` retains the name and both locations.
7. As a non-OP, verify `create`, `setlobby`, `setarena`, `start`, and `end` are unavailable.
8. Have two test players use `/rbevent join`; verify both arrive in the lobby and the participant count is correct.
9. Have one player use `/rbevent leave`; verify they return to their original dimension/location.
10. As OP, start the event; verify the countdown and arena teleport.
11. End the event; verify both remaining participants return to their original locations and event state resets.
12. Try creating another event after everyone has left; verify the configuration can be replaced.


## v0.10.1 Parkour checkpoints

- `/rbevent checkpoint add` saves the block you are standing in as the next checkpoint.
- `/rbevent checkpoint list` displays configured checkpoints.
- `/rbevent checkpoint clear` removes all checkpoints.
- `/rbevent finish set` saves the finish block. `/rbevent finish clear` removes it.
- If any parkour configuration is set, events require at least one checkpoint and a finish location before starting. Events without parkour configuration retain the original generic event behavior.
- Participants must touch every checkpoint in order before the finish counts.
- Reaching the finish early does not finish the run. Finish times and finish announcements are recorded in memory for the current event.
- Checkpoints/finish are saved in `world/data/runeborn-events.json`.
- This release does not yet award Crowns or store persistent leaderboards.
