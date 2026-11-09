package com.yori3o.yo_hooks.paper;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

/** The five hook tiers with the mod's stats (yo_hooks/hooks.json). */
public enum Tier {
    COPPER("copper", "Copper", 10, 42, 4, 1, Material.COPPER_INGOT, Material.COPPER_NUGGET, false),
    IRON("iron", "Iron", 12, 72, 6, 1, Material.IRON_INGOT, Material.IRON_NUGGET, false),
    GOLD("gold", "Golden", 17, 24, 20, 1, Material.GOLD_INGOT, Material.GOLD_NUGGET, false),
    DIAMOND("diamond", "Diamond", 21, 156, 12, 2, Material.DIAMOND, null, false),
    NETHERITE("netherite", "Netherite", 26, 384, 15, 2, Material.NETHERITE_INGOT, null, true);

    public final String id;
    public final String english;
    public final int range;
    public final int durability;
    public final int enchantability;
    public final int damage;
    public final Material repair;
    public final @Nullable Material nugget;
    public final boolean fireResistant;

    Tier(String id, String english, int range, int durability, int enchantability, int damage, Material repair,
         @Nullable Material nugget, boolean fireResistant) {
        this.id = id;
        this.english = english;
        this.range = range;
        this.durability = durability;
        this.enchantability = enchantability;
        this.damage = damage;
        this.repair = repair;
        this.nugget = nugget;
        this.fireResistant = fireResistant;
    }

    public String hookId() {
        return id + "_grappling_hook";
    }

    public String headId() {
        return id + "_hook_head";
    }

    /** The model shown while the hook is out (the head is on the rope, only the handle stays in hand). */
    public String handleModel() {
        return this == NETHERITE ? "grappling_hook_handle_netherite" : "grappling_hook_handle";
    }

    public static @Nullable Tier byId(String id) {
        for (Tier t : values()) {
            if (t.id.equals(id)) {
                return t;
            }
        }
        return null;
    }
}
