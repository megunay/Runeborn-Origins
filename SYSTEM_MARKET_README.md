# Runeborn System Market (v0.11.0)

This update adds a server-side inventory GUI shop alongside the existing player-to-player `/rbmarket` listings. Players do not need a client-side mod for the shop.

## Commands

- `/rbshop` — open the system shop.
- `/rbshop prices` — show price/configuration help.
- `/rbshop reload` — reload the shop catalogue (operator permission required).
- `/rbbalance` — check Crowns balance.
- `/rbmarket` — existing player-to-player market remains unchanged.

## GUI controls

- Left-click an item: buy 1.
- Right-click an item: sell 1 from the main 36 inventory slots.
- Shift-left-click an item: buy up to 16 (limited by the item's stack size).
- Shift-right-click an item: sell up to 16 from the main inventory.
- Click category icons to filter; use the arrows to change pages.
- Equipped armor and offhand items are not sold by the system shop.

## Prices and configuration

The first server launch creates `world/config/runeborn-claims/system-market.json` with a starter catalogue and editable prices. Edit the JSON and run `/rbshop reload`. A price of `0` disables that direction (buy or sell). Set `enabled` to `false` to hide an item from the shop. Valid categories are `blocks`, `ores`, `farming`, `food`, `mob_drops`, and `utility`.

The starter prices are intentionally conservative placeholders, not a final economy balance. Most buy prices exceed sell prices to prevent simple buy/resell loops from creating Crowns. Test and adjust prices for your server before considering them final.

## Build

Use the same Java 25 / Gradle setup as the working v0.10.6 project and run:

```powershell
.\gradlew.bat clean build
```

Test on a copy of the server first. Back up the working JAR and the world `data` files before replacing anything.
