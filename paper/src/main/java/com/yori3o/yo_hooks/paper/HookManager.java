package com.yori3o.yo_hooks.paper;

import com.yori3o.yo_hooks.paper.HookMath.V;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
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
 * player's velocity whenever the rope is taut.
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

    /** Ticks after a hook ends in which a "flying is not enabled" kick is still forgiven. */
    private static final long KICK_GRACE = 60;

    private final YoHooks plugin;
    private final Map<UUID, Hook> hooks = new HashMap<>();
    private final Map<UUID, Long> kickGrace = new HashMap<>();
    private long tick;

    HookManager(YoHooks plugin) {
        this.plugin = plugin;
    }

    @Nullable Hook of(Player p) {
        return hooks.get(p.getUniqueId());
    }

    boolean forgiveFlyKick(Player p) {
        Long until = kickGrace.get(p.getUniqueId());
        return hooks.containsKey(p.getUniqueId()) || (until != null && until >= tick);
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
        hooks.put(p.getUniqueId(), h);
        plugin.sounds().play(p.getLocation().add(0, 1, 0), Sounds.Kind.CAST, Sound.Source.PLAYER, 0.7f, 1f);
    }

    void discard(Hook h, End end) {
        if (hooks.get(h.owner) != h) {
            return;
        }
        hooks.remove(h.owner);
        h.rope.remove();
        kickGrace.put(h.owner, tick + KICK_GRACE);
        Player p = Bukkit.getPlayer(h.owner);
        if (p == null) {
            return;
        }
        clear(p, h.id);
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

    @Override
    public void run() {
        tick++;
        kickGrace.values().removeIf(until -> until < tick);
        for (Hook h : List.copyOf(hooks.values())) {
            Player p = Bukkit.getPlayer(h.owner);
            if (p == null || !p.isValid() || p.isDead() || p.getWorld() != h.world || handOf(p, h.id) == null) {
                discard(h, End.SILENT);
                continue;
            }
            if (h.location().distance(p.getLocation()) > h.maxRange) {
                discard(h, End.BREAK);
                continue;
            }
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
            }
        }
    }

    // --- flight (the mod's HookEntity as a vanilla throwable: gravity 0.03, drag 0.99 or 0.8 in water) ---

    private void fly(Player p, Hook h) {
        World w = h.world;
        Location from = h.location();
        Vector vel = new Vector(h.vx, h.vy, h.vz);
        double len = vel.length();
        boolean hitEntity = false;
        if (len > 1e-6) {
            Vector dir = vel.clone().multiply(1 / len);
            RayTraceResult block = w.rayTraceBlocks(from, dir, len, FluidCollisionMode.NEVER, true);
            double reach = block == null ? len : block.getHitPosition().distance(from.toVector());
            RayTraceResult entity = w.rayTraceEntities(from, dir, reach, 0.125, e -> canHit(p, h, e));
            if (entity != null && entity.getHitEntity() instanceof LivingEntity target) {
                hitEntity(p, h, target);
                hitEntity = true;
            }
            if (block != null && block.getHitBlock() != null) {
                hitBlock(p, h, block.getHitBlock(), block.getHitPosition());
                return;
            }
        }
        h.x += h.vx;
        h.y += h.vy;
        h.z += h.vz;
        double drag = inWater(w.getBlockAt(h.location())) ? 0.8 : 0.99;
        double keep = (hitEntity ? 0.25 : 1) * drag;
        h.vx *= keep;
        h.vy = h.vy * keep - 0.03;
        h.vz *= keep;
        h.rope.moveTo(h.location());
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

        var input = p.getCurrentInput();
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
        double step = HookMath.climbStep(cfg.climbSpeed, h.agility, cfg.funnyMode);
        boolean climbing = false;
        if (up) {
            double l = HookMath.climbUp(h.length, step);
            if (l < h.length) {
                h.length = l;
                climbing = true;
                if (!cfg.funnyMode) {
                    exhaust(p, HookMath.climbExhaustion(cfg.decreaseSatiety, h.agility));
                }
                if (h.climbSoundCooldown <= 0) {
                    plugin.sounds().play(p.getLocation(), Sounds.Kind.CLIMB, Sound.Source.PLAYER, 0.21f, 1f);
                    h.climbSoundCooldown = 39;
                }
            }
        } else if (down) {
            h.length = Math.max(h.length, HookMath.climbDown(h.length, cfg.climbSpeed, h.maxRange));
        }
        if (h.climbSoundCooldown > 0) {
            h.climbSoundCooldown--;
        }
        if (p.isInsideVehicle()) {
            return;
        }
        Location eyeLoc = p.getEyeLocation();
        V eye = new V(eyeLoc.getX(), eyeLoc.getY(), eyeLoc.getZ());
        boolean onGround = clientOnGround(p);
        HookMath.Rope r = HookMath.ropeStep(eye, h.position(), HookMath.headVelocity(moved, onGround), h.length,
            climbing, cfg.softHook, cfg.stiffness, step, onGround, p.isGliding());
        if (r.taut()) {
            p.setVelocity(new Vector(r.velocity().x(), r.velocity().y(), r.velocity().z()));
        }
        if (cfg.funnyMode || (!onGround && r.distance() + 0.6 > h.length && r.towardHook().y() > -0.15)) {
            p.setFallDistance(0);
        }
    }

    private void jumpOff(Player p, Hook h, V moved) {
        Settings cfg = plugin.settings();
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
