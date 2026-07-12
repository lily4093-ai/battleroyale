package com.example.battleroyale.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.Arrays;
import java.util.List;

/**
 * Server-side config, equivalent to the original plugin's config.yml.
 * Lives in config/battleroyale-server.toml on the dedicated server.
 */
public final class BRConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue MAX_HEALTH;
    public static final ForgeConfigSpec.DoubleValue DAMAGE_MULTIPLIER;

    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> BORDER_SIZES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> BORDER_COUNTDOWN_TIMES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> BORDER_SPEED;

    public static final ForgeConfigSpec.IntValue SUPPLY_DROP_INTERVAL_MINUTES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SUPPLY_DROP_ITEMS;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DEFAULT_ITEMS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("game");
        MAX_HEALTH = builder
                .comment("Max health (and respawn health) for players during a game.")
                .defineInRange("max_health", 40.0, 1.0, 1024.0);
        DAMAGE_MULTIPLIER = builder
                .comment("Multiplier applied to all incoming player damage during a game.")
                .defineInRange("damage_multiplier", 1.0, 0.0, 100.0);
        builder.pop();

        builder.push("border");
        BORDER_SIZES = builder
                .comment("Border size (diameter, in blocks) for each shrink phase, in order.")
                .defineList("sizes", Arrays.asList(2500.0, 2000.0, 1500.0, 1000.0, 500.0, 250.0, 100.0, 50.0),
                        o -> o instanceof Double);
        BORDER_COUNTDOWN_TIMES = builder
                .comment("Wait time (seconds) before each border shrink starts, in order.")
                .defineList("countdown_times", Arrays.asList(300, 240, 180, 120, 90, 60, 30),
                        o -> o instanceof Integer);
        BORDER_SPEED = builder
                .comment("Border shrink speed (blocks/second) for each phase, in order.")
                .defineList("speed", Arrays.asList(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0),
                        o -> o instanceof Double);
        builder.pop();

        builder.push("supply_drop");
        SUPPLY_DROP_INTERVAL_MINUTES = builder
                .comment("Minutes between supply drop chest spawns.")
                .defineInRange("interval_minutes", 4, 1, 1440);
        SUPPLY_DROP_ITEMS = builder
                .comment("Items placed in supply drop chests. Format: ITEM_ID:MIN-MAX:ENCHANT=LEVEL,ENCHANT=LEVEL")
                .defineListAllowEmpty("items", Arrays.asList(
                        "diamond_chestplate:1:protection=4",
                        "diamond_leggings:1:protection=4",
                        "diamond_boots:1:protection=4",
                        "diamond_helmet:1:protection=4",
                        "ender_pearl:4-8",
                        "splash_potion:1-2",
                        "tnt:5-10"
                ), o -> o instanceof String);
        builder.pop();

        DEFAULT_ITEMS = builder
                .comment("Items given to every player when a game starts. Format: ITEM_ID:AMOUNT:ENCHANT=LEVEL,ENCHANT=LEVEL",
                        "The 3rd segment may also be a raw NBT compound (e.g. {BlockId:\"tacz:attachment_workbench\"}) applied as-is.")
                .defineListAllowEmpty("default_items", Arrays.asList(
                        "diamond_axe:1:{Damage:0,Enchantments:[{id:\"minecraft:efficiency\",lvl:3s},{id:\"minecraft:unbreaking\",lvl:3s}]}",
                        "diamond_pickaxe:1:{Damage:0,Enchantments:[{id:\"minecraft:efficiency\",lvl:3s},{id:\"minecraft:unbreaking\",lvl:3s}]}",
                        "bread:64",
                        "tacz:gun_smith_table:1",
                        "tacz:workbench_c:1:{BlockId:\"tacz:attachment_workbench\"}",
                        "tacz:workbench_a:1:{BlockId:\"tacz:ammo_workbench\"}"
                ), o -> o instanceof String);

        SPEC = builder.build();
    }

    private BRConfig() {
    }
}
