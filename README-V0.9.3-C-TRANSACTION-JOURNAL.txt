Runeborn v0.9.3-C — Marketplace Transaction Journal
Minecraft 26.2 / Fabric

Changes:
- Adds world/data/runeborn-market-transaction.json as a recovery journal for marketplace purchases.
- A purchase writes the intended post-transaction marketplace and economy snapshots to the journal before replacing either live data file.
- Economy and marketplace files are then written atomically from the journal snapshots.
- The journal is deleted only after both data files have been written successfully.
- On startup, if a journal remains, the mod rolls forward both snapshots before loading the economy and market data.
- Existing itemData serialization, older listing compatibility, inventory capacity checks, OPAC claim restriction, RTP, jobs, and other systems are retained.

Important limitation:
This journal coordinates the marketplace/economy JSON files. Minecraft player inventory data is saved separately, so this does not guarantee perfect recovery from a crash at every instant between item insertion and journal creation. Do not describe this as fully crash-proof. A durable cross-resource transaction would require a stronger design that accounts for player inventory persistence too.

Build on Windows:
  .\gradlew.bat clean build

After build, test:
1. Normal purchase: buyer receives item, seller gets Crowns, listing disappears.
2. Restart after a completed purchase: listing does not return and balances persist.
3. Inspect world/data/ for runeborn-market-transaction.json after normal operation; it should normally be absent.
4. Backup the world before any manual journal-recovery test. Do not hand-edit the journal on a live server.
