package com.yori3o.yo_hooks.paper;

import java.util.List;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * YoHooks: a Paper port of Yori3o's Grappling Hooks (MIT, Copyright (c) 2026 yori3o).
 * Same items, recipes, enchantments, loot, trades and rope physics, computed on the server; see paper/README.md.
 */
public final class YoHooks extends JavaPlugin {

    /** Scoreboard tag on the plugin's display entities, to clean up leftovers. */
    static final String ENTITY_TAG = "yo_hooks";

    private Settings settings;
    private Enchants enchants;
    private HookItems items;
    private Pack pack;
    private Sounds sounds;
    private HookManager hooks;
    private Recipes recipes;
    private Loot loot;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig(), getLogger());
        enchants = new Enchants();
        enchants.load();
        items = new HookItems(this);
        pack = new Pack(this);
        pack.load();
        sounds = new Sounds(pack);
        hooks = new HookManager(this);
        recipes = new Recipes(this);
        recipes.register();
        loot = new Loot(this);
        removeLeftovers();

        var pm = getServer().getPluginManager();
        pm.registerEvents(pack, this);
        pm.registerEvents(new Listeners(this), this);
        pm.registerEvents(loot, this);
        pm.registerEvents(new Trades(this), this);
        getServer().getScheduler().runTaskTimer(this, hooks, 1, 1);
        registerCommand("yohooks", "Grappling hooks: give hooks and heads, reload the config", List.of(), new YoHooksCommand(this));
        for (Player p : getServer().getOnlinePlayers()) { // after /reload or a plugin manager
            hooks.clearAll(p);
            p.discoverRecipes(recipes.keys());
        }
    }

    @Override
    public void onDisable() {
        if (hooks != null) {
            hooks.discardAll();
        }
        if (recipes != null) {
            recipes.unregister();
        }
    }

    void reload() {
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        pack.load();
    }

    private void removeLeftovers() {
        for (World w : getServer().getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(ENTITY_TAG)) {
                    e.remove();
                }
            }
        }
    }

    Settings settings() {
        return settings;
    }

    Enchants enchants() {
        return enchants;
    }

    HookItems items() {
        return items;
    }

    Sounds sounds() {
        return sounds;
    }

    HookManager hooks() {
        return hooks;
    }

    Recipes recipes() {
        return recipes;
    }

    Loot loot() {
        return loot;
    }
}
