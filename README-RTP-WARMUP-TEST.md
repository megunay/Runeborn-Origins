# Runeborn v0.9.2 — RTP Warmup Test

This build applies the same RTP rules to both `/rtp` and `/randomteleport` because Essential Commands registers both literals to `RandomTeleportCommand`.

## Sequence
1. Check Essential Commands RTP cooldown first.
2. If on cooldown, show the cooldown message and stop immediately.
3. If not on cooldown, start a 5-second warmup.
4. Player must remain still; movement cancels the warmup.
5. After 5 seconds, the original Essential Commands RTP command is executed.
6. Essential Commands records its normal RTP cooldown when the command proceeds.

The warmup is RTP-specific and does not affect `/tpa`, `/tpaccept`, homes, spawn, etc.

## Important
The source was verified against the uploaded Essential Commands 0.41.0 mc26.2 JAR. The build could not be executed in this Linux environment because the Gradle 9.8 distribution is not cached and external network access is unavailable. Build it on the normal Windows development machine with:

    gradlew.bat clean build

Then install the generated Runeborn Claims JAR and test both `/rtp` and `/randomteleport`.
