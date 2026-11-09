package com.yori3o.yo_hooks.paper;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * What players see instead of the mod's client-side rope renderer: the hook head as an item display and an iron
 * chain block display stretched from the hook back to the player's hands. Both live at the hook; only the chain's
 * transformation changes each tick (interpolated over one tick), so nothing needs to ride the player. The
 * entities are never saved with the world.
 */
final class Rope {

    /** Width of the chain relative to the block model (the vanilla chain is 3/16 of a block wide). */
    private static final float WIDTH = 0.8f;
    /** Hands, relative to the player's eyes. */
    private static final double HAND_DROP = 0.45;

    private final ItemDisplay head;
    private final BlockDisplay chain;

    Rope(World world, Location at, ItemStack headItem) {
        Location l = at.clone();
        l.setYaw(0);
        l.setPitch(0);
        head = world.spawn(l, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(headItem);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.addScoreboardTag(YoHooks.ENTITY_TAG);
        });
        chain = world.spawn(l, BlockDisplay.class, d -> {
            d.setPersistent(false);
            d.setBlock(Material.IRON_CHAIN.createBlockData());
            d.setTeleportDuration(1);
            d.setInterpolationDuration(1);
            d.addScoreboardTag(YoHooks.ENTITY_TAG);
        });
    }

    /** Move both displays with a flying hook. */
    void moveTo(Location at) {
        Location l = at.clone();
        l.setYaw(0);
        l.setPitch(0);
        head.teleport(l);
        chain.teleport(l);
    }

    /** Stretch the chain from the hook to the player's hands and point the head along the rope. */
    void stretch(Location hook, Location eye) {
        Vector3f d = new Vector3f(
            (float) (eye.getX() - hook.getX()), (float) (eye.getY() - HAND_DROP - hook.getY()), (float) (eye.getZ() - hook.getZ()));
        float length = d.length();
        if (length < 0.05f) {
            return;
        }
        Vector3f dir = new Vector3f(d).div(length);
        Quaternionf q = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), dir);
        // The chain model runs up the block's centre; shift it so its axis starts at the hook, then rotate and scale
        Vector3f centre = new Vector3f(0.5f * WIDTH, 0, 0.5f * WIDTH).rotate(q).negate();
        chain.setInterpolationDelay(0);
        chain.setTransformation(new Transformation(centre, q, new Vector3f(WIDTH, length, WIDTH), new Quaternionf()));
        // The head points away from the player, tipped like the mod's renderer (135 degrees around Z)
        Quaternionf away = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f(dir).negate())
            .rotateZ((float) Math.toRadians(135));
        head.setInterpolationDelay(0);
        head.setTransformation(new Transformation(new Vector3f(), away, new Vector3f(0.75f), new Quaternionf()));
    }

    void remove() {
        head.remove();
        chain.remove();
    }
}
