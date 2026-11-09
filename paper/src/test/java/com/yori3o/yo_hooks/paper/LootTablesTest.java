package com.yori3o.yo_hooks.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** The injected chest pools must match the mod's 26.2 loot tables. */
class LootTablesTest {

    @Test
    void tablesMatchTheMod() {
        assertEquals(10, LootTables.TABLES.size());
        LootTables.Table mineshaft = LootTables.TABLES.get("chests/abandoned_mineshaft");
        assertEquals(15, mineshaft.totalWeight());
        assertEquals(0.15, mineshaft.entries().get(0).keepMin());
        LootTables.Table end = LootTables.TABLES.get("chests/end_city_treasure");
        assertEquals(LootTables.Enchanting.LEVELS, end.entries().get(0).enchanting());
        assertEquals(39, end.entries().get(0).levelsMax());
        assertEquals(0.6, LootTables.TABLES.get("chests/simple_dungeon").entries().get(0).enchantChance());
        assertEquals(0, LootTables.TABLES.get("chests/trial_chambers/intersection_barrel").emptyWeight());
    }

    @Test
    void rollFollowsTheWeights() {
        LootTables.Table mineshaft = LootTables.TABLES.get("chests/abandoned_mineshaft");
        SplittableRandom random = new SplittableRandom(42);
        Map<Tier, Integer> seen = new EnumMap<>(Tier.class);
        int empty = 0;
        int rolls = 150_000;
        for (int i = 0; i < rolls; i++) {
            LootTables.Entry e = LootTables.roll(mineshaft, random);
            if (e == null) {
                empty++;
            } else {
                seen.merge(e.tier(), 1, Integer::sum);
            }
        }
        assertEquals(3.0 / 15, seen.get(Tier.DIAMOND) / (double) rolls, 0.01);
        assertEquals(6.0 / 15, seen.get(Tier.IRON) / (double) rolls, 0.01);
        assertEquals(6.0 / 15, empty / (double) rolls, 0.01);
    }

    @Test
    void alwaysAnItemWithoutAnEmptyEntry() {
        SplittableRandom random = new SplittableRandom(7);
        for (int i = 0; i < 1000; i++) {
            assertNotNull(LootTables.roll(LootTables.TABLES.get("chests/spawn_bonus_chest"), random));
        }
        assertNull(LootTables.roll(new LootTables.Table(1, java.util.List.of()), random));
    }

    @Test
    void setDamageKeepsTheGivenShare() {
        assertEquals(0, LootTables.damage(1, 72));
        assertEquals(57, LootTables.damage(0.2, 72)); // 80 % used
        assertEquals(24, LootTables.damage(0, 24));
    }
}
