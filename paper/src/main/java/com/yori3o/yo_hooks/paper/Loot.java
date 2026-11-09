package com.yori3o.yo_hooks.paper;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.set.RegistrySet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

/** Grappling hooks in vanilla chests, and the enchanting the mod's loot and trades use. */
final class Loot implements Listener {

    private final YoHooks plugin;

    Loot(YoHooks plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    void onLoot(LootGenerateEvent e) {
        if (!plugin.settings().loot) {
            return;
        }
        LootTables.Table table = LootTables.TABLES.get(e.getLootTable().getKey().getKey());
        if (table == null) {
            return;
        }
        LootTables.Entry entry = LootTables.roll(table, ThreadLocalRandom.current());
        if (entry != null) {
            e.getLoot().add(build(entry));
        }
    }

    ItemStack build(LootTables.Entry entry) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        ItemStack s = plugin.items().hook(entry.tier());
        Integer max = s.getData(DataComponentTypes.MAX_DAMAGE);
        if (entry.keepMax() < 1 && max != null) {
            double keep = entry.keepMin() + rnd.nextDouble() * (entry.keepMax() - entry.keepMin());
            s.setData(DataComponentTypes.DAMAGE, LootTables.damage(keep, max));
        }
        switch (entry.enchanting()) {
            case RANDOM -> {
                if (rnd.nextDouble() < entry.enchantChance()) {
                    enchantRandomly(s);
                }
            }
            case LEVELS -> s = enchantWithLevels(s, rnd.nextInt(entry.levelsMin(), entry.levelsMax() + 1));
            case NONE -> { }
        }
        plugin.items().refreshLore(s);
        return s;
    }

    /** enchant_randomly over #on_random_loot: one enchantment that fits a hook, at a random level. */
    void enchantRandomly(ItemStack s) {
        List<Enchantment> options = lootEnchantments();
        Enchantment pick = options.get(ThreadLocalRandom.current().nextInt(options.size()));
        s.addEnchantment(pick, ThreadLocalRandom.current().nextInt(1, pick.getMaxLevel() + 1));
    }

    /** enchant_with_levels over the non-treasure enchantments that fit a hook, using vanilla's enchanting. */
    ItemStack enchantWithLevels(ItemStack s, int levels) {
        return Bukkit.getItemFactory().enchantWithLevels(s, levels,
            RegistrySet.keySet(RegistryKey.ENCHANTMENT, EnchantmentKeys.UNBREAKING, Enchants.LONG_REACH, Enchants.GENTLE_TOUCH),
            new Random(ThreadLocalRandom.current().nextLong()));
    }

    private static List<Enchantment> lootEnchantments() {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        return List.of(registry.getOrThrow(EnchantmentKeys.UNBREAKING), registry.getOrThrow(Enchants.LONG_REACH),
            registry.getOrThrow(Enchants.GENTLE_TOUCH));
    }
}
