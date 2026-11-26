package com.reflexguard;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.SkillContainer;
import yesman.epicfight.skill.SkillDataKeys;
import yesman.epicfight.skill.SkillSlots;
import yesman.epicfight.skill.dodge.DodgeSkill;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.skill.guard.ParryingSkill;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;

/**
 * Drives Epic Fight's OWN guard/parry and dodge skills for the player, exactly as if the keys were pressed:
 * the real animations, stamina cost, parry stagger and Epic Fight events (so other Epic Fight mods react too).
 * Runs before Epic Fight handles the hit, so it can start the move in time.
 */
public class EfGuard {
    private static final org.apache.logging.log4j.Logger LOGGER = org.apache.logging.log4j.LogManager.getLogger("reflexguard");
    private static final String HOLD_KEY = "reflexguard_hold_until";
    private final Guard fallback = new Guard();
    /** set by epicDodge: the equipped skill is not a plain DodgeSkill and will do the dodge itself, so the hit must not be cancelled here */
    private static boolean skillOwned = false;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide) return;
        if (!Cfg.ENABLED.get() || p.isCreative() || p.isSpectator()) return;

        DamageSource src = e.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity attacker = src.getEntity();
        Entity direct = src.getDirectEntity();
        boolean explosion = src.is(DamageTypeTags.IS_EXPLOSION);
        if (attacker == null && direct == null && !explosion) return;
        if (attacker == p) return;
        if (attacker != null && !Cfg.IGNORED_ATTACKERS.get().isEmpty()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(attacker.getType());
            if (id != null && Cfg.IGNORED_ATTACKERS.get().contains(id.toString())) return;
        }

        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        if (pp == null || !pp.isEpicFightMode()) {
            if (Cfg.FALLBACK_GENERIC.get()) fallback.onAttack(e);
            return;
        }

        long now = p.level().getGameTime();
        Entity from = attacker != null ? attacker : direct;
        boolean projectile = direct instanceof Projectile;
        boolean melee = !projectile && attacker instanceof LivingEntity && direct == attacker;
        boolean blockable = (melee || projectile) && !explosion && !src.is(DamageTypeTags.BYPASSES_ARMOR);

        SkillContainer gc = pp.getSkill(SkillSlots.GUARD);
        Skill gs = gc == null ? null : gc.getSkill();

        // Safe window after a perfect dodge: ignore follow-up hits (combos)
        if (now < p.getPersistentData().getLong("reflexguard_safe_until")) { e.setCanceled(true); return; }

        // Already in our automatic guard stance: keep parrying follow-up hits.
        if (gs instanceof GuardSkill && pp.isHoldingSkill(gs) && p.getPersistentData().getLong(HOLD_KEY) > now) {
            arm(gc, gs, p, now);
            return;
        }

        boolean front = true;
        if (from != null) {
            Vec3 look = p.getViewVector(1.0F);
            Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
            Vec3 to = new Vec3(from.getX() - p.getX(), 0, from.getZ() - p.getZ()).normalize();
            front = flat.dot(to) > 0.0D;
        }
        debug("hit from " + (from == null ? "?" : from.getType().toShortString()) + " front=" + front + " holding=" + pp.isHoldingAny() + " canSkill=" + pp.getEntityState().canUseSkill() + " guard=" + (gs == null ? "none" : gs.getClass().getSimpleName()));
        boolean busy = !pp.isHoldingAny() && !pp.getEntityState().canUseSkill();   // attacking / mid-animation

        // Perfect dodge: hits from behind, or while you are attacking / busy, cannot be parried, so they are dodged for free
        if (Cfg.PERFECT_DODGE.get() && (!front || busy) && p.getRandom().nextInt(100) < Cfg.DODGE_CHANCE.get()
                && epicDodge(p, pp, src, from, now)) {
            if (!skillOwned) e.setCanceled(true);
            return;
        }

        // 1) Parry / guard with Epic Fight's own skill
        if (Cfg.PARRY.get() && p.getRandom().nextInt(100) < Cfg.PARRY_CHANCE.get()
                && blockable && gs instanceof GuardSkill && !pp.isHoldingAny()) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            boolean held = gc.requestHold(pp, buf);
            debug("parry requestHold=" + held);
            if (held) {
                arm(gc, gs, p, now);
                return;
            }
        }

        // 2) Everything else is a perfect dodge too (free, invulnerable, with Epic Fight's dodge animation)
        if (Cfg.DODGE.get() && p.getRandom().nextInt(100) < Cfg.DODGE_CHANCE.get() && epicDodge(p, pp, src, from, now)) {
            if (!skillOwned) e.setCanceled(true);
            return;
        }

        // 3) Epic Fight could not do it
        if (now < p.getPersistentData().getLong("reflexguard_safe_until")) { e.setCanceled(true); return; }
        if (Cfg.FALLBACK_GENERIC.get()) fallback.onAttack(e);
    }

    /**
     * Epic Fight's own Dodge skill (your equipped roll/step), the same way the parry uses Epic Fight's own Guard skill.
     * First the normal route (like pressing the dodge key). If you are busy (attacking), Epic Fight would refuse, so the
     * same skill is executed directly, which swaps your attack for the dodge animation. Returns false if no Dodge skill is equipped.
     */
    private static boolean epicDodge(ServerPlayer p, ServerPlayerPatch pp, DamageSource src, Entity from, long now) {
        skillOwned = false;
        SkillContainer dc = pp.getSkill(SkillSlots.DODGE);
        if (dc != null && dc.getSkill() != null) debug("dodge slot skill = " + chain(dc.getSkill().getClass()));
        float yaw = from != null ? yawTo(p, from) : p.getYRot();
        if (pp.isHoldingAny()) pp.resetHolding();

        if (dc != null && dc.getSkill() instanceof DodgeSkill ds) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeInt(1);          // backward animation while facing the attacker = move away
            buf.writeFloat(yaw);
            boolean ok = dc.requestCasting(pp, buf);
            if (!ok && !pp.isInAir()) {
                FriendlyByteBuf forced = new FriendlyByteBuf(Unpooled.buffer());
                forced.writeInt(1);
                forced.writeFloat(yaw);
                try {
                    ds.executeOnServer(dc, forced);
                } catch (Throwable t) {
                    debug("equipped dodge skill " + ds.getClass().getName() + " could not be forced: " + t);
                }
            }
            debug("dodge with equipped skill " + ds.getClass().getSimpleName() + " normalRoute=" + ok);
        } else if (dc != null && dc.getSkill() != null) {
            // Another kind of Dodge-slot skill (for example "Dodge Master"). Press its key and let ITS OWN code dodge the hit
            // (with its own animation). If it does not dodge by itself, onHurt() below takes over.
            boolean ok = false;
            try {
                ok = dc.requestCasting(pp, new FriendlyByteBuf(Unpooled.buffer()));
            } catch (Throwable t) {
                debug("pressing the equipped dodge skill failed: " + t);
            }
            debug("dodge with equipped skill " + dc.getSkill().getClass().getSimpleName() + " pressed=" + ok);
            if (ok) {
                skillOwned = true;
                p.getPersistentData().putLong("reflexguard_skill_dodge", now);
                return true;
            }
            if (!pp.isInAir()) {
                pp.playAnimationSynchronized(Animations.BIPED_ROLL_BACKWARD, 0.0F);
                pp.setModelYRot(yaw, true);
                debug("equipped dodge skill could not be pressed: built-in roll instead");
            }
        } else if (Cfg.DODGE_WITHOUT_SKILL.get() && !pp.isInAir()) {
            // No Dodge skill equipped (skill-tree packs start without one): use Epic Fight's own roll animation.
            pp.playAnimationSynchronized(Animations.BIPED_ROLL_BACKWARD, 0.0F);
            pp.setModelYRot(yaw, true);
            debug("dodge with Epic Fight built-in roll (no dodge skill equipped)");
        } else {
            debug("dodge in air: hit cancelled, no animation");
        }
        p.getPersistentData().putLong("reflexguard_safe_until", now + Cfg.DODGE_IFRAMES.get());
        pp.onDodgeSuccess(src, from != null ? from.position() : p.position());
        return true;
    }

    /** the equipped skill did not dodge the hit itself: cancel it and use the built-in roll */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide) return;
        long t = p.getPersistentData().getLong("reflexguard_skill_dodge");
        if (t == 0L) return;
        long now = p.level().getGameTime();
        p.getPersistentData().putLong("reflexguard_skill_dodge", 0L);
        if (now - t > 2L) return;
        e.setCanceled(true);
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        if (pp != null && !pp.isInAir()) pp.playAnimationSynchronized(Animations.BIPED_ROLL_BACKWARD, 0.0F);
        p.getPersistentData().putLong("reflexguard_safe_until", now + Cfg.DODGE_IFRAMES.get());
        debug("the equipped dodge skill did not dodge the hit by itself: cancelled it and rolled instead");
    }

    private static String chain(Class<?> c) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(k.getName());
        }
        return sb.toString();
    }

    private static void debug(String msg) {
        if (Cfg.DEBUG.get()) LOGGER.info("[ReflexGuard] " + msg);
    }

    private static void arm(SkillContainer gc, Skill gs, ServerPlayer p, long now) {
        if (gs instanceof ParryingSkill) {
            gc.getDataManager().setDataSync(SkillDataKeys.LAST_ACTIVE.get(), p.tickCount);
        }
        p.getPersistentData().putLong(HOLD_KEY, now + Cfg.GUARD_HOLD_TICKS.get());
    }

    private static float yawTo(ServerPlayer p, Entity from) {
        double dx = from.getX() - p.getX();
        double dz = from.getZ() - p.getZ();
        return Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F);
    }

    /** Let go of the automatic guard once the hold time is over (like releasing the key). */
    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        long until = p.getPersistentData().getLong(HOLD_KEY);
        if (until == 0L || p.level().getGameTime() < until) return;
        p.getPersistentData().putLong(HOLD_KEY, 0L);
        ServerPlayerPatch pp = EpicFightCapabilities.getEntityPatch(p, ServerPlayerPatch.class);
        if (pp == null) return;
        SkillContainer gc = pp.getSkill(SkillSlots.GUARD);
        if (gc != null && gc.getSkill() instanceof GuardSkill gs && pp.isHoldingSkill(gs)) {
            gs.cancelOnServer(gc, null);
        }
    }
}
