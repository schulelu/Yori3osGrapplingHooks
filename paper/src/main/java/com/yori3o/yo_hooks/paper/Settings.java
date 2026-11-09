package com.yori3o.yo_hooks.paper;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Input;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** config.yml, read once on enable and on /yohooks reload. Names and defaults follow the mod's config files. */
public final class Settings {

    /** A vanilla input a rope control can be bound to. */
    public enum Control {
        FORWARD, BACKWARD, LEFT, RIGHT, JUMP, SNEAK, SPRINT, NONE;

        public boolean held(Input input) {
            return switch (this) {
                case FORWARD -> input.isForward();
                case BACKWARD -> input.isBackward();
                case LEFT -> input.isLeft();
                case RIGHT -> input.isRight();
                case JUMP -> input.isJump();
                case SNEAK -> input.isSneak();
                case SPRINT -> input.isSprint();
                case NONE -> false;
            };
        }
    }

    public final double climbSpeed;
    public final boolean softHook;
    public final double stiffness;
    public final boolean funnyMode;
    public final double decreaseSatiety;
    public final boolean breakingFragileBlocks;
    public final Set<Material> blockList;
    public final boolean whitelistMode;
    public final Map<Tier, int[]> tiers; // range, durability, damage
    public final Control climbUp;
    public final Control climbDown;
    public final Control jumpOff;
    public final boolean scrollToClimb;
    public final boolean packEnabled;
    public final String packUrl;
    public final String packSha1;
    public final String packPrompt;
    public final boolean loot;
    public final boolean trades;
    public final double tradeChance;

    public Settings(FileConfiguration c, Logger log) {
        climbSpeed = clamp(c.getDouble("climb-speed", 0.115), 0, 1);
        softHook = c.getBoolean("soft-hook", false);
        stiffness = clamp(c.getDouble("stiffness", 0.1), 0.1, 1);
        funnyMode = c.getBoolean("funny-mode", false);
        decreaseSatiety = clamp(c.getDouble("decrease-satiety", 1.1), 0, 3);
        breakingFragileBlocks = c.getBoolean("breaking-fragile-blocks", true);
        whitelistMode = c.getBoolean("whitelist-mode", false);
        blockList = new HashSet<>();
        for (String id : c.getStringList("blocks-blacklist")) {
            Material m = Material.matchMaterial(id);
            if (m == null || !m.isBlock()) {
                log.warning("blocks-blacklist: unknown block " + id);
            } else {
                blockList.add(m);
            }
        }
        tiers = new EnumMap<>(Tier.class);
        for (Tier t : Tier.values()) {
            ConfigurationSection s = c.getConfigurationSection("tiers." + t.id);
            int range = s == null ? t.range : (int) clamp(s.getInt("range", t.range), 1, 99);
            int durability = s == null ? t.durability : (int) clamp(s.getInt("durability", t.durability), -1, 960);
            int damage = s == null ? t.damage : (int) clamp(s.getInt("damage", t.damage), 0, 100);
            tiers.put(t, new int[] {range, durability, damage});
        }
        climbUp = control(c, "controls.climb-up", Control.FORWARD, log);
        climbDown = control(c, "controls.climb-down", Control.BACKWARD, log);
        jumpOff = control(c, "controls.jump-off", Control.JUMP, log);
        scrollToClimb = c.getBoolean("controls.scroll-to-climb", false);
        packEnabled = c.getBoolean("resource-pack.enabled", true);
        packUrl = c.getString("resource-pack.url", "").trim();
        packSha1 = c.getString("resource-pack.sha1", "").trim().toLowerCase(Locale.ROOT);
        packPrompt = c.getString("resource-pack.prompt", "");
        loot = c.getBoolean("loot", true);
        trades = c.getBoolean("trades.enabled", true);
        tradeChance = clamp(c.getDouble("trades.chance", 0.4), 0, 1);
    }

    public int range(Tier t) {
        return tiers.get(t)[0];
    }

    public int durability(Tier t) {
        return tiers.get(t)[1];
    }

    public int damage(Tier t) {
        return tiers.get(t)[2];
    }

    /** The hook can't attach to listed blocks (or only to listed ones in whitelist mode). */
    public boolean refuses(Material block) {
        return blockList.contains(block) != whitelistMode;
    }

    private static Control control(FileConfiguration c, String path, Control def, Logger log) {
        String v = c.getString(path, def.name()).trim().toUpperCase(Locale.ROOT);
        try {
            return Control.valueOf(v);
        } catch (IllegalArgumentException e) {
            log.warning(path + ": unknown input '" + v + "' (forward, backward, left, right, jump, sneak, sprint, none)");
            return def;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
