# Runeborn v0.11.3 — Market Category UI

## Changes
- `/rbshop` now opens a category-selection screen only. It no longer displays catalogue items beneath the category buttons.
- Clicking a category opens that category's item listings without the category buttons taking up the top row.
- The item view includes a Categories button (slot 47), All Items button (slot 48), and balance display (slot 49).
- Building blocks remain in the Building Blocks category and are excluded from All Items.
- Player Trades remains accessible from the category-selection screen and uses the existing player listing purchase flow.

## Build and test
1. Back up the currently working mod and project.
2. Open this project using the server's existing Java 25 and Gradle setup.
3. Run `gradlew.bat clean build`.
4. Test `/rbshop`: the initial screen should contain categories only.
5. Click Building Blocks: only block listings should appear, without category buttons in the top row.
6. Click All Items: non-block catalogue items should appear.
7. Click Categories to return to the category selection screen.
8. Test Player Trades and verify the existing buy flow remains functional.

This source update has not been compiled in the current environment. Verify the build and test on a staging server before deployment.
