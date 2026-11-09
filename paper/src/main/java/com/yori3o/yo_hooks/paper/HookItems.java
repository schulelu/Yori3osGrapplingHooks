package com.yori3o.yo_hooks.paper;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.DamageResistant;
import io.papermc.paper.datacomponent.item.Enchantable;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.Repairable;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.keys.tags.DamageTypeTagKeys;
import io.papermc.paper.registry.set.RegistrySet;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

/**
 * The hooks and hook heads. Paper can't add item types, so they are a fishing rod and a tripwire hook with the
 * mod's components (name, durability, enchantability, repair item, fire resistance) and a {@code custom_model_data}
 * string the optional resource pack turns into the mod's textures.
 */
public final class HookItems {

    public static final String NS = "yo_hooks";
    /** Which item this is, e.g. "iron_grappling_hook" or "iron_hook_head". */
    public static final NamespacedKey ITEM = new NamespacedKey(NS, "item");
    /** UUID of the hook this stack has out (the mod's hook_id component). */
    public static final NamespacedKey HOOK_ID = new NamespacedKey(NS, "hook_id");

    private static final TextColor TOOLTIP = TextColor.color(0x5555FF);

    private final YoHooks plugin;

    HookItems(YoHooks plugin) {
        this.plugin = plugin;
    }

    public ItemStack hook(Tier tier) {
        ItemStack s = ItemStack.of(Material.FISHING_ROD);
        s.setData(DataComponentTypes.ITEM_NAME, Component.translatable("item.yo_hooks." + tier.hookId(), tier.english + " Grappling Hook"));
        int durability = plugin.settings().durability(tier);
        if (durability > 0) {
            s.setData(DataComponentTypes.MAX_DAMAGE, durability);
        } else {
            s.unsetData(DataComponentTypes.MAX_DAMAGE);
            s.setData(DataComponentTypes.UNBREAKABLE);
        }
        s.setData(DataComponentTypes.ENCHANTABLE, Enchantable.enchantable(tier.enchantability));
        s.setData(DataComponentTypes.REPAIRABLE, Repairable.repairable(
            RegistrySet.keySet(RegistryKey.ITEM, TypedKey.create(RegistryKey.ITEM, tier.repair.getKey()))));
        if (tier.fireResistant) {
            fireResistant(s);
        }
        model(s, tier.hookId());
        s.editPersistentDataContainer(pdc -> pdc.set(ITEM, PersistentDataType.STRING, tier.hookId()));
        refreshLore(s);
        return s;
    }

    public ItemStack head(Tier tier) {
        ItemStack s = ItemStack.of(Material.TRIPWIRE_HOOK);
        s.setData(DataComponentTypes.ITEM_NAME, Component.translatable("item.yo_hooks." + tier.headId(), tier.english + " Hook"));
        if (tier.fireResistant) {
            fireResistant(s);
        }
        model(s, tier.headId());
        s.editPersistentDataContainer(pdc -> pdc.set(ITEM, PersistentDataType.STRING, tier.headId()));
        return s;
    }

    public @Nullable ItemStack byId(String id) {
        for (Tier t : Tier.values()) {
            if (t.hookId().equals(id)) {
                return hook(t);
            }
            if (t.headId().equals(id)) {
                return head(t);
            }
        }
        return null;
    }

    public static @Nullable Tier hookTier(@Nullable ItemStack s) {
        String id = itemId(s);
        return id != null && id.endsWith("_grappling_hook") ? Tier.byId(id.substring(0, id.length() - "_grappling_hook".length())) : null;
    }

    public static @Nullable Tier headTier(@Nullable ItemStack s) {
        String id = itemId(s);
        return id != null && id.endsWith("_hook_head") ? Tier.byId(id.substring(0, id.length() - "_hook_head".length())) : null;
    }

    public static boolean isHook(@Nullable ItemStack s) {
        return hookTier(s) != null;
    }

    public static @Nullable String itemId(@Nullable ItemStack s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s.getPersistentDataContainer().get(ITEM, PersistentDataType.STRING);
    }

    public static @Nullable UUID hookId(@Nullable ItemStack s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        String v = s.getPersistentDataContainer().get(HOOK_ID, PersistentDataType.STRING);
        try {
            return v == null ? null : UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Mark a hook stack as having this hook out: the handle model replaces the hook, as with the mod's hook_active. */
    public static void setActive(ItemStack s, Tier tier, UUID hook) {
        s.editPersistentDataContainer(pdc -> pdc.set(HOOK_ID, PersistentDataType.STRING, hook.toString()));
        model(s, tier.handleModel());
    }

    /** Back to a hook in hand; returns false if the stack was not out. */
    public static boolean clearActive(ItemStack s) {
        Tier tier = hookTier(s);
        if (tier == null || hookId(s) == null) {
            return false;
        }
        s.editPersistentDataContainer(pdc -> pdc.remove(HOOK_ID));
        model(s, tier.hookId());
        return true;
    }

    /** "Maximum range: N blocks", including Long Reach, in the mod's tooltip color. */
    public void refreshLore(ItemStack s) {
        Tier tier = hookTier(s);
        if (tier == null) {
            return;
        }
        Settings cfg = plugin.settings();
        int range = HookMath.range(cfg.range(tier), plugin.enchants().longReach(s), cfg.funnyMode);
        s.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
            Component.text("Maximum range: " + range + " blocks", TOOLTIP).decoration(TextDecoration.ITALIC, false))));
    }

    private static void model(ItemStack s, String model) {
        s.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(NS + ":" + model).build());
    }

    private static void fireResistant(ItemStack s) {
        s.setData(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(
            RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).getTag(DamageTypeTagKeys.IS_FIRE)));
    }
}
