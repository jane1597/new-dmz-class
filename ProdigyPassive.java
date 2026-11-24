package com.dmzprodigy;

import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.passives.IClassPassive;
import com.dragonminez.common.passives.handlers.BerserkerPassive;
import com.dragonminez.common.passives.handlers.ClericPassive;
import com.dragonminez.common.passives.handlers.MartialArtistPassive;
import com.dragonminez.common.passives.handlers.SpiritualistPassive;
import com.dragonminez.common.passives.handlers.TankPassive;
import com.dragonminez.common.passives.handlers.UnversedPassive;
import com.dragonminez.common.passives.handlers.WarriorPassive;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.techniques.KiAttackData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * The Prodigy passive: runs the real passive of every other class at the same time.
 * Multipliers are multiplied together, flat bonuses are added. (Paladin is handled in ProdigyClass.)
 */
public class ProdigyPassive implements IClassPassive {
    public static final String KEY = "prodigy";

    private final List<IClassPassive> parts = new ArrayList<>();
    private List<String> built = null;

    @Override
    public String classKey() { return KEY; }

    /** rebuilt when the config list changes */
    private List<IClassPassive> parts() {
        List<? extends String> want = ProdigyCfg.PASSIVES.get();
        if (built == null || !built.equals(want)) {
            parts.clear();
            for (String s : want) {
                switch (s.trim().toLowerCase()) {
                    case "warrior" -> parts.add(new WarriorPassive());
                    case "martialartist" -> parts.add(new MartialArtistPassive());
                    case "spiritualist" -> parts.add(new SpiritualistPassive());
                    case "berserker" -> parts.add(new BerserkerPassive());
                    case "tank" -> parts.add(new TankPassive());
                    case "cleric" -> parts.add(new ClericPassive());
                    case "unversed" -> parts.add(new UnversedPassive());
                    default -> { } // "paladin" is done in ProdigyClass
                }
            }
            built = new ArrayList<>(want);
        }
        return parts;
    }

    @Override
    public void onPlayerSecond(ServerPlayer player, StatsData data) {
        for (IClassPassive p : parts()) p.onPlayerSecond(player, data);
    }

    // Mastery bonus: like the Mutant bonus it is a separate multiplier, so it stacks with potions and racial bonuses.
    // Every tick we look at what DragonMineZ added to the player's form masteries and add the bonus % of that on top.
    private final Map<UUID, Map<String, Double>> lastMastery = new HashMap<>();

    @Override
    public void onPlayerTick(ServerPlayer player, StatsData data) {
        for (IClassPassive p : parts()) p.onPlayerTick(player, data);
        masteryBonus(player, data);
    }

    private void masteryBonus(ServerPlayer player, StatsData data) {
        double bonus = ProdigyCfg.MASTERY_BONUS.get();
        if (bonus <= 0.0 || data == null || data.getCharacter() == null) return;
        var ch = data.getCharacter();
        Map<String, Double> prev = lastMastery.get(player.getUUID());
        Map<String, Double> now = new HashMap<>();
        boolean first = prev == null;
        String race = ch.getRaceName();
        scan(ch.getFormMasteries(), false, race, bonus, first ? null : prev, now);
        scan(ch.getStackFormMasteries(), true, race, bonus, first ? null : prev, now);
        lastMastery.put(player.getUUID(), now);
    }

    private static void scan(com.dragonminez.common.stats.extras.FormMasteries fm, boolean stack, String race, double bonus,
                             Map<String, Double> prev, Map<String, Double> now) {
        if (fm == null) return;
        for (Map.Entry<String, Double> en : fm.entries().entrySet()) {
            String id = (stack ? "s|" : "f|") + en.getKey();
            double cur = en.getValue();
            Double old = prev == null ? null : prev.get(id);
            if (old != null && cur > old + 1.0e-9) {
                int c = en.getKey().indexOf(':');
                if (c > 0) {
                    String group = en.getKey().substring(0, c), form = en.getKey().substring(c + 1);
                    var fd = stack ? ConfigManager.getStackForm(group, form) : ConfigManager.getForm(race, group, form);
                    double max = fd != null ? fd.getMaxMastery() : 100.0;
                    fm.addMastery(group, form, (cur - old) * bonus, max);
                    cur = fm.entries().getOrDefault(en.getKey(), cur);
                }
            }
            now.put(id, cur);
        }
    }

    @Override
    public void onMeleeHit(ServerPlayer attacker, StatsData data, LivingEntity target, boolean blocked) {
        for (IClassPassive p : parts()) p.onMeleeHit(attacker, data, target, blocked);
    }

    @Override
    public double staminaRegenMultiplier(StatsData data) {
        double m = 1.0;
        for (IClassPassive p : parts()) m *= p.staminaRegenMultiplier(data);
        return m;
    }

    @Override
    public double healthRegenMultiplier(StatsData data) {
        double m = 1.0;
        for (IClassPassive p : parts()) m *= p.healthRegenMultiplier(data);
        return m;
    }

    @Override
    public double bonusHpRegenFromStamina(StatsData data) {
        double s = 0.0;
        for (IClassPassive p : parts()) s += p.bonusHpRegenFromStamina(data);
        return s;
    }

    @Override
    public double armorPenBonus(StatsData data) {
        double s = 0.0;
        for (IClassPassive p : parts()) s += p.armorPenBonus(data);
        return s;
    }

    @Override
    public double critChanceBonus(StatsData data) {
        double s = 0.0;
        for (IClassPassive p : parts()) s += p.critChanceBonus(data);
        return s;
    }

    @Override
    public double strikeDamageMultiplier(StatsData attacker, LivingEntity target) {
        double m = 1.0;
        for (IClassPassive p : parts()) m *= p.strikeDamageMultiplier(attacker, target);
        return m;
    }

    @Override
    public double healingReceivedMultiplier(StatsData data) {
        double m = 1.0;
        for (IClassPassive p : parts()) m *= p.healingReceivedMultiplier(data);
        return m;
    }

    @Override
    public double kiCooldownMultiplier(StatsData data, KiAttackData ki) {
        double m = 1.0;
        for (IClassPassive p : parts()) m *= p.kiCooldownMultiplier(data, ki);
        return m;
    }

    @Override
    public double secondaryDurationMultiplier(StatsData data, KiAttackData ki) {
        double m = 1.0;
        for (IClassPassive p : parts()) m *= p.secondaryDurationMultiplier(data, ki);
        return m;
    }

    @Override
    public double kiDamageMultiplier(StatsData data, KiAttackData ki) {
        double m = 1.0;
        for (IClassPassive p : parts()) m *= p.kiDamageMultiplier(data, ki);
        return m;
    }

    @Override
    public void onKiAttackFired(ServerPlayer player, StatsData data, KiAttackData ki) {
        for (IClassPassive p : parts()) p.onKiAttackFired(player, data, ki);
    }

    @Override
    public void onStrikeCast(ServerPlayer player, StatsData data) {
        for (IClassPassive p : parts()) p.onStrikeCast(player, data);
    }

    @Override
    public double outgoingDamageMultiplier(ServerPlayer attacker, StatsData data, LivingEntity target, DMZEvent.DamageSourceType type) {
        double m = 1.0;
        for (IClassPassive p : parts()) m *= p.outgoingDamageMultiplier(attacker, data, target, type);
        return m;
    }
}
