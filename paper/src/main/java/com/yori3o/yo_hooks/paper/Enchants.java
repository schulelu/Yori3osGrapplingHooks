package com.yori3o.yo_hooks.paper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.keys.ItemTypeKeys;
import io.papermc.paper.registry.keys.tags.EnchantmentTagKeys;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.jetbrains.annotations.Nullable;

/**
 * The mod's three enchantments (data/yo_hooks/enchantment/*.json), registered while the server bootstraps.
 * They apply to fishing rods, the hooks' base item; {@link Listeners} keeps them off plain fishing rods.
 */
public final class Enchants {

    public static final TypedKey<Enchantment> LONG_REACH = EnchantmentKeys.create(Key.key(HookItems.NS, "long_reach"));
    public static final TypedKey<Enchantment> AGILITY = EnchantmentKeys.create(Key.key(HookItems.NS, "agility"));
    public static final TypedKey<Enchantment> GENTLE_TOUCH = EnchantmentKeys.create(Key.key(HookItems.NS, "gentle_touch"));
    public static final List<TypedKey<Enchantment>> ALL = List.of(LONG_REACH, AGILITY, GENTLE_TOUCH);

    private @Nullable Enchantment longReach;
    private @Nullable Enchantment agility;
    private @Nullable Enchantment gentleTouch;

    static void bootstrap(BootstrapContext ctx) {
        ctx.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {
            RegistryKeySet<ItemType> rods = RegistrySet.keySet(RegistryKey.ITEM, ItemTypeKeys.FISHING_ROD);
            event.registry().register(LONG_REACH, b -> b
                .description(Component.translatable("enchantment.yo_hooks.long_reach", "Long Reach"))
                .supportedItems(rods).weight(3).maxLevel(4).anvilCost(4)
                .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(5, 15))
                .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(30, 15))
                .activeSlots(EquipmentSlotGroup.MAINHAND));
            event.registry().register(AGILITY, b -> b
                .description(Component.translatable("enchantment.yo_hooks.agility", "Agility"))
                .supportedItems(rods).weight(1).maxLevel(2).anvilCost(8)
                .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(29, 100))
                .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(59, 100))
                .activeSlots(EquipmentSlotGroup.MAINHAND));
            event.registry().register(GENTLE_TOUCH, b -> b
                .description(Component.translatable("enchantment.yo_hooks.gentle_touch", "Gentle Touch"))
                .supportedItems(rods).weight(1).maxLevel(1).anvilCost(6)
                .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(15, 0))
                .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(65, 0))
                .activeSlots(EquipmentSlotGroup.MAINHAND));
        }));
        // Same tag membership as the mod's data/minecraft/tags/enchantment/*.json
        ctx.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.ENCHANTMENT), event -> {
            var r = event.registrar();
            r.addToTag(EnchantmentTagKeys.IN_ENCHANTING_TABLE, List.of(AGILITY));
            r.addToTag(EnchantmentTagKeys.TREASURE, List.of(AGILITY));
            r.addToTag(EnchantmentTagKeys.NON_TREASURE, List.of(GENTLE_TOUCH, LONG_REACH));
            r.addToTag(EnchantmentTagKeys.TRADEABLE, ALL);
        });
    }

    void load() {
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        longReach = registry.get(LONG_REACH);
        agility = registry.get(AGILITY);
        gentleTouch = registry.get(GENTLE_TOUCH);
    }

    public int longReach(ItemStack s) {
        return level(s, longReach);
    }

    public int agility(ItemStack s) {
        return level(s, agility);
    }

    public boolean gentleTouch(ItemStack s) {
        return level(s, gentleTouch) > 0;
    }

    public boolean isOurs(Enchantment e) {
        return e.equals(longReach) || e.equals(agility) || e.equals(gentleTouch);
    }

    public @Nullable Enchantment longReachEnchantment() {
        return longReach;
    }

    private static int level(@Nullable ItemStack s, @Nullable Enchantment e) {
        return s == null || e == null ? 0 : s.getEnchantmentLevel(e);
    }
}
