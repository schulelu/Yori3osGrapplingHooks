package com.yori3o.yo_hooks.paper;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Holds a player who hangs still on a tight rope (not in the mod): an invisible item display the player rides.
 * A rider's client shows the player exactly where the server puts them, so there is no bobbing at any ping, and
 * the server doesn't count a passenger as floating. Climbing moves the seat, interpolated over two ticks. The
 * display is never saved with the world.
 */
final class Seat {

    /**
     * Height of the seat above the rider's feet: vanilla places a player 0.6 below the vehicle's passenger point,
     * and a display's passenger point is its own position.
     */
    static final double OFFSET = 0.6;

    private final ItemDisplay display;

    private Seat(ItemDisplay display) {
        this.display = display;
    }

    /** Seat the player where they are, or return null if the mount was refused (another plugin, say). */
    static @Nullable Seat mount(Player p) {
        Location at = p.getLocation().add(0, OFFSET, 0);
        at.setYaw(0);
        at.setPitch(0);
        ItemDisplay d = p.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setTeleportDuration(2);
            e.addScoreboardTag(YoHooks.ENTITY_TAG);
        });
        if (!d.addPassenger(p)) {
            d.remove();
            return null;
        }
        return new Seat(d);
    }

    /** Whether the player still rides this seat (vanilla dismounts on sneak, a teleport ejects them). */
    boolean holds(Player p) {
        return display.isValid() && display.getPassengers().contains(p);
    }

    /** Move the seat, and with it the rider. */
    void move(Vector delta) {
        display.teleport(display.getLocation().add(delta)); // keeps the rider (vanilla behaviour since 1.21.10)
    }

    /**
     * Let the player off where they are and remove the seat. Vanilla puts a dismounted rider at the vehicle's
     * position, which is {@link #OFFSET} above their feet, so the seat first moves down to the feet.
     */
    void dismount(@Nullable Player p) {
        if (p != null && p.isValid() && holds(p)) {
            Location feet = p.getLocation();
            feet.setYaw(0);
            feet.setPitch(0);
            display.teleport(feet);
            display.removePassenger(p);
        }
        display.remove();
    }
}
