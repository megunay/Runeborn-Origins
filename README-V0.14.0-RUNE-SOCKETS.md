# Runeborn v0.14.1 — Equipment Sockets

## Command
- `/rbsocket` opens the Equipment Sockets GUI.
- Hold eligible equipment in the main hand and a genuine Runeborn Rune in the offhand. Click Insert Rune.

## Rules
- Swords and axes: up to 2 permanent sockets.
- Armor: 1 permanent socket.
- Only server-marked Runeborn Runes with recognized family/rarity and a Rune ID are accepted. Renaming an ordinary item does not make it valid.
- Rune is consumed only after validation and successful socket data write. Existing equipment components are preserved.
- Rune effects, removal, and Rune words are not part of this version.

## Test
Build with `./gradlew build` (Windows: `gradlew.bat build`). Test sword/axe 2-socket limit, armor 1-socket limit, invalid renamed Echo Shard rejection, reconnect/restart persistence, and existing Runeborn features.
