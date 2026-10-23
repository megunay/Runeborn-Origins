# Runeborn Parkour Rewards — Test Guide

This update adds Crowns rewards to the existing four-tower race. It keeps the existing checkpoint recovery and central Runestone progression.

## Reward amounts

- Every participant who completes all four trials: **100 Crowns**.
- 1st place: **+250 Crowns** placement bonus.
- 2nd place: **+150 Crowns** placement bonus.
- 3rd place: **+75 Crowns** placement bonus.

The completion reward is paid when the player finishes. Placement bonuses are distributed when every registered participant finishes, or when an admin ends the event early. If players finish on the same server tick, they share the same placement and bonus; subsequent placement numbers follow competition ranking.

Players who do not finish receive no completion or placement reward. Existing Crowns balances are used, and the economy file is saved after rewards are granted.

## Build and install

1. Back up the currently working Runeborn mod JAR and server world.
2. Open this project with JDK 25 and run `gradlew.bat clean build`.
3. Confirm the build succeeds before installing anything.
4. Install the newly built JAR on a test server first.

## Test checklist

- [ ] Start an event with one participant; complete all four towers and return to the central Runestone. Confirm +100 Crowns and a saved balance.
- [ ] Start an event with at least three participants; finish them in a known order. Confirm placement bonuses of +250, +150, and +75 Crowns.
- [ ] Confirm players who do not finish receive no reward.
- [ ] End an event early after one or more players finish. Confirm eligible finishers receive placement bonuses and each bonus is paid only once.
- [ ] Check `/rbeconomy` or the balance command, then restart the server and verify balances persist.
- [ ] Confirm checkpoint activation, Y=110 recovery, and central Runestone progression still work.
