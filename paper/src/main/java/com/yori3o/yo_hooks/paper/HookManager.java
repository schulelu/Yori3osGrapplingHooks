package com.yori3o.yo_hooks.paper;

import com.yori3o.yo_hooks.paper.HookMath.V;
import com.yori3o.yo_hooks.paper.Settings.Control;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Runs every hook every tick: flight, attaching, the rope physics and the controls. In the mod the player's client
 * moves the player along the rope; here the server computes the mod's formula and sends the result as the
 * player's velocity whenever the rope is taut. A player who hangs still is held on a {@link Seat} instead.
 */
final class HookManager implements Runnable {

    enum End {
        /** Right-click again: the mod's "back" sound. */
        RETRACT,
        /** Jumped off: "back". */
        JUMP,
        /** Out of range: "breaking". */
        BREAK,
        /** No sound (anchor gone, slot switched, owner gone, blocked block). */
        SILENT
    }

    /** With range catch, a flying hook only breaks this far past its range (a teleport or an ender pearl). */
    private static final double CATCH_BREAK_SLACK = 4;
    /** Ticks of hanging still before the seat holds the player. */
    private static final int SEAT_DELAY = 10;
    /** Ticks after leaving the seat before it holds the player again. */
    private static final int SEAT_COOLDOWN = 20;
    /** Speed toward a pressed movement key that ends the seat, to start the swing. */
    private static final double SEAT_PUSH = 0.1;

    private final YoHooks plugin;
    private final Map<UUID, Hook> hooks = new HashMap<>();
    private long tick;

    HookManager(YoHooks plugin) {
        this.plugin = plugin;
    }

    @Nullable Hook of(Player p) {
        return hooks.get(p.getUniqueId());
    }

    /** Right-click with a hook: cast it, or pull the current one back. */
    void use(Player p, EquipmentSlot hand) {
        Hook current = hooks.get(p.getUniqueId());
        if (current != null) {
            discard(current, End.RETRACT);
            return;
        }
        ItemStack stack = p.getInventory().getItem(hand);
        Tier tier = HookItems.hookTier(stack);
        if (tier == null) {
            return;
        }
        Settings cfg = plugin.settings();
        if (!cfg.funnyMode) {
            exhaust(p, HookMath.castExhaustion(cfg.decreaseSatiety));
        }
        Enchants en = plugin.enchants();
        int agility = en.agility(stack);
        int range = HookMath.range(cfg.range(tier), en.longReach(stack), cfg.funnyMode);
        UUID id = UUID.randomUUID();
        clearAll(p);
        ItemStack bound = p.getInventory().getItem(hand);
        HookItems.setActive(bound, tier, id);
        plugin.items().refreshLore(bound);
        p.getInventory().setItem(hand, bound);

        Location eye = p.getEyeLocation();
        Vector v = eye.getDirection().multiply(HookMath.castSpeed(cfg.funnyMode, agility));
        Hook h = new Hook(id, p.getUniqueId(), tier, agility, en.gentleTouch(stack), range,
            new Rope(p.getWorld(), eye, plugin.items().head(tier)), eye, p.getLocation());
        h.vx = v.getX();
        h.vy = v.getY();
        h.vz = v.getZ();
        h.lastJump = cfg.jumpOff.held(p.getCurrentInput());
        h.lastLetGo = cfg.letGo.held(p.getCurrentInput());
        hooks.put(p.getUniqueId(), h);
        plugin.sounds().play(p.getLocation().add(0, 1, 0), Sounds.Kind.CAST, Sound.Source.PLAYER, 0.7f, 1f);
    }

