# Runeborn v0.12.0 — Persistent Parkour Leaderboards

Built on the v0.11.3 market-category UI source. This update adds persistent parkour records without replacing the system market, player-to-player market, Crowns economy, race rewards, inventory isolation, checkpoints, mob cleanup, or Y=110 recovery.

## Commands

- `/rbtop` — top 10 personal best finish times for the active season.
- `/rbtop me` — your personal best, rank, completion count, and date.
- `/rbtop season` — shows the active season.
- `/rbtop season set <name>` — operator/admin command to select or create a season. Existing season data is retained.

## Records

- Stored by player UUID, so changing a Minecraft name does not create a new player record.
- Tracks best elapsed time in ticks, the date of that best, last completion date, event name, and total completed runs.
- Records are written after each valid finish to `world/data/runeborn-parkour-leaderboards.json`.
- Records are separated by season. Changing the active season does not erase previous season records.
- Both the tower-race finish and the legacy configured checkpoint-course finish write records.

## Build and test

Run on the development machine using the project's configured Java 25 and Gradle 9.8.0:

```powershell
.\gradlew.bat clean build
```

Then test on a staging server:

1. Finish a valid race and verify a personal-best message appears.
2. Run `/rbtop` and `/rbtop me`.
3. Restart the server and verify records remain.
4. Use `/rbtop season set Season 2`, complete a run, and verify Season 1 records remain available by switching back with `/rbtop season set Season 1`.
5. Verify an invalid/incomplete run does not create a record.

The source ZIP was not compiled in the packaging environment because it could not reach services.gradle.org to download Gradle 9.8.0. Build and test before deploying to the live server.
