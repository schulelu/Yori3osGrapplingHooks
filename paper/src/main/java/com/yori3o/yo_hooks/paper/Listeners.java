package com.yori3o.yo_hooks.paper;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Player input and the rules that keep hooks, heads and the hook enchantments apart from plain items. */
final class Listeners implements Listener {

    private final YoHooks plugin;

    Listeners(YoHooks plugin) {
        this.plugin = plugin;
    }

    private HookManager hooks() {
        return plugin.hooks();
    }

    // --- using a hook ---

    @EventHandler(priority = EventPriority.HIGH)
    void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        EquipmentSlot hand = e.getHand();
        if (hand == null || !HookItems.isHook(e.getItem())) {
            return;
        }
        // Like the mod (and vanilla): using a door, chest or button wins over the item, unless sneaking
        Block clicked = e.getClickedBlock();
        if (clicked != null && interactable(clicked) && !e.getPlayer().isSneaking()
            && e.useInteractedBlock() != Event.Result.DENY) {
            e.setUseItemInHand(Event.Result.DENY);
            return;
        }
        e.setUseItemInHand(Event.Result.DENY); // no fishing bobber
        e.setUseInteractedBlock(Event.Result.DENY);
        hooks().use(e.getPlayer(), hand);
    }

    @EventHandler(ignoreCancelled = true)
    void onPlaceHead(BlockPlaceEvent e) {
        if (HookItems.headTier(e.getItemInHand()) != null) {
            e.setCancelled(true); // hook heads are crafting parts, not tripwire hooks
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onScroll(PlayerItemHeldEvent e) {
        if (!plugin.settings().scrollToClimb || hooks().of(e.getPlayer()) == null) {
            return;
        }
        int delta = e.getNewSlot() - e.getPreviousSlot();
        boolean wheelUp = delta == -1 || delta == 8; // the wheel moves the selection left, wrapping from 0 to 8
        e.setCancelled(true);
        hooks().scroll(e.getPlayer(), wheelUp);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onHit(EntityDamageByEntityEvent e) {
        // Before the knockback is applied: a hit swings a seated player on the rope
        if (e.getEntity() instanceof Player p) {
            hooks().knockOffSeat(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onFuel(FurnaceBurnEvent e) {
        if (HookItems.isHook(e.getFuel())) {
            e.setCancelled(true); // a fishing rod underneath, but the mod's hooks don't burn
        }
    }

    @EventHandler
    void onDrop(PlayerDropItemEvent e) {
        Item dropped = e.getItemDrop();
        ItemStack s = dropped.getItemStack();
        if (HookItems.clearActive(s)) {
            dropped.setItemStack(s);
        }
    }

    @EventHandler
    void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        hooks().clearAll(p); // no hook survives a restart
        p.discoverRecipes(plugin.recipes().keys());
    }

    @EventHandler
    void onQuit(PlayerQuitEvent e) {
        Hook h = hooks().of(e.getPlayer());
        if (h != null) {
            hooks().discard(h, HookManager.End.SILENT);
        }
    }

    @EventHandler
    void onDeath(PlayerDeathEvent e) {
        Hook h = hooks().of(e.getPlayer());
        if (h != null) {
            hooks().discard(h, HookManager.End.SILENT);
        }
    }

    // --- enchanting: the hook enchantments only on hooks, the fishing ones only on fishing rods ---

    private boolean wrongFor(ItemStack item, Enchantment e) {
        if (HookItems.isHook(item)) {
            return e.equals(Enchantment.LURE) || e.equals(Enchantment.LUCK_OF_THE_SEA);
        }
        return plugin.enchants().isOurs(e);
    }

    @EventHandler(ignoreCancelled = true)
    void onEnchantOffers(PrepareItemEnchantEvent e) {
        ItemStack item = e.getItem();
        if (item.getType() != Material.FISHING_ROD) {
            return;
        }
        boolean hook = HookItems.isHook(item);
        Enchantment replacement = hook ? plugin.enchants().longReachEnchantment() : Enchantment.LURE;
        for (EnchantmentOffer offer : e.getOffers()) {
            if (offer != null && replacement != null && wrongFor(item, offer.getEnchantment())) {
                offer.setEnchantment(replacement);
                offer.setEnchantmentLevel(Math.min(offer.getEnchantmentLevel(), replacement.getMaxLevel()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    void onEnchant(EnchantItemEvent e) {
        ItemStack item = e.getItem();
        if (item.getType() != Material.FISHING_ROD) {
            return;
        }
        boolean hook = HookItems.isHook(item);
        Map<Enchantment, Integer> add = e.getEnchantsToAdd();
        int removedLevel = 0;
        for (Enchantment ench : Map.copyOf(add).keySet()) {
            if (wrongFor(item, ench)) {
                removedLevel = Math.max(removedLevel, add.remove(ench));
            }
        }
        Enchantment replacement = hook ? plugin.enchants().longReachEnchantment() : Enchantment.LURE;
        if (add.isEmpty() && replacement != null) {
            add.put(replacement, Math.max(1, Math.min(removedLevel, replacement.getMaxLevel())));
        }
        if (hook) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack done = e.getInventory().getItem(0);
                if (HookItems.isHook(done)) {
                    plugin.items().refreshLore(done);
                    e.getInventory().setItem(0, done);
                }
            });
        }
    }

    @EventHandler
    void onAnvil(PrepareAnvilEvent e) {
        ItemStack result = e.getResult();
        if (result == null || result.getType() != Material.FISHING_ROD) {
            return;
        }
        ItemStack first = e.getInventory().getFirstItem();
        ItemStack second = e.getInventory().getSecondItem();
        // Combining two rods: only a hook with a hook of the same tier, or two plain fishing rods
        if (second != null && second.getType() == Material.FISHING_ROD && HookItems.hookTier(first) != HookItems.hookTier(second)) {
            e.setResult(null);
            return;
        }
        for (Enchantment ench : result.getEnchantments().keySet()) {
            if (wrongFor(result, ench)) {
                e.setResult(null);
                return;
            }
        }
        if (HookItems.isHook(result)) {
            ItemStack r = result.clone();
            plugin.items().refreshLore(r);
            e.setResult(r);
        }
    }

    @EventHandler
    void onSmithing(PrepareSmithingEvent e) {
        if (HookItems.hookTier(e.getResult()) != Tier.NETHERITE) {
            return;
        }
        ItemStack base = e.getInventory().getInputEquipment();
        if (HookItems.hookTier(base) != Tier.DIAMOND) {
            e.setResult(null); // the recipe's base is any fishing rod; only a diamond hook upgrades
            return;
        }
        ItemStack up = plugin.items().hook(Tier.NETHERITE);
        up.addUnsafeEnchantments(base.getEnchantments());
        Integer damage = base.getData(DataComponentTypes.DAMAGE);
        if (damage != null && damage > 0) {
            up.setData(DataComponentTypes.DAMAGE, damage);
        }
        plugin.items().refreshLore(up);
        e.setResult(up);
    }

    /** Deprecated for being approximate; Paper has no exact replacement, and approximate is enough here. */
    @SuppressWarnings("deprecation")
    private static boolean interactable(Block b) {
        return b.getType().isInteractable();
    }
}
