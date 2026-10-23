# Runeborn checkpoint recovery test

## Recovery behavior

During an active tower race, if a participant falls below Y=110:

- If they have reached a valid checkpoint, they are teleported to their latest checkpoint.
- If they have not reached any checkpoint, they are teleported to the Tower 1 starting location.
- A chat message tells the participant which recovery path was used.

The threshold is intentionally Y=110 because the parkour is built high above the ground and water can prevent players from reaching the vanilla void boundary.

## Test on a private/test server

1. Back up the currently working mod JAR and event configuration.
2. Build the mod with `./gradlew clean build` (Windows: `gradlew.bat clean build`).
3. Install the newly built JAR on the server and restart it.
4. Start the event and fall below Y=110 before reaching the first checkpoint. Confirm you return to Tower 1 and see the start-recovery message.
5. Reach the first checkpoint, then fall below Y=110. Confirm you return to that checkpoint and see the checkpoint-recovery message.
6. Confirm the central Runestone still advances the tower race as before.

This recovery check runs while the tower-race event is active and only applies to registered participants.
