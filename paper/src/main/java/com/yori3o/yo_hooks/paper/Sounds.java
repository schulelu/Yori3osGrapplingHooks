package com.yori3o.yo_hooks.paper;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The mod's sounds (assets/yo_hooks/sounds.json) for players whose resource pack loaded, vanilla stand-ins for
 * everyone else. Volumes and pitches are the mod's.
 */
final class Sounds {

    enum Kind {
        CAST("cast", "entity.fishing_bobber.throw"),
        BACK("back", "entity.fishing_bobber.retrieve"),
        HIT("hit", "block.chain.hit"),
        CLIMB("climb", "block.chain.step"),
        AMBIENT("ambient", null),
        BREAKING("breaking", "entity.item.break");

        final Key custom;
        final @Nullable Key fallback;

        Kind(String id, @Nullable String fallback) {
            this.custom = Key.key(HookItems.NS, id);
            this.fallback = fallback == null ? null : Key.key(fallback);
        }
    }

    private final Pack pack;

    Sounds(Pack pack) {
        this.pack = pack;
    }

    void play(Location at, Kind kind, Sound.Source source, float volume, float pitch) {
        double reach = 16 * Math.max(1, volume);
        for (Player p : at.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(at) > reach * reach) {
                continue;
            }
            Key key = pack.loaded(p) ? kind.custom : kind.fallback;
            if (key != null) {
                p.playSound(Sound.sound(key, source, volume, pitch), at.getX(), at.getY(), at.getZ());
            }
        }
    }
}
