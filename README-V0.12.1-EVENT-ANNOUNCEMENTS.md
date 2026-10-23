# Runeborn v0.12.1 — Configurable Event Announcements

Built on the tested v0.12.0 project. This update adds configurable announcement templates and per-announcement toggles while preserving the market, Crowns, rewards, inventory isolation, checkpoints, recovery, mob cleanup, and leaderboard implementation.

## What is configurable

Settings are stored inside `world/data/runeborn-events.json` under the `announcements` object. The settings are created with defaults when the event configuration is next saved.

- `enabled`: master switch for the configurable announcement types.
- `countdownEnabled`, `startedEnabled`, `finishEnabled`, `endedEnabled`, `cancelledEnabled`: individual switches.
- `countdownSeconds`: countdown seconds to announce, default `[5,4,3,2,1]`.
- `countdownMessage`: supports `%event%`, `%seconds%`, `%participants%`.
- `startedMessage`: supports `%event%`, `%participants%`.
- `finishMessage`: supports `%event%`, `%player%`, `%time%`.
- `endedMessage`: supports `%event%`, `%returned%`.
- `cancelledMessage`: supports `%event%`.

Example:

```json
"announcements": {
  "enabled": true,
  "countdownEnabled": true,
  "startedEnabled": true,
  "finishEnabled": true,
  "endedEnabled": true,
  "cancelledEnabled": true,
  "countdownSeconds": [10, 5, 3, 2, 1],
  "countdownMessage": "%event% starts in %seconds% seconds! %participants% players are ready.",
  "startedMessage": "%event% has begun! Good luck to all %participants% participants.",
  "finishMessage": "%player% completed %event% in %time%!",
  "endedMessage": "%event% has ended. Returned %returned% players.",
  "cancelledMessage": "%event% was cancelled."
}
```

Keep the rest of `runeborn-events.json` intact when editing it. Stop the server before editing the file, then restart the server so the configuration is reloaded. Other event messages (such as joins, leaves, checkpoint and stage updates) retain their existing behavior.

## Build

Use the project's Java 25 environment:

```powershell
.\gradlew.bat clean build
```

The packaging environment uses Java 21, so this source has not been compiled here. Build and test on the development machine before installing it on the live server.
