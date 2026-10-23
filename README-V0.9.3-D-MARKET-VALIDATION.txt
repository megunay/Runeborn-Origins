Runeborn v0.9.3-D — Marketplace Validation and Listing Display

Baseline: v0.9.3-C transaction journal source. This patch is intended to preserve existing marketplace/economy/job/RTP/claim behavior while adding validation and a clearer market listing display.

Changes:
- `/rbmarket sell` accepts prices from 1 to 1,000,000,000 Crowns.
- Sell validation checks non-empty held item, positive quantity, quantity available in hand, and quantity no greater than that item's valid stack size.
- Refuses new listings when the listing ID counter is at its safe limit instead of risking integer overflow/reused IDs.
- Startup market-data loading skips entries with invalid IDs, quantities, prices, item IDs, or seller names; existing valid listings remain compatible.
- `/rbmarket list` shows active listing count and uses the item's display name (including custom names) when it can decode the ItemStack, alongside quantity, price, seller, and ID.
- Keeps existing owner-only cancellation with operator/admin override.

Build on Windows:
  .\gradlew.bat clean build

Test checklist:
1. Price 0 / negative / above 1,000,000,000 is rejected without consuming items.
2. Price 1 and price 1,000,000,000 are accepted when the player holds a valid item and has enough quantity.
3. Quantity 0 / negative / greater than held count is rejected without consuming items.
4. A quantity greater than the held item's maximum stack size is rejected.
5. Custom-named or enchanted item appears by display name in `/rbmarket list` and still buys/cancels with components intact.
6. A player cannot cancel another player's listing; an operator can.
7. Corrupt/invalid saved listing entries are skipped with a warning, while valid entries load.
8. Restart, buy, restart, and verify listing/balances as in v0.9.3-C.

Note: This source package is not claimed to be build-verified here. Build and test on the project's Windows/Gradle environment before installing on the live server. Back up the world before testing invalid saved-data handling.
