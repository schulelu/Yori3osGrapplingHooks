package com.yori3o.yo_hooks.paper;

import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.jetbrains.annotations.Nullable;

/**
 * The extra pool the mod injects into vanilla chests on 26.2 (src/pre26.3/.../loot_table/minecraft/*.json):
 * one roll, weighted entries, an empty entry where the mod has one.
 */
public final class LootTables {

    public enum Enchanting { NONE, RANDOM, LEVELS }

    /**
     * One weighted entry. {@code keepMin/keepMax}: vanilla set_damage, the share of durability left (1 = new).
     * {@code enchantChance}: chance of enchant_randomly; {@code levelsMin/Max}: enchant_with_levels.
     */
    public record Entry(Tier tier, int weight, double keepMin, double keepMax, Enchanting enchanting, double enchantChance,
                        int levelsMin, int levelsMax) {

        static Entry of(Tier tier, int weight) {
            return new Entry(tier, weight, 1, 1, Enchanting.NONE, 0, 0, 0);
        }

        Entry keep(double min, double max) {
            return new Entry(tier, weight, min, max, enchanting, enchantChance, levelsMin, levelsMax);
        }

        Entry random(double chance) {
            return new Entry(tier, weight, keepMin, keepMax, Enchanting.RANDOM, chance, 0, 0);
        }

        Entry levels(int min, int max) {
            return new Entry(tier, weight, keepMin, keepMax, Enchanting.LEVELS, 1, min, max);
        }
    }

    public record Table(int emptyWeight, List<Entry> entries) {
        int totalWeight() {
            return emptyWeight + entries.stream().mapToInt(Entry::weight).sum();
        }
    }

    /** Keyed by the vanilla loot table path, e.g. "chests/abandoned_mineshaft". */
    public static final Map<String, Table> TABLES = Map.of(
        "chests/abandoned_mineshaft", new Table(6, List.of(
            Entry.of(Tier.DIAMOND, 3).keep(0.15, 0.35), Entry.of(Tier.IRON, 6).keep(0.2, 0.5))),
        "chests/spawn_bonus_chest", new Table(0, List.of(Entry.of(Tier.GOLD, 1), Entry.of(Tier.IRON, 1))),
        "chests/jungle_temple", new Table(2, List.of(
            Entry.of(Tier.GOLD, 2).keep(0.4, 0.8), Entry.of(Tier.IRON, 1).keep(0.3, 0.6))),
        "chests/ruined_portal", new Table(3, List.of(Entry.of(Tier.GOLD, 2).random(1))),
        "chests/bastion_other", new Table(3, List.of(Entry.of(Tier.GOLD, 1).keep(0.4, 0.9).random(1))),
        "chests/bastion_treasure", new Table(3, List.of(Entry.of(Tier.NETHERITE, 4).keep(0.15, 0.5))),
        "chests/end_city_treasure", new Table(14, List.of(
            Entry.of(Tier.DIAMOND, 1).levels(20, 39), Entry.of(Tier.IRON, 1).levels(20, 39))),
        "chests/simple_dungeon", new Table(4, List.of(Entry.of(Tier.COPPER, 1).keep(0.6, 0.9).random(0.6))),
        "chests/village/village_weaponsmith", new Table(2, List.of(Entry.of(Tier.IRON, 1))),
        "chests/trial_chambers/intersection_barrel", new Table(0, List.of(
            Entry.of(Tier.DIAMOND, 1).keep(0.2, 0.6).random(1), Entry.of(Tier.IRON, 1).keep(0.4, 0.8).random(1))));

    private LootTables() {
    }

    /** One roll of the pool: an entry, or null for the empty entry. */
    public static @Nullable Entry roll(Table table, RandomGenerator random) {
        int n = random.nextInt(table.totalWeight());
        for (Entry e : table.entries()) {
            n -= e.weight();
            if (n < 0) {
                return e;
            }
        }
        return null;
    }

    /** Vanilla set_damage: damage = floor((1 - share kept) * max durability). */
    public static int damage(double keep, int maxDurability) {
        return (int) Math.floor((1 - keep) * maxDurability);
    }
}
