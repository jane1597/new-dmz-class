package com.reflexguard;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/** Reflex Guard: always-on auto parry and auto dodge. */
@Mod(ReflexGuard.ID)
public class ReflexGuard {
    public static final String ID = "reflexguard";

    public ReflexGuard() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Cfg.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, GrowthCfg.SPEC, "reflexguard-growth.toml");
        MinecraftForge.EVENT_BUS.register(new EfGuard());
        MinecraftForge.EVENT_BUS.register(new Growth());
    }
}
