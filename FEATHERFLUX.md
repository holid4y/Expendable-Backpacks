# Expendable Backpacks — FeatherFlux fork

Fork used on the FeatherFlux server (Paper 26.2, Java and Bedrock through Geyser). Upstream:
https://github.com/Shweit/Expendable-Backpacks (GPL-3.0). Changes live on the `featherflux` branch.

## What changed

- **Personal backpack** (`mochila` package): every player has one backpack locked in a hotbar slot
  (default slot 9). It cannot be dropped, moved, placed, put in containers or item frames, is never
  part of death drops, comes back after `/clear`, and stray or cloned copies are removed.
- **Capacity from OriginsPaper**: size follows `originspaper:max_level` (the highest level the player
  ever reached in any origin, published by OriginsPaper ≥ 1.5.0 in the player's own data) and never
  shrinks: 1→9, 3→18, 5→27, 7→36, 9→45, 10→54 slots.
- **Upgrade tree** (`/mochila arvore`, crouch + right-click): Coletor, Reabastecer, Bolso de Pesca
  (EvenMoreFish catch event), Saqueador (Lootin loot screen), Adega (BreweryX `Brew.age`), Toca-discos
  (JEXT discs, FeatherDiscos disc count). Plugins are reached at runtime, none is required.
- **On the back**: a non-persistent ItemDisplay showing the tier's head, linked to the player only on
  clients via SET_PASSENGERS (packetevents, optional), so teleports and seat plugins are untouched.
- **Storage**: backpacks.yml is written atomically, an unreadable file is set aside instead of being
  overwritten, and writes are batched (close, quit, 30 s autosave) instead of on every click.
- Crafting and placing backpacks as blocks are off by default (`recipes-enabled`, `placement-enabled`).
- Portuguese names and messages; bStats removed (it would report a modified build under upstream's id).

Tested with protocol bots on a Paper 26.2 sandbox with OriginsPaper, Lootin, EvenMoreFish, BreweryX,
FeatherDiscos, AxGraves, GSit and EssentialsX: `tests/client/sandbox-mochila.cjs` in
https://github.com/UnusualHatter/featherdiscos.
