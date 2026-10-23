# Runeborn Claims v0.9.1

Fabric server-side mod for Runeborn: Origins. Integrates LuckPerms with Open Parties and Claims and provides playtime-based claim progression.

## Tier progression

| Tier | Starting claims | Gain | Maximum |
|---|---:|---:|---:|
| Runeborn | 4 | +1 per active hour | 20 |
| Wayfarer | 8 | +1 per active hour | 40 |
| Rune Keeper | 12 | +1 per active hour | 60 |
| Ascendant | 16 | +1 per active hour | 80 |
| Admin | 500 | — | 500 |

## v0.9.1 features

- Persistent per-player playtime.
- Active-playtime tracking using movement/rotation activity.
- Configurable AFK timeout (default 5 minutes).
- Claim allowance increases only after completed active hours.
- Automatic LuckPerms transient `xaero.pac_max_claims` update for OPAC.
- `/rbclaims` now shows tier, playtime, current/max claims, and time to the next claim.
- `/rbclaims set <player> <time>` for staff testing.
- `/rbclaims reload` to reload the Runeborn config.

## Economy and Jobs

The v0.2 AFK detector is intentionally conservative and API-stable for Minecraft 26.2: movement or camera rotation resets the AFK timer. Interaction-event hooks can be added later if we want more aggressive activity detection.

## Configuration

After first startup, the config is generated at:

`<world>/config/runeborn-claims/config.json`

Default values:

- `afkTimeoutSeconds`: 300
- `movementThresholdBlocks`: 0.10
- `lookThresholdDegrees`: 3.0
- `saveIntervalSeconds`: 60

## Build

Use Java 25 and the Gradle wrapper:

```powershell
.\gradlew.bat build
```

The output JAR is in `build/libs/`.
