package com.yori3o.yo_hooks.paper;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Blocks a hook breaks when it hits them: the mod's #yo_hooks:fragile tag for 26.2
 * (src/26.2-plus/.../tags/block/fragile.json), with the c:glass_* convention tags expanded to vanilla blocks.
 */
public final class Fragile {

    static final List<String> LISTED = List.of(
        "ice", "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud", "small_amethyst_bud",
        "pointed_dripstone", "flower_pot", "decorated_pot", "turtle_egg", "sniffer_egg", "lily_pad", "big_dripleaf",
        "chorus_plant", "sea_pickle", "bamboo", "cocoa", "scaffolding", "sea_lantern", "glowstone", "end_rod",
        "sulfur_spike");

    private static final Set<Material> BLOCKS = build();

    private Fragile() {
    }

    public static boolean is(Material m) {
        return BLOCKS.contains(m);
    }

    private static Set<Material> build() {
        Set<Material> s = EnumSet.noneOf(Material.class);
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isBlock()) {
                continue;
            }
            String n = m.name();
            // c:glass_blocks and c:glass_panes: plain, tinted and stained glass and panes
            if (n.equals("GLASS") || n.equals("TINTED_GLASS") || n.equals("GLASS_PANE")
                || n.endsWith("_STAINED_GLASS") || n.endsWith("_STAINED_GLASS_PANE")) {
                s.add(m);
            }
        }
        s.addAll(Tag.CANDLES.getValues());
        for (String id : LISTED) {
            Material m = Material.matchMaterial(id);
            if (m != null) {
                s.add(m);
            }
        }
        return s;
    }
}
