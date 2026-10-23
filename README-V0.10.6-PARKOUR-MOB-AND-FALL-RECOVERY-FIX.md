Runeborn v0.10.6 - Parkour Mob Cleanup and Safe Recovery

Changes
- Clears hostile mobs inside the parkour course bounds when the race starts.
- Repeats the cleanup once per second while the tower race is active, so newly spawned hostile mobs are removed.
- Cleanup is limited to the event dimension and the course bounds (X 840..950, Y 80..240, Z 310..420). It targets hostile Monster entities, not players, passive animals, villagers, or entities outside that box.
- Successful event teleports now reset fall distance and zero player velocity, preventing the previous fall from killing a player after checkpoint recovery.
- Preserves inventory isolation, rewards, checkpoint ordering, Y=110 recovery, and central Runestone progression.

Build and test
1. Back up the working v0.10.5 JAR and world.
2. Extract this archive into a separate project directory.
3. Run: .\gradlew.bat clean build
4. Test on a private server before replacing the live JAR.
5. Spawn a hostile mob inside the course bounds before starting an event; confirm it is removed when the event starts. Spawn another during the event; confirm it is removed within about one second.
6. Fall below Y=110 with and without a checkpoint. Confirm the player teleports to the right location and does not die from residual fall damage.
7. Confirm checkpoint progression, tower transitions, rewards, and inventory restoration still work.

Note: The source was patched but not compiled in the generation environment, which does not have Java 25 installed. Run the build locally and share any compile errors before installing.
