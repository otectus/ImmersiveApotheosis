package com.otectus.immersiveapotheosis.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Gameplay configuration, per world and synced to clients. Every read goes through a helper that falls back to the
 * default while no world is loaded.
 */
public final class ServerConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue SOCKET_FLOORS;
    public static final ForgeConfigSpec.IntValue FINE_SOCKET_FLOOR;
    public static final ForgeConfigSpec.IntValue MASTERWORK_SOCKET_FLOOR;
    public static final ForgeConfigSpec.BooleanValue SOCKETS_FROM_SMITHING;
    public static final ForgeConfigSpec.BooleanValue SOCKETS_FROM_TANNING;
    public static final ForgeConfigSpec.BooleanValue SOCKETS_FROM_JEWELRY;

    public static final ForgeConfigSpec.BooleanValue REFUSE_SOCKETED_MELTING;
    public static final ForgeConfigSpec.BooleanValue BINDING_LEATHER_SALVAGE;

    public static final ForgeConfigSpec.BooleanValue JEWELRY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue JEWELRY_RINGS;
    public static final ForgeConfigSpec.BooleanValue JEWELRY_AMULETS;
    public static final ForgeConfigSpec.BooleanValue JEWELRY_BRACELETS;
    public static final ForgeConfigSpec.BooleanValue JEWELRY_CIRCLETS;
    public static final ForgeConfigSpec.ConfigValue<String> JEWELRY_MAX_RARITY;
    public static final ForgeConfigSpec.DoubleValue JEWELRY_AFFIX_VALUE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue JEWELRY_GEM_VALUE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue JEWELRY_MAX_SOCKETS;
    public static final ForgeConfigSpec.IntValue JEWELRY_MAX_ACTIVE_AFFIXES;

    public enum ReforgeFailureCost { DUST_AND_LEVELS, FULL, NONE }
    public enum CuttingFailureCost { BOTH, DUST, MATERIALS, NONE }

    public static final ForgeConfigSpec.BooleanValue PERFORMED_SALVAGING;
    public static final ForgeConfigSpec.BooleanValue PERFORMED_SOCKETING;
    public static final ForgeConfigSpec.BooleanValue PERFORMED_GEM_CUTTING;
    public static final ForgeConfigSpec.BooleanValue PERFORMED_REFORGING;
    public static final ForgeConfigSpec.BooleanValue CREATIVE_BYPASS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_PERMISSION_BYPASS;
    public static final ForgeConfigSpec.DoubleValue TIMING_WINDOW_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue SESSION_GRACE_SECONDS;
    public static final ForgeConfigSpec.IntValue SALVAGE_MIN_OUTPUTS;
    public static final ForgeConfigSpec.DoubleValue REFORGE_FRAYED_RETENTION;
    public static final ForgeConfigSpec.DoubleValue REFORGE_WEAK_RETENTION;
    public static final ForgeConfigSpec.EnumValue<ReforgeFailureCost> REFORGE_FAILURE_COST;
    public static final ForgeConfigSpec.EnumValue<CuttingFailureCost> CUTTING_FAILURE_COST;
    public static final ForgeConfigSpec.DoubleValue CUTTING_PERFECT_DUST_REFUND;
    public static final ForgeConfigSpec.IntValue SOCKETING_FAILURE_DURABILITY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Master craftsmanship leaves room for gems: player-made Fine and Masterwork work always has at least",
                "this many sockets. A floor, never a bonus: reforging and Sigils of Socketing still work as usual, and",
                "floor sockets count toward the sigil maximum. Loot and villager work have no maker and get no floor.").push("sockets");
        SOCKET_FLOORS = b.define("enabled", true);
        FINE_SOCKET_FLOOR = b.comment("Sockets guaranteed for Fine work (Jewelry: Fine and Exceptional).")
                .defineInRange("fineFloor", 1, 0, 3);
        MASTERWORK_SOCKET_FLOOR = b.comment("Sockets guaranteed for Masterwork.").defineInRange("masterworkFloor", 2, 0, 3);
        SOCKETS_FROM_SMITHING = b.comment("Immersive Smithing: pieces with a Maker's Mark that are not Faulty.")
                .define("smithing", true);
        SOCKETS_FROM_TANNING = b.comment("Immersive Tanning: leatherwork made by a player at the bench.").define("tanning", true);
        SOCKETS_FROM_JEWELRY = b.comment("Immersive Jewelry: pieces with a jeweler's name (needs jewelry categories).")
                .define("jewelry", true);
        b.pop();

        b.push("smithing");
        REFUSE_SOCKETED_MELTING = b.comment("The Smith's Forge refuses gear with socketed gems, which melting would destroy.",
                        "Players pry the gems out first with a Sigil of Withdrawal.")
                .define("refuseSocketedMelting", true);
        b.pop();

        b.push("tanning");
        BINDING_LEATHER_SALVAGE = b.comment("Salvaging recipes that yield vanilla leather yield Binding Leather instead,",
                        "so every route to leather still ends at the Tanner's Rack.")
                .define("bindingLeatherSalvage", true);
        b.pop();

        b.comment("Immersive Jewelry pieces take Apotheosis affixes and gems: they reforge, socket and salvage like gear, and",
                "worn pieces apply them through Jewelry's own rules (a broken piece is inert, and only as many pieces of a form",
                "count as Jewelry allows). Defaults budget for five jewelry slots; 1.0 multipliers give gear parity.").push("jewelry");
        JEWELRY_ENABLED = b.comment("Master switch. Off: jewelry has no Apotheosis category, so no affixes, sockets or gems.")
                .define("enabled", true);
        JEWELRY_RINGS = b.define("rings", true);
        JEWELRY_AMULETS = b.define("amulets", true);
        JEWELRY_BRACELETS = b.define("bracelets", true);
        JEWELRY_CIRCLETS = b.define("circlets", true);
        JEWELRY_MAX_RARITY = b.comment("Worn jewelry counts as at most this rarity: a higher-rarity piece's affixes take this",
                        "rarity's values. An Apotheosis rarity id.")
                .define("maxRarity", "apotheosis:epic");
        JEWELRY_AFFIX_VALUE_MULTIPLIER = b.comment("Scales the attribute values of worn jewelry's affixes.")
                .defineInRange("affixValueMultiplier", 0.5, 0.0, 2.0);
        JEWELRY_GEM_VALUE_MULTIPLIER = b.comment("Scales the attribute values of gems socketed in worn jewelry.")
                .defineInRange("gemValueMultiplier", 0.5, 0.0, 2.0);
        JEWELRY_MAX_SOCKETS = b.comment("The most sockets a jewelry piece can have, whatever rolled or was added.")
                .defineInRange("maxSockets", 1, 0, 3);
        JEWELRY_MAX_ACTIVE_AFFIXES = b.comment("How many of a piece's affixes act when worn; the rest are dormant (shown in the tooltip).")
                .defineInRange("maxActiveAffixes", 2, 0, 8);
        b.pop();

        b.comment("Apotheosis's stations, performed. Apotheosis still decides what is rolled; hands-on dismantling, setting,",
                "cutting and arcane metalwork decide how well it lands, never beyond Apotheosis's own bounds. Esc during the",
                "count-in cancels for free.").push("stations");
        PERFORMED_SALVAGING = b.comment("Salvaging Table: your score picks each output inside Apotheosis's own count range.")
                .define("salvaging", true);
        PERFORMED_SOCKETING = b.comment("Socketing at the smithing table: a failed setting returns the gem untouched.")
                .define("socketing", true);
        PERFORMED_GEM_CUTTING = b.comment("Gem Cutting Table: Stable or better upgrades the gem; a failed cut keeps every gem.")
                .define("gemCutting", true);
        PERFORMED_REFORGING = b.comment("Reforging Tables: a weaker performance keeps the rolled affixes at lower values.")
                .define("reforging", true);
        CREATIVE_BYPASS = b.comment("Creative players use the stations instantly.").define("creativeBypass", false);
        ALLOW_PERMISSION_BYPASS = b.comment("Players with the permission immersive_apotheosis.bypass_performances use them instantly.")
                .define("allowPermissionBypass", true);
        TIMING_WINDOW_MULTIPLIER = b.comment("Crafting assistance: above 1 widens spatial, pressure, heat and alignment tolerances.",
                        "The existing key is retained for compatibility; this does not change rewards or costs.")
                .defineInRange("timingWindowMultiplier", 1.0, 0.5, 2.0);
        SESSION_GRACE_SECONDS = b.comment("Seconds after the 60-second work limit that the server waits for remaining input.")
                .defineInRange("sessionGraceSeconds", 5, 1, 30);
        SALVAGE_MIN_OUTPUTS = b.comment("Batches whose best case yields fewer items than this salvage instantly, as in Apotheosis.")
                .defineInRange("salvageMinOutputs", 4, 0, 64);
        REFORGE_FRAYED_RETENTION = b.comment("A Frayed reforge keeps this share of each affix's roll within its rarity's range.")
                .defineInRange("reforgeFrayedRetention", 0.75, 0.0, 1.0);
        REFORGE_WEAK_RETENTION = b.comment("A Weak reforge keeps this share.").defineInRange("reforgeWeakRetention", 0.45, 0.0, 1.0);
        REFORGE_FAILURE_COST = b.comment("A failed reforge applies nothing and re-rolls the choices. DUST_AND_LEVELS charges the gem",
                        "dust and levels but keeps the rarity materials; FULL charges everything; NONE is free.")
                .defineEnum("reforgeFailureCost", ReforgeFailureCost.DUST_AND_LEVELS);
        CUTTING_FAILURE_COST = b.comment("A failed cut keeps both gems and consumes: BOTH gem dust and materials, DUST, MATERIALS or NONE.")
                .defineEnum("gemCuttingFailureCost", CuttingFailureCost.BOTH);
        CUTTING_PERFECT_DUST_REFUND = b.comment("Share of the gem dust a Perfect cut gives back.")
                .defineInRange("gemCuttingPerfectDustRefund", 0.5, 0.0, 1.0);
        SOCKETING_FAILURE_DURABILITY = b.comment("Durability a failed setting costs the item (0: free).")
                .defineInRange("socketingFailureDurability", 0, 0, 1000);
        b.pop();

        SPEC = b.build();
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static int get(ForgeConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static double get(ForgeConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static String get(ForgeConfigSpec.ConfigValue<String> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    public static <T extends Enum<T>> T get(ForgeConfigSpec.EnumValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    private ServerConfig() {}
}
