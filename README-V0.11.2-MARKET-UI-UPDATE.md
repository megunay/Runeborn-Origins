# Runeborn v0.11.2 — Market UI Update

## Changes
- Removed items in the `blocks` category from the `All Items` view. Players can browse building materials through the dedicated Building Blocks category.
- Added a `Player Trades` button to the system shop GUI.
- The Player Trades view shows active player listings with item, quantity, total price, seller, and listing ID.
- Left-click a listing to purchase it using the existing `/rbmarket` transaction flow. Existing checks for own listings, balance, inventory space, item-data preservation, and transaction persistence remain in effect.
- Existing `/rbmarket sell <price> <amount>`, `/rbmarket cancel <id>`, and `/rbmarket buy <id>` commands remain available.

## Build and test
Run `gradlew.bat clean build` with the project's supported Java runtime. Test the GUI on a staging server before replacing the live JAR.

1. Open `/rbshop`; verify blocks no longer appear in All Items.
2. Open Building Blocks; verify configured block items are still listed there.
3. Create a listing using `/rbmarket sell <price> <amount>` while holding an item.
4. Open `/rbshop`, click Player Trades, and buy the listing from another player.
5. Verify seller and buyer balances, received item data, listing removal, and persistence after restart.
