package com.yori3o.yo_hooks.paper;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;

/**
 * The mod's 26.2 recipes (src/modern/resources/data/yo_hooks/recipe), with the c: tags expanded to vanilla items.
 * The netherite hook from a diamond hook is a smithing recipe whose result {@link Listeners} builds, so that
 * damaged and enchanted diamond hooks can be upgraded too.
 */
final class Recipes {

    private final YoHooks plugin;
    private final List<NamespacedKey> keys = new ArrayList<>();

    Recipes(YoHooks plugin) {
        this.plugin = plugin;
    }

    List<NamespacedKey> keys() {
        return keys;
    }

    void register() {
        HookItems items = plugin.items();
        for (Tier t : List.of(Tier.COPPER, Tier.IRON, Tier.GOLD)) {
            ShapedRecipe head = new ShapedRecipe(key(t.headId()), items.head(t));
            head.shape("AA", "BA");
            head.setIngredient('A', t.repair);
            head.setIngredient('B', t.nugget);
            head.setCategory(CraftingBookCategory.MISC);
            add(head);
        }
        ShapedRecipe diamondHead = new ShapedRecipe(key(Tier.DIAMOND.headId()), items.head(Tier.DIAMOND));
        diamondHead.shape("AA", " A");
        diamondHead.setIngredient('A', Material.DIAMOND);
        diamondHead.setCategory(CraftingBookCategory.MISC);
        add(diamondHead);

        RecipeChoice template = new RecipeChoice.MaterialChoice(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        RecipeChoice netherite = new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT);
        add(new SmithingTransformRecipe(key(Tier.NETHERITE.headId()), items.head(Tier.NETHERITE), template,
            RecipeChoice.exactChoice(items.head(Tier.DIAMOND)), netherite, false));

        for (Tier t : Tier.values()) {
            ShapedRecipe hook = new ShapedRecipe(key(t == Tier.NETHERITE ? "netherite_grappling_hook_alternative" : t.hookId()), items.hook(t));
            hook.shape("  A", " B ", "C  ");
            hook.setIngredient('A', RecipeChoice.exactChoice(items.head(t)));
            hook.setIngredient('B', Material.IRON_CHAIN);
            hook.setIngredient('C', Material.STICK);
            hook.setCategory(CraftingBookCategory.EQUIPMENT);
            add(hook);
        }
        // Base: any fishing rod; the result is set (or refused) in Listeners#onSmithing
        add(new SmithingTransformRecipe(key(Tier.NETHERITE.hookId()), items.hook(Tier.NETHERITE), template,
            new RecipeChoice.MaterialChoice(Material.FISHING_ROD), netherite, false));

        // Hook heads melt back into nuggets (the mod also melts whole hooks; see README)
        for (Tier t : List.of(Tier.COPPER, Tier.IRON, Tier.GOLD)) {
            RecipeChoice head = RecipeChoice.exactChoice(items.head(t));
            FurnaceRecipe smelt = new FurnaceRecipe(key(t.id + "_nugget_from_smelting"), new org.bukkit.inventory.ItemStack(t.nugget), head, 0.1f, 200);
            smelt.setCategory(CookingBookCategory.MISC);
            add(smelt);
            BlastingRecipe blast = new BlastingRecipe(key(t.id + "_nugget_from_blasting"), new org.bukkit.inventory.ItemStack(t.nugget), head, 0.1f, 100);
            blast.setCategory(CookingBookCategory.MISC);
            add(blast);
        }
    }

    void unregister() {
        for (NamespacedKey k : keys) {
            Bukkit.removeRecipe(k, false);
        }
        keys.clear();
    }

    private void add(org.bukkit.inventory.Recipe r) {
        Bukkit.addRecipe(r, false);
        keys.add(((org.bukkit.Keyed) r).getKey());
    }

    private NamespacedKey key(String id) {
        return new NamespacedKey(plugin, id);
    }
}
