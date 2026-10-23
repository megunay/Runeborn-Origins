Runeborn Claims v0.9.0

Minecraft 26.2 / Fabric

New in v0.9.0:
- Persistent Runeborn economy balances in world/data/runeborn-economy.json
- /rbbalance
- /rbbalance player <player> (admin)
- /rbpay <player> <amount>
- /rbeconomy set <player> <amount> (admin)
- /rbeconomy add <player> <amount> (admin)
- /rbeconomy take <player> <amount> (admin)
- Currency is Crowns.
- Starting balance is 0.
- Economy is intentionally independent from job rewards for now; job income will be added after the wallet/transaction foundation is tested.

Build:
  .\\gradlew.bat clean build

Output:
  build\\libs\\runeborn-claims-0.9.0.jar
