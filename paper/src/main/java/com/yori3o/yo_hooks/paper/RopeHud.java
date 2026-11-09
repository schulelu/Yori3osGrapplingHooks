package com.yori3o.yo_hooks.paper;

import com.yori3o.yo_hooks.paper.Settings.Control;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * The action bar while a hook is out (not in the mod): how far the hook flew or how long the rope is, out of the
 * range, and the rope controls. Keys are keybind components, so every player sees the key they bound.
 */
final class RopeHud {

    /** Sent again after this many ticks even when nothing changed; the client shows an action bar for 3 s. */
    private static final int REFRESH = 30;

    private RopeHud() {
    }

    /** Show the hook's state, if it changed (to 0.1 blocks) or the last message is about to fade. */
    static void show(Player p, Hook h, Settings cfg, long tick) {
        double value = h.attached ? h.length : h.location().distance(p.getLocation());
        String key = (h.attached ? "rope " : "hook ") + Math.round(value * 10);
        if (key.equals(h.hudKey) && tick - h.hudTick < REFRESH) {
            return;
        }
        h.hudKey = key;
        h.hudTick = tick;
        p.sendActionBar(text(h, value, cfg));
    }

    static void clear(Player p) {
        p.sendActionBar(Component.empty());
    }

    private static Component text(Hook h, double value, Settings cfg) {
        TextComponent.Builder b = Component.text()
            .append(Component.text(h.attached ? "Rope " : "Hook ", NamedTextColor.GRAY))
            .append(Component.text(String.format(Locale.ROOT, "%.1f", value), NamedTextColor.WHITE))
            .append(Component.text(" / " + h.maxRange, NamedTextColor.GRAY));
        if (h.attached) {
            hint(b, cfg.climbUp, cfg.scrollToClimb, "up");
            hint(b, cfg.climbDown, cfg.scrollToClimb, "down");
            hint(b, cfg.jumpOff, false, "jump");
            hint(b, cfg.letGo, false, "let go");
        }
        return b.build();
    }

    private static void hint(TextComponent.Builder b, Control control, boolean wheel, String action) {
        String bind = control.keybind();
        if (bind == null && !wheel) {
            return;
        }
        b.append(Component.text("   ["));
        b.append(bind != null ? Component.keybind(bind, NamedTextColor.YELLOW) : Component.text("Wheel", NamedTextColor.YELLOW));
        b.append(Component.text("] " + action, NamedTextColor.GRAY));
    }
}
