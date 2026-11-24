package com.dmzprodigy;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/** config/dmzprodigy-common.toml */
public final class ProdigyCfg {
    private ProdigyCfg() {}

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue RELOAD;
    public static final ForgeConfigSpec.IntValue STAT_VALUE;
    public static final ForgeConfigSpec.DoubleValue MASTERY_BONUS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> PASSIVES;
    public static final ForgeConfigSpec.BooleanValue PALADIN_ON;
    public static final ForgeConfigSpec.DoubleValue TP_BONUS;
    public static final ForgeConfigSpec.BooleanValue REGENERATE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Master switch for the Prodigy class.").define("enabled", true);
        RELOAD = b.comment("After adding the class to DragonMineZ's race files, run /dmzreload config so it shows up right away.")
                .define("reloadAfterPatch", true);
        STAT_VALUE = b.comment("Starting points in every stat (STR, SKP, RES, VIT, PWR, ENE) for a NEW Prodigy class entry.",
                "Only used when the class is first written to config/dragonminez/races/<race>/stats.json.",
                "After that, edit the numbers in those files (class \"prodigy\") or delete the entry to make it again.")
                .defineInRange("startingStat", 10, 0, 100000);
        MASTERY_BONUS = b.comment("Extra form mastery gain for Prodigy. 0.5 = +50%. DragonMineZ only supports steps of 0.25, so it is rounded to the nearest step.")
                .defineInRange("formMasteryBonus", 0.5, 0.0, 10.0);
        PASSIVES = b.comment("Which class passives a Prodigy gets. All 8 by default.",
                "Keys: warrior, martialartist, spiritualist, berserker, paladin, tank, cleric, unversed")
                .defineList("passives", List.of("warrior", "martialartist", "spiritualist", "berserker", "paladin", "tank", "cleric", "unversed"),
                        o -> o instanceof String);
        PALADIN_ON = b.comment("Paladin part (party damage shield + party lifesteal) is done by this mod because DragonMineZ only checks for the exact class \"paladin\".",
                "Only matters when 'paladin' is in the passives list. Numbers (redirectPct, lifestealPct) are in the class's passive values in stats.json.")
                .define("paladinPartyEffects", true);
        TP_BONUS = b.comment("Extra TP gain for Prodigy. 0.25 = +25%. DragonMineZ only supports steps of 0.25, so it is rounded to the nearest step.")
                .defineInRange("tpGainBonus", 0.25, 0.0, 10.0);
        REGENERATE = b.comment("true = on the next start, rewrite the Prodigy entry in every race's stats.json from scratch (equal scaling, 10 stats), then set this back to false yourself.")
                .define("regenerateClassEntry", false);
        SPEC = b.build();
    }
}