    void discard(Hook h, End end) {
        if (hooks.get(h.owner) != h) {
            return;
        }
        hooks.remove(h.owner);
        h.rope.remove();
        Player p = Bukkit.getPlayer(h.owner);
        if (h.seat != null) {
            h.seat.dismount(p);
            h.seat = null;
        }
        if (p == null) {
            return;
        }
        clear(p, h.id);
        if (h.hudKey != null) {
            RopeHud.clear(p);
        }
        Location at = p.getLocation().add(0, 1, 0);
        switch (end) {
            case RETRACT, JUMP -> plugin.sounds().play(at, Sounds.Kind.BACK, Sound.Source.PLAYER, 1f, 1f);
            case BREAK -> plugin.sounds().play(at, Sounds.Kind.BREAKING, Sound.Source.PLAYER, 1f, 1f);
            case SILENT -> { }
        }
    }

    void discardAll() {
        for (Hook h : List.copyOf(hooks.values())) {
            discard(h, End.SILENT);
        }
    }

    /** Remove the "hook is out" mark from every stack of the player (a new cast, or after a restart). */
    void clearAll(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && HookItems.clearActive(s)) {
                inv.setItem(i, s);
            }
        }
    }

    private void clear(Player p, UUID hook) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s != null && hook.equals(HookItems.hookId(s)) && HookItems.clearActive(s)) {
                inv.setItem(i, s);
            }
        }
    }

    /** The hand holding the stack bound to this hook, or null if neither does any more. */
    private static @Nullable EquipmentSlot handOf(Player p, UUID hook) {
        if (hook.equals(HookItems.hookId(p.getInventory().getItemInMainHand()))) {
            return EquipmentSlot.HAND;
        }
        if (hook.equals(HookItems.hookId(p.getInventory().getItemInOffHand()))) {
            return EquipmentSlot.OFF_HAND;
        }
        return null;
    }

    void scroll(Player p, boolean up) {
        Hook h = hooks.get(p.getUniqueId());
        if (h != null) {
            h.scrollClimb = Math.max(-10, Math.min(10, h.scrollClimb + (up ? 2 : -2)));
        }
    }

    /** Getting hit while seated drops the player back into the swing, so the knockback moves them. */
    void knockOffSeat(Player p) {
        Hook h = hooks.get(p.getUniqueId());
        if (h != null && h.seat != null) {
            unseat(p, h, null);
        }
    }

    @Override
    public void run() {
        tick++;
        Settings cfg = plugin.settings();
        for (Hook h : List.copyOf(hooks.values())) {
            Player p = Bukkit.getPlayer(h.owner);
            if (p == null || !p.isValid() || p.isDead() || p.getWorld() != h.world || handOf(p, h.id) == null) {
                discard(h, End.SILENT);
                continue;
            }
            // A flying hook on a rope is held at the range by fly(); only a far jump of the player breaks it
            double slack = !h.attached && cfg.rangeCatch ? CATCH_BREAK_SLACK : 0;
            if (h.location().distance(p.getLocation()) > h.maxRange + slack) {
                discard(h, End.BREAK);
                continue;
            }
            // The mod's ServerGamePacketListenerImplMixin: hanging on a rope isn't flying, so no "floating" kick
            p.resetFlyingTicks();
            if (ThreadLocalRandom.current().nextDouble() > 0.9955) {
                plugin.sounds().play(h.location(), Sounds.Kind.AMBIENT, Sound.Source.AMBIENT, 1f, 1f);
            }
            if (h.attached) {
                hang(p, h);
            } else {
                fly(p, h);
            }
            if (hooks.get(h.owner) == h) {
                h.rope.stretch(h.location(), p.getEyeLocation());
                if (cfg.actionBar) {
                    RopeHud.show(p, h, cfg, tick);
                }
            }
        }
    }

    // --- flight (the mod's HookEntity as a vanilla throwable: gravity 0.03, drag 0.99 or 0.8 in water) ---

    private void fly(Player p, Hook h) {
        Settings cfg = plugin.settings();
        World w = h.world;
        V pos = h.position();
        V vel = new V(h.vx, h.vy, h.vz);
        V next = pos.add(vel);
        if (cfg.rangeCatch) {
            // Range catch: at the end of the rope the hook stops going outward and swings down on it
            Location feet = p.getLocation();
            HookMath.Catch c = HookMath.catchStep(pos, vel, new V(feet.getX(), feet.getY(), feet.getZ()),
                h.maxRange - HookMath.CATCH_MARGIN);
            next = c.position();
            vel = c.velocity();
            h.caught |= c.taut();
        }
        Location from = h.location();
        V step = next.sub(pos);
        double len = step.length();
        boolean hitEntity = false;
        if (len > 1e-6) {
            Vector dir = new Vector(step.x(), step.y(), step.z()).multiply(1 / len);
            RayTraceResult block = w.rayTraceBlocks(from, dir, len, FluidCollisionMode.NEVER, true);
            double reach = block == null ? len : block.getHitPosition().distance(from.toVector());
            RayTraceResult entity = w.rayTraceEntities(from, dir, reach, 0.125, e -> canHit(p, h, e));
            if (entity != null && entity.getHitEntity() instanceof LivingEntity target) {
                hitEntity(p, h, target);
                hitEntity = true;
            }
            if (block != null && block.getHitBlock() != null) {
                if (h.caught && block.getHitPosition().distance(p.getEyeLocation().toVector()) < HookMath.CATCH_MIN_ATTACH) {
                    discard(h, End.RETRACT); // thrown up, fell back: no anchor at the player's own feet
                    return;
                }
                hitBlock(p, h, block.getHitBlock(), block.getHitPosition());
                return;
            }
        }
        h.x = next.x();
        h.y = next.y();
        h.z = next.z();
        double drag = inWater(w.getBlockAt(h.location())) ? 0.8 : h.caught ? HookMath.CATCH_DRAG : 0.99;
        double keep = (hitEntity ? 0.25 : 1) * drag;
        h.vx = vel.x() * keep;
        h.vy = vel.y() * keep - (h.caught ? HookMath.CATCH_GRAVITY : 0.03);
        h.vz = vel.z() * keep;
        h.rope.moveTo(h.location());
        if (h.caught && ++h.caughtTicks > HookMath.CATCH_TICKS) {
            discard(h, End.RETRACT); // swung on the rope without finding anything
        }
    }

    private static boolean inWater(Block b) {
        return b.getType() == Material.WATER || (b.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged());
    }

    private static boolean canHit(Player p, Hook h, Entity e) {
        return e instanceof LivingEntity le && !le.isDead() && e != p && !e.equals(p.getVehicle())
            && !h.hitEntities.contains(e.getUniqueId()) && !(e instanceof Player other && other.getGameMode() == GameMode.SPECTATOR);
    }

    private void hitEntity(Player p, Hook h, LivingEntity target) {
        h.hitEntities.add(target.getUniqueId());
        int damage = plugin.settings().damage(h.tier);
        if (damage > 0) {
            target.damage(damage, DamageSource.builder(DamageType.THROWN).withCausingEntity(p).withDirectEntity(p).build());
        }
    }

    private void hitBlock(Player p, Hook h, Block block, Vector at) {
        Settings cfg = plugin.settings();
        Material type = block.getType();
        if (cfg.refuses(type)) {
            discard(h, End.SILENT);
            return;
        }
        if (!cfg.funnyMode && p.getGameMode() != GameMode.CREATIVE) {
            EquipmentSlot hand = handOf(p, h.id);
            if (hand != null) {
                p.damageItemStack(hand, 1);
            }
            if (handOf(p, h.id) == null) { // the hook broke
                discard(h, End.SILENT);
                return;
            }
        }
        if (cfg.breakingFragileBlocks && !h.gentleTouch && p.getGameMode() != GameMode.ADVENTURE && p.getGameMode() != GameMode.SPECTATOR) {
            if (Fragile.is(type)) {
                Boolean drops = mayBreak(p, block);
                if (drops != null) {
                    if (drops) {
                        block.breakNaturally(true);
                    } else {
                        block.setType(Material.AIR);
                    }
                    discard(h, End.SILENT);
                    return;
                }
            } else if (type.hasGravity() && fallsFree(block) && mayBreak(p, block) != null) {
                BlockData data = block.getBlockData();
                block.setType(Material.AIR);
                h.world.spawn(block.getLocation().add(0.5, 0, 0.5), FallingBlock.class, fb -> fb.setBlockData(data));
                h.onFallingBlock = true;
            } else if ((type == Material.REDSTONE_ORE || type == Material.DEEPSLATE_REDSTONE_ORE)
                && block.getBlockData() instanceof Lightable lit && !lit.isLit()) {
                lit.setLit(true);
                block.setBlockData(lit);
            }
        }
        Location hit = new Location(h.world, at.getX(), at.getY(), at.getZ());
        h.world.playSound(hit, block.getBlockSoundGroup().getHitSound(), SoundCategory.PLAYERS, 0.22f, 1.15f);
        plugin.sounds().play(hit, Sounds.Kind.HIT, Sound.Source.PLAYER, 0.26f, 1f);
        h.x = at.getX();
        h.y = at.getY();
        h.z = at.getZ();
        h.vx = h.vy = h.vz = 0;
        h.attached = true;
        h.anchor = block;
        h.length = Math.max(HookMath.MIN_LENGTH, p.getEyeLocation().toVector().distance(at));
        h.rope.moveTo(hit);
    }

    /** May the player break this block (WorldGuard and other protections listen to BlockBreakEvent)? Returns
     * whether it drops items, or null if not allowed. */
    private static @Nullable Boolean mayBreak(Player p, Block block) {
        BlockBreakEvent ev = new BlockBreakEvent(block, p);
        ev.setExpToDrop(0);
        Bukkit.getPluginManager().callEvent(ev);
        return ev.isCancelled() ? null : ev.isDropItems();
    }

    private static boolean fallsFree(Block b) {
        Block below = b.getRelative(BlockFace.DOWN);
        return below.isEmpty() || below.isLiquid() || below.isReplaceable() || below.getType() == Material.FIRE;
    }

    // --- hanging: climbing, jumping off and the rope physics ---

    private void hang(Player p, Hook h) {
        Settings cfg = plugin.settings();
        if (h.anchor == null || h.anchor.getType().isAir()) {
            discard(h, End.SILENT);
            return;
        }
        Location now = p.getLocation();
        V moved = new V(now.getX() - h.lastPos.getX(), now.getY() - h.lastPos.getY(), now.getZ() - h.lastPos.getZ());
        h.lastPos = now.clone();
        if (h.seatCooldown > 0) {
            h.seatCooldown--;
        }
        if (h.seat != null && !h.seat.holds(p)) { // dismounted by vanilla, a teleport or another plugin
            h.seat.dismount(null);
            h.seat = null;
            h.stillTicks = 0;
            h.seatCooldown = SEAT_COOLDOWN;
        }
        boolean seated = h.seat != null;
        boolean onGround = !seated && clientOnGround(p);
        boolean otherVehicle = !seated && p.isInsideVehicle();

        Input input = p.getCurrentInput();
        boolean letGo = cfg.letGo.held(input);
        if (letGo && !h.lastLetGo && !onGround && !otherVehicle) {
            discard(h, End.RETRACT); // let go of the rope: no boost, no hunger
            return;
        }
        h.lastLetGo = letGo;
        boolean jump = cfg.jumpOff.held(input);
        if (jump && !h.lastJump) {
            jumpOff(p, h, moved);
            return;
        }
        h.lastJump = jump;

        boolean up = cfg.climbUp.held(input) || h.scrollClimb > 0;
        boolean down = !up && (cfg.climbDown.held(input) || h.scrollClimb < 0);
        if (h.scrollClimb != 0) {
            h.scrollClimb -= Integer.signum(h.scrollClimb);
        }
        if (h.climbSoundCooldown > 0) {
            h.climbSoundCooldown--;
        }
        double step = HookMath.climbStep(cfg.climbSpeed, h.agility, cfg.funnyMode);
        if (seated) {
            sit(p, h, input, up, down, step);
            return;
        }
        boolean climbing = false;
        if (up) {
            double l = HookMath.climbUp(h.length, step);
            if (l < h.length) {
                h.length = l;
                climbing = true;
                climbed(p, h);
            }
        } else if (down) {
            h.length = Math.max(h.length, HookMath.climbDown(h.length, cfg.climbSpeed, h.maxRange));
        }
        if (otherVehicle) {
            return;
        }
        Location eyeLoc = p.getEyeLocation();
        V eye = new V(eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ());
        HookMath.Rope r = HookMath.ropeStep(eye, h.position(), HookMath.headVelocity(moved, onGround), h.length,
            climbing, cfg.softHook, cfg.stiffness, step, onGround, p.isGliding());
        if (r.taut()) {
            p.setVelocity(new Vector(r.velocity().x(), r.velocity().y(), r.velocity().z()));
        }
        if (cfg.funnyMode || (!onGround && r.distance() + 0.6 > h.length && r.towardHook().y() > -0.15)) {
            p.setFallDistance(0);
        }
        if (cfg.seatWhenStill) {
            considerSeat(p, h, input, moved, r, onGround || up || down);
        }
    }

    /** Climbing up costs hunger and plays the climb sound at most every 39 ticks, like the mod's ServerReceiver. */
    private void climbed(Player p, Hook h) {
        Settings cfg = plugin.settings();
        if (!cfg.funnyMode) {
            exhaust(p, HookMath.climbExhaustion(cfg.decreaseSatiety, h.agility));
        }
        if (h.climbSoundCooldown <= 0) {
            plugin.sounds().play(p.getLocation(), Sounds.Kind.CLIMB, Sound.Source.PLAYER, 0.21f, 1f);
            h.climbSoundCooldown = 39;
        }
    }

    // --- the seat (not in the mod): hanging still is held by the server, so nothing bobs ---

    /** Seat a player who has hung still on the tight rope for {@link #SEAT_DELAY} ticks. */
    private void considerSeat(Player p, Hook h, Input in, V moved, HookMath.Rope r, boolean busy) {
        // The vertical limit is loose on purpose: the up-and-down bob is what the seat removes
        boolean still = !busy && !p.isGliding() && !p.isFlying() && !p.isInWater() && !p.isClimbing()
            && !in.isForward() && !in.isBackward() && !in.isLeft() && !in.isRight() && !in.isJump() && !in.isSneak()
            && Math.hypot(moved.x(), moved.z()) < 0.05 && Math.abs(moved.y()) < 0.3
            && r.distance() > h.length - 0.3 && r.towardHook().y() > 0;
        h.stillTicks = still ? h.stillTicks + 1 : 0;
        if (h.stillTicks < SEAT_DELAY || h.seatCooldown > 0) {
            return;
        }
        h.stillTicks = 0;
        Seat seat = Seat.mount(p);
        if (seat == null) {
            h.seatCooldown = SEAT_COOLDOWN * 5; // refused; don't retry every tick
            return;
        }
        h.seat = seat;
        h.length = Math.max(HookMath.MIN_LENGTH, r.distance());
        p.setFallDistance(0);
    }

    /** One tick on the seat: climbing moves it along the rope, other keys put the player back into the swing. */
    private void sit(Player p, Hook h, Input in, boolean up, boolean down, double step) {
        Settings cfg = plugin.settings();
        if (in.isSneak()) { // vanilla's sneak dismount would drop the player at the seat's height; leave cleanly
            unseat(p, h, null);
            return;
        }
        Vector push = swingPush(p, in, cfg);
        if (push != null) {
            unseat(p, h, push);
            return;
        }
        p.setFallDistance(0);
        if (!up && !down) {
            return;
        }
        Location eyeLoc = p.getEyeLocation();
        V toHook = h.position().sub(new V(eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ()));
        double dist = toHook.length();
        if (dist < 1e-6) {
            return;
        }
        double l = up ? HookMath.climbUp(h.length, step)
            : Math.max(h.length, HookMath.climbDown(h.length, cfg.climbSpeed, h.maxRange));
        if (l == h.length) {
            return; // at the top, or as long as the range allows
        }
        V d = toHook.mul((dist - l) / dist); // moves the eyes to exactly l from the hook
        Vector delta = new Vector(d.x(), d.y(), d.z());
        if (p.getWorld().hasCollisionsIn(p.getBoundingBox().shift(delta).expand(-1e-3))) {
            if (down) {
                unseat(p, h, null); // reached the ground
            }
            return; // going up: something is in the way
        }
        h.length = l;
        h.seat.move(delta);
        if (up) {
            climbed(p, h);
        }
    }

    /** A pressed movement key that isn't a rope control starts a swing toward it; null if there is none. */
    private static @Nullable Vector swingPush(Player p, Input in, Settings cfg) {
        double forward = (pushes(Control.FORWARD, in.isForward(), cfg) ? 1 : 0) - (pushes(Control.BACKWARD, in.isBackward(), cfg) ? 1 : 0);
        double left = (pushes(Control.LEFT, in.isLeft(), cfg) ? 1 : 0) - (pushes(Control.RIGHT, in.isRight(), cfg) ? 1 : 0);
        if (forward == 0 && left == 0) {
            return null;
        }
        // Vanilla's moveRelative: forward is (-sin yaw, cos yaw), left is (cos yaw, sin yaw)
        double yaw = Math.toRadians(p.getLocation().getYaw());
        double x = -Math.sin(yaw) * forward + Math.cos(yaw) * left;
        double z = Math.cos(yaw) * forward + Math.sin(yaw) * left;
        double n = Math.hypot(x, z);
        return new Vector(x / n * SEAT_PUSH, 0, z / n * SEAT_PUSH);
    }

    private static boolean pushes(Control key, boolean held, Settings cfg) {
        return held && key != cfg.climbUp && key != cfg.climbDown && key != cfg.jumpOff && key != cfg.letGo;
    }

    private void unseat(Player p, Hook h, @Nullable Vector push) {
        if (h.seat == null) {
            return;
        }
        h.seat.dismount(p);
        h.seat = null;
        h.stillTicks = 0;
        h.seatCooldown = SEAT_COOLDOWN;
        h.lastPos = p.getLocation().clone();
        if (push != null) {
            p.setVelocity(push);
        }
    }

    private void jumpOff(Player p, Hook h, V moved) {
        Settings cfg = plugin.settings();
        unseat(p, h, null);
        Location eyeLoc = p.getEyeLocation();
        V eye = new V(eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ());
        if (HookMath.jumpAllowed(eye, h.position(), cfg.funnyMode)) {
            Vector look = eyeLoc.getDirection();
            V v = HookMath.jumpImpulse(HookMath.headVelocity(moved, clientOnGround(p)), new V(look.getX(), look.getY(), look.getZ()),
                h.agility, cfg.funnyMode ? 1.4 : 1.0);
            p.setVelocity(new Vector(v.x(), v.y(), v.z()));
        }
        if (!cfg.funnyMode) {
            exhaust(p, HookMath.jumpExhaustion(cfg.decreaseSatiety));
        }
        discard(h, End.JUMP);
        p.setFallDistance(0);
    }

    private static void exhaust(Player p, double amount) {
        GameMode m = p.getGameMode();
        if (m == GameMode.SURVIVAL || m == GameMode.ADVENTURE) {
            p.setExhaustion(p.getExhaustion() + (float) amount);
        }
    }

    /**
     * The mod runs this physics on the client with the client's own on-ground flag, so the client's claim is the
     * input that matches it. Paper deprecates the getter only because the value is client-controlled.
     */
    @SuppressWarnings("deprecation")
    private static boolean clientOnGround(Player p) {
        return p.isOnGround();
    }
}
