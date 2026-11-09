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
| Let go, without the boost | **Shift**, in the air |

The keys can be changed in `config.yml`, and the mouse wheel can climb instead (`scroll-to-climb`). While a hook
is out, the action bar shows the rope length, the range and these keys as you bound them.

- **Throwing past the range**: when the hook reaches the end of its range without hitting anything, the rope
  catches it and it swings down, grabbing the first surface it lands on, such as a floor or roof just out of
  reach. If it finds nothing within two seconds, it comes back.
- **Hanging still**: after half a second of hanging still on a tight rope, the server holds you in place. You
  don't bob, whatever your ping, and W/S climb smoothly. Other players see you sitting in the rope as in a
  harness. A/D (or any movement key that isn't a rope control), Space, Shift or getting hit puts you back into
  the normal swing.

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
  - Copper, iron and gold hooks and heads smelt back into nuggets, also when damaged or enchanted.
  - Hooks don't burn as furnace fuel, unlike vanilla fishing rods.
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
  - the config screen (use `config.yml` and `/yohooks reload`).
- **Additions** the mod doesn't have: catching the hook at the end of its range, the seat when hanging still,
  letting go with Shift and the action bar. Each can be switched off in `config.yml` (`range-catch`,
  `seat-when-still`, `controls.let-go: none`, `action-bar`).

Like the mod, the plugin tells the server's anti-fly check that a player on a rope isn't flying, so hanging still
never gets anyone kicked.

## Admin

- `/yohooks give <player> <item> [count]`. Items: `<tier>_grappling_hook` and `<tier>_hook_head`, with tier being
  copper, iron, gold, diamond or netherite.
- `/yohooks reload`.
- Permission `yohooks.admin` (operators by default).
- `config.yml` mirrors the mod's settings: climb speed, soft rope, hunger cost, fragile blocks, a block
  black/whitelist, per-tier range, durability and damage, controls, the resource pack, loot and trades. It also
  switches the additions above on and off.

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
