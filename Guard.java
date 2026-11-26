package com.reflexguard;

import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** The actual logic: every hit that would land on a player is parried or dodged. */
public class Guard {
    private static final String KEY = "reflexguard_safe_until";

    public void onAttack(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide) return;
        if (!Cfg.ENABLED.get() || p.isCreative() || p.isSpectator()) return;

        DamageSource src = e.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;           // void, /kill
        Entity attacker = src.getEntity();
        Entity direct = src.getDirectEntity();
        boolean explosion = src.is(DamageTypeTags.IS_EXPLOSION);
        if (attacker == null && direct == null && !explosion) return;          // fall, fire, drowning, hunger ... are not "attacks"
        if (attacker == p) return;

        if (attacker != null && !Cfg.IGNORED_ATTACKERS.get().isEmpty()) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(attacker.getType());
            if (id != null && Cfg.IGNORED_ATTACKERS.get().contains(id.toString())) return;
        }

        long now = p.level().getGameTime();
        if (now < p.getPersistentData().getLong(KEY)) {                         // still inside the dodge / parry window
            e.setCanceled(true);
            return;
        }

        boolean projectile = direct instanceof Projectile;
        boolean melee = !projectile && attacker instanceof LivingEntity && direct == attacker;
        int roll = p.getRandom().nextInt(100);

        if (Cfg.PARRY.get() && roll < Cfg.PARRY_CHANCE.get()) {
            if (melee && attacker.distanceTo(p) <= Cfg.PARRY_RANGE.get()) {
                parryMelee(p, (LivingEntity) attacker, e.getAmount(), now);
                e.setCanceled(true);
                return;
            }
            if (projectile && Cfg.PARRY_PROJECTILES.get()) {
                deflect(p, (Projectile) direct, now);
                e.setCanceled(true);
                return;
            }
        }
        if (Cfg.DODGE.get() && p.getRandom().nextInt(100) < Cfg.DODGE_CHANCE.get()) {
            dodge(p, attacker != null ? attacker : direct, now);
            e.setCanceled(true);
        }
    }

    private static void safeFor(ServerPlayer p, long now, int ticks) {
        p.getPersistentData().putLong(KEY, now + ticks);
    }

    private static void msg(ServerPlayer p, String text) {
        if (Cfg.SHOW_MESSAGES.get()) p.displayClientMessage(Component.literal(text), true);
    }

    private static void parryMelee(ServerPlayer p, LivingEntity attacker, float amount, long now) {
        safeFor(p, now, Cfg.PARRY_IFRAMES.get());
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.2F, 1.2F);
        level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getEyeY() - 0.3, p.getZ(), 14, 0.4, 0.3, 0.4, 0.2);
        attacker.knockback(Cfg.PARRY_KNOCKBACK.get(), p.getX() - attacker.getX(), p.getZ() - attacker.getZ());
        attacker.hurtMarked = true;
        int stun = Cfg.PARRY_STUN_TICKS.get();
        if (stun > 0) {
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stun, 3));
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, stun, 1));
        }
        double counter = Cfg.PARRY_COUNTER.get();
        if (counter > 0 && amount > 0) {
            attacker.hurt(p.damageSources().playerAttack(p), (float) (amount * counter));
        }
        msg(p, "§e§lPARRY!");
    }

    private static void deflect(ServerPlayer p, Projectile pr, long now) {
        safeFor(p, now, Cfg.PARRY_IFRAMES.get());
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F);
        level.sendParticles(ParticleTypes.CRIT, pr.getX(), pr.getY(), pr.getZ(), 10, 0.2, 0.2, 0.2, 0.2);
        Vec3 v = pr.getDeltaMovement();
        pr.setDeltaMovement(v.scale(-1.0));
        pr.setOwner(p);
        pr.setYRot(pr.getYRot() + 180.0F);
        pr.hurtMarked = true;
        msg(p, "§e§lDEFLECT!");
    }

    private static void dodge(ServerPlayer p, Entity from, long now) {
        safeFor(p, now, Cfg.DODGE_IFRAMES.get());
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.6F);
        level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.3, 0.1, 0.3, 0.02);
        if (Cfg.DODGE_DASH.get() && from != null) {
            Vec3 away = p.position().subtract(from.position());
            Vec3 flat = new Vec3(away.x, 0, away.z);
            if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z).scale(-1);
            flat = flat.normalize();
            Vec3 side = new Vec3(-flat.z, 0, flat.x).scale(p.getRandom().nextBoolean() ? 1 : -1);
            Vec3 push = side.scale(Cfg.DODGE_DASH_POWER.get()).add(flat.scale(Cfg.DODGE_DASH_POWER.get() * 0.4));
            p.setDeltaMovement(p.getDeltaMovement().add(push.x, 0.08, push.z));
            p.hurtMarked = true;
        }
        msg(p, "§b§lDODGE!");
    }
}
