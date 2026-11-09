package com.yori3o.yo_hooks.paper;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Nullable;

/** One thrown hook (the mod's HookEntity): flying until it hits a block, then the anchor of the rope. */
final class Hook {

    final UUID id;
    final UUID owner;
    final Tier tier;
    final int agility;
    final boolean gentleTouch;
    final int maxRange;
    final Rope rope;
    final World world;
    final Set<UUID> hitEntities = new HashSet<>();

    double x;
    double y;
    double z;
    double vx;
    double vy;
    double vz;

    boolean attached;
    double length;
    @Nullable Block anchor;
    boolean onFallingBlock;

    /** Where the owner was last tick; the difference is the movement the server saw. */
    Location lastPos;
    boolean lastJump;
    int climbSoundCooldown;
    /** Mouse-wheel climbing: positive ticks climb up, negative climb down. */
    int scrollClimb;

    Hook(UUID id, UUID owner, Tier tier, int agility, boolean gentleTouch, int maxRange, Rope rope, Location eye, Location ownerPos) {
        this.id = id;
        this.owner = owner;
        this.tier = tier;
        this.agility = agility;
        this.gentleTouch = gentleTouch;
        this.maxRange = maxRange;
        this.rope = rope;
        this.world = eye.getWorld();
        this.x = eye.getX();
        this.y = eye.getY();
        this.z = eye.getZ();
        this.lastPos = ownerPos.clone();
    }

    Location location() {
        return new Location(world, x, y, z);
    }

    HookMath.V position() {
        return new HookMath.V(x, y, z);
    }
}
