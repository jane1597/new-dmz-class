package com.reflexguard;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/reflexguard-growth.toml */
public final class GrowthCfg {
    private GrowthCfg() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue CROP;
    public static final ForgeConfigSpec.DoubleValue TREE;
    public static final ForgeConfigSpec.IntValue RADIUS;
    public static final ForgeConfigSpec.IntValue CROP_STAGE_TICKS;
    public static final ForgeConfigSpec.IntValue TREE_STAGE_TICKS;
    public static final ForgeConfigSpec.IntValue COOLDOWN_PERCENT;
    public static final ForgeConfigSpec.DoubleValue BABY;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Plant growth (only around players, inside the radius below)").push("plants");
        CROP = b.comment("Crop growth speed multiplier. 1 = vanilla (this system is off), above 1 = timed even growth, 3 = three times faster. Covers wheat, carrots, potatoes, beetroot, melon/pumpkin stems, nether wart, cocoa, sweet berries, sugar cane, cactus, bamboo, kelp and modded crops that use the crops tag.")
                .defineInRange("cropMultiplier", 3.0, 1.0, 50.0);
        TREE = b.comment("Tree (sapling) growth speed multiplier. 1 = vanilla (off), above 1 = timed growth.")
                .defineInRange("treeMultiplier", 3.0, 1.0, 50.0);
        RADIUS = b.comment("Only plants within this many chunks of a player are sped up (bigger = more lag).")
                .defineInRange("radiusChunks", 4, 1, 12);
        CROP_STAGE_TICKS = b.comment("How many ticks one growth stage takes at 1x (20 ticks = 1 second). The real time is this divided by the multiplier. Every crop has its own timer, so crops planted together finish together.")
                .defineInRange("cropStageTicks", 3000, 20, 1000000);
        TREE_STAGE_TICKS = b.comment("Same for saplings (a sapling needs two steps to become a tree).")
                .defineInRange("treeStageTicks", 4000, 20, 1000000);
        b.pop();

        b.comment("Animals").push("animals");
        COOLDOWN_PERCENT = b.comment("Breeding cooldown as a percent of vanilla. 100 = vanilla (5 min), 20 = five times shorter, 0 = no cooldown.")
                .defineInRange("breedingCooldownPercent", 20, 0, 100);
        BABY = b.comment("Baby growth speed multiplier. 1 = vanilla (20 min), 5 = five times faster.")
                .defineInRange("babyGrowthMultiplier", 5.0, 1.0, 1000.0);
        b.pop();
        SPEC = b.build();
    }
}
