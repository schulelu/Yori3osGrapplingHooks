package com.yori3o.yo_hooks.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

/**
 * The mod's toolsmith trades (src/26.1-26.2/.../villager_trade/toolsmith): an iron hook at level 3, a diamond hook
 * at level 5, enchanted with 5 to 19 levels, priced base + levels like vanilla's enchanted-item trades.
 */
final class Trades implements Listener {

    private final YoHooks plugin;

    Trades(YoHooks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    void onTrade(VillagerAcquireTradeEvent e) {
        Settings cfg = plugin.settings();
        if (!cfg.trades || !(e.getEntity() instanceof Villager v) || !Villager.Profession.TOOLSMITH.equals(v.getProfession())) {
            return;
        }
        int level = v.getVillagerLevel();
        if (level != 3 && level != 5) {
            return;
        }
        // The event fires once per new trade; decide once per level
        NamespacedKey decided = new NamespacedKey(plugin, "trade_level_" + level);
        if (v.getPersistentDataContainer().has(decided)) {
            return;
        }
        v.getPersistentDataContainer().set(decided, PersistentDataType.BYTE, (byte) 1);
        if (ThreadLocalRandom.current().nextDouble() >= cfg.tradeChance) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> add(v, level));
    }

    private void add(Villager v, int level) {
        if (!v.isValid()) {
            return;
        }
        Tier tier = level == 3 ? Tier.IRON : Tier.DIAMOND;
        int levels = ThreadLocalRandom.current().nextInt(5, 20);
        ItemStack hook = plugin.loot().enchantWithLevels(plugin.items().hook(tier), levels);
        if (hook.getEnchantments().isEmpty()) {
            return; // the mod discards trades whose hook came out unenchanted
        }
        plugin.items().refreshLore(hook);
        MerchantRecipe r = new MerchantRecipe(hook, 0, 3, true, level == 3 ? 10 : 30, 0.2f);
        r.addIngredient(new ItemStack(Material.EMERALD, Math.min(64, (level == 3 ? 3 : 13) + levels)));
        List<MerchantRecipe> recipes = new ArrayList<>(v.getRecipes());
        recipes.add(r);
        v.setRecipes(recipes);
    }
}
