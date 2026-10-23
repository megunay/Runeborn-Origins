# Runeborn v0.14.3 — Socket Mapping Fix

- Removes the unavailable `ItemTags` import.
- Determines socket eligibility from the registered vanilla item identifier.
- Swords and axes: 2 sockets.
- Helmets, chestplates, leggings, and boots: 1 socket.
- Other items: 0 sockets.
- Keeps `/rbsocket` and the existing socket flow in place.

Build locally with `./gradlew clean build` (Windows: `gradlew.bat clean build`) before installing.
