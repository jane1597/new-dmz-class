package com.reflexguard;

import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

/** config/reflexguard-common.toml (created on first start, every option is explained in the file). */
public final class Cfg {
    private Cfg() {}

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue SHOW_MESSAGES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORED_ATTACKERS;

    public static final ForgeConfigSpec.BooleanValue FALLBACK_GENERIC;
    public static final ForgeConfigSpec.IntValue GUARD_HOLD_TICKS;

    public static final ForgeConfigSpec.BooleanValue DEBUG;
    public static final ForgeConfigSpec.BooleanValue DODGE_WITHOUT_SKILL;
    public static final ForgeConfigSpec.BooleanValue PERFECT_DODGE;
    public static final ForgeConfigSpec.BooleanValue DODGE_ANIMATION;

    public static final ForgeConfigSpec.BooleanValue PARRY;
    public static final ForgeConfigSpec.IntValue PARRY_CHANCE;
    public static final ForgeConfigSpec.DoubleValue PARRY_RANGE;
    public static final ForgeConfigSpec.DoubleValue PARRY_KNOCKBACK;
    public static final ForgeConfigSpec.IntValue PARRY_STUN_TICKS;
    public static final ForgeConfigSpec.DoubleValue PARRY_COUNTER;
    public static final ForgeConfigSpec.BooleanValue PARRY_PROJECTILES;
    public static final ForgeConfigSpec.IntValue PARRY_IFRAMES;

    public static final ForgeConfigSpec.BooleanValue DODGE;
    public static final ForgeConfigSpec.IntValue DODGE_CHANCE;
    public static final ForgeConfigSpec.IntValue DODGE_IFRAMES;
    public static final ForgeConfigSpec.BooleanValue DODGE_DASH;
    public static final ForgeConfigSpec.DoubleValue DODGE_DASH_POWER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("General").push("general");
        ENABLED = b.comment("Master switch. false = the mod does nothing.").define("enabled", true);
        SHOW_MESSAGES = b.comment("Show text above your hotbar (only used by the plain fallback below).").define("showMessages", false);
        DEBUG = b.comment("Write what the mod decides on every hit to the game log (look for [ReflexGuard] in logs/latest.log). Useful if something does not work.").define("debugLog", true);
        DODGE_WITHOUT_SKILL = b.comment("If you have no Dodge skill equipped, play Epic Fight's built-in roll anyway. false = only dodge when a Dodge skill is equipped (the dodge then always matches the skill you equipped: roll, step, or a modded one).").define("dodgeWithoutSkill", true);
        FALLBACK_GENERIC = b.comment("When Epic Fight cannot do the move (no Guard/Dodge skill equipped, not enough stamina, vanilla mode), use the simple built-in parry/dodge instead so the hit is still stopped. false = the hit lands like normal.").define("fallbackGeneric", false);
        GUARD_HOLD_TICKS = b.comment("How long the automatic guard/parry stance is held after an enemy attacks (ticks). Longer = covers combos.").defineInRange("guardHoldTicks", 12, 4, 60);
        IGNORED_ATTACKERS = b.comment("Entity ids that can always hurt you (never parried or dodged), e.g. [\"minecraft:warden\"]. Empty = everything is guarded.")
                .defineListAllowEmpty("ignoredAttackers", List.of(), o -> o instanceof String);
        b.pop();

        PERFECT_DODGE = b.comment("Perfect dodge: hits you cannot parry (from behind, or while you are attacking / mid-animation) are dodged for free with brief invulnerability. Uses the dodge chance and invulnerableTicks of the [dodge] section.").define("perfectDodge", true);

        DODGE_ANIMATION = b.comment("Play Epic Fight's own dodge animation (roll/step) on a perfect dodge. It cancels your current attack. Needs a Dodge skill equipped. false = no animation, just sound and particles.").define("dodgeAnimation", true);

        b.comment("Auto parry: a melee hit (or a projectile) is blocked completely and punished.").push("parry");
        PARRY = b.comment("Turn auto parry on or off.").define("enabled", true);
        PARRY_CHANCE = b.comment("Chance (0-100) that a hit is parried. 100 = always.").defineInRange("chance", 100, 0, 100);
        PARRY_RANGE = b.comment("A melee attacker must be within this many blocks to be parried (bigger = also long-reach bosses).").defineInRange("range", 8.0, 1.0, 32.0);
        PARRY_KNOCKBACK = b.comment("How hard the attacker is pushed back.").defineInRange("knockback", 1.4, 0.0, 10.0);
        PARRY_STUN_TICKS = b.comment("How long the attacker is staggered (slowness + weakness), in ticks. 20 ticks = 1 second.").defineInRange("stunTicks", 40, 0, 600);
        PARRY_COUNTER = b.comment("Counter damage: this fraction of the damage you would have taken is dealt back to the attacker. 0 = none, 1 = the same amount, 2 = double.").defineInRange("counterDamage", 0.5, 0.0, 20.0);
        PARRY_PROJECTILES = b.comment("Deflect projectiles (arrows, fireballs...) back at the shooter instead of just dodging them.").define("deflectProjectiles", true);
        PARRY_IFRAMES = b.comment("Ticks you cannot be hurt after a parry (lets you ignore combo follow-ups).").defineInRange("invulnerableTicks", 10, 0, 100);
        b.pop();

        b.comment("Auto dodge: anything that is not parried is sidestepped.").push("dodge");
        DODGE = b.comment("Turn auto dodge on or off.").define("enabled", true);
        DODGE_CHANCE = b.comment("Chance (0-100) that a hit is dodged. 100 = always.").defineInRange("chance", 100, 0, 100);
        DODGE_IFRAMES = b.comment("Ticks you cannot be hurt after a dodge (dodge i-frames). Multi-hit attacks inside this time all miss.").defineInRange("invulnerableTicks", 14, 0, 100);
        DODGE_DASH = b.comment("Do a small sidestep when you dodge.").define("sidestep", true);
        DODGE_DASH_POWER = b.comment("How far the sidestep carries you.").defineInRange("sidestepPower", 0.6, 0.0, 3.0);
        b.pop();

        SPEC = b.build();
    }
}
