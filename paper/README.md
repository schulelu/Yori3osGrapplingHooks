# YoHooks: Yori3o's Grappling Hooks for Paper

A Paper server plugin that brings [Yori3o's Grappling Hooks](https://github.com/yori3o/Yori3osGrapplingHooks) to
servers whose players use an unmodified client. It uses the mod's items, recipes, enchantments, loot, trades and
rope physics. Paper can't run client code, so the server computes the rope and sends the movement to the player.
Players need nothing installed. An optional resource pack gives them the mod's own textures and sounds.

Target: **Paper 26.2** (built against Paper API 26.2 build 135), Java 25.

## Playing

| Action | Key |
|---|---|
| Throw the hook, or pull it back | right-click with a grappling hook |
| Climb up the rope | hold **W** |
| Climb down | hold **S** |
| Jump off with a boost | **Space** (the hook must not be far below you) |

The keys can be changed in `config.yml`, and the mouse wheel can climb instead (`scroll-to-climb`).

- **Hooks**: copper (range 10), iron (12), gold (17), diamond (21) and netherite (26). They lose durability on
  each hit, can be repaired with their material in an anvil, and take Unbreaking and Mending.
- **Enchantments**:
  - *Long Reach* I–IV: + 3/7/10/14 blocks of range.
  - *Agility* I–II: faster throw, climbing and jump.
  - *Gentle Touch*: the hook no longer breaks fragile blocks.
- **Fragile blocks**: glass, panes, ice, amethyst, dripstone, candles, pots, eggs, lily pads, bamboo, sea lanterns,
  glowstone, end rods and similar break when hit. Unsupported sand and gravel fall. Protected regions
  (WorldGuard and other plugins that cancel block breaking) are respected.
- **Recipes**:
  - Hook head: ingots plus a nugget (copper, iron, gold), or three diamonds.
  - Hook: hook head, iron chain and stick, diagonally.
  - Netherite: upgrade the diamond hook or the diamond head at a smithing table.
  - Copper, iron and gold heads smelt back into nuggets.
- **Finding hooks**: extra loot in mineshafts, dungeons, temples, ruined portals, bastions, end cities, trial
  chamber barrels, village weaponsmiths and the bonus chest. Toolsmiths may sell enchanted iron (level 3) and
  diamond (level 5) hooks.

## Without the resource pack

The plugin offers the pack when a player joins. Accepting it shows the mod's hook textures and plays its sounds.
Declining is fine:

- Hooks look like fishing rods and hook heads like tripwire hooks, both with their own names.
- The rope is an iron chain.
- Sounds are vanilla.

## Differences from the mod

- **Movement**: the swing is calculated on the server, so with a high ping it feels a little less direct than the
  client mod.
- **The rope** is a chain of display entities, not the mod's renderer. It starts at the player's hands as the
  server sees them, so it can trail slightly during fast swings.
- **Climbing** uses vanilla keys (W/S) instead of the mod's extra keybinds.
- **Not ported**:
  - VR (Vivecraft) support;
  - the "distance on hook" statistic;
  - the "Failed Hook" advancement;
  - the creative tab (use `/yohooks give`);
  - the config screen (use `config.yml` and `/yohooks reload`);
  - smelting whole hooks back to nuggets (heads still smelt).
- **Hanging still** looks like flying to the server's anti-fly check. The plugin forgives that kick while a hook
  is out and for three seconds after.

## Admin

- `/yohooks give <player> <item> [count]`. Items: `<tier>_grappling_hook` and `<tier>_hook_head`, with tier being
  copper, iron, gold, diamond or netherite.
- `/yohooks reload`.
- Permission `yohooks.admin` (operators by default).
- `config.yml` mirrors the mod's settings: climb speed, soft rope, hunger cost, fragile blocks, a block
  black/whitelist, per-tier range, durability and damage, controls, the resource pack, loot and trades.

## Building

From the repository root:

```sh
sh gradlew -p paper build         # jar: paper/build/libs, resource pack: paper/build/pack
sh gradlew -p paper test          # formula tests against the mod's numbers
sh gradlew -p paper runServer     # a local Paper 26.2 test server with the plugin
```

The build zips the resource pack from the mod's own assets (`src/main/resources/assets/yo_hooks`,
`src/modern/resources/assets/yo_hooks`). It writes the pack's release URL and sha1 into the jar. Tag
`paper-v<version>` to publish both as release assets.

## License

MIT, like the mod: Copyright (c) 2026 yori3o, Paper port Copyright (c) 2026 Schuler Lukas. See [LICENSE](../LICENSE).
