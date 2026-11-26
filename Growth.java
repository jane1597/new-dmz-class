package com.reflexguard;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Even, timed plant growth (every crop/sapling near a player has its own timer, so plants put down together
 * finish together, none gets stuck) plus faster animal breeding cooldown and baby growth.
 */
public class Growth {
    private static final int PROCESS_EVERY = 5;       // ticks
    private static final int SCAN_PER_TICK = 2;       // chunks

    private static final class LevelData {
        final Long2IntOpenHashMap timers = new Long2IntOpenHashMap();
        final LongOpenHashSet active = new LongOpenHashSet();
        final ArrayDeque<Long> queue = new ArrayDeque<>();
    }

    private final Map<ResourceKey<Level>, LevelData> data = new HashMap<>();

    private static int steps(double x, RandomSource r) {
        int i = (int) x;
        return i + (r.nextDouble() < x - i ? 1 : 0);
    }

    /** 0 none, 1 crop, 2 tree */
    private static int category(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof SaplingBlock || s.is(BlockTags.SAPLINGS)) return 2;
        if (b instanceof CropBlock || b instanceof StemBlock || b instanceof SweetBerryBushBlock || b instanceof NetherWartBlock
                || b instanceof CocoaBlock || b instanceof SugarCaneBlock || b instanceof CactusBlock || b instanceof BambooStalkBlock
                || b instanceof BambooSaplingBlock || b instanceof GrowingPlantHeadBlock || s.is(BlockTags.CROPS)) return 1;
        return 0;
    }

    private static boolean wanted(BlockState s) {
        int c = category(s);
        return (c == 1 && GrowthCfg.CROP.get() > 1.0) || (c == 2 && GrowthCfg.TREE.get() > 1.0);
    }

    private static IntegerProperty ageProp(BlockState s) {
        for (Property<?> p : s.getProperties()) {
            if (p instanceof IntegerProperty ip && ip.getName().equals("age")) return ip;
        }
        return null;
    }

    private static boolean randomOnly(Block b) {
        return b instanceof SugarCaneBlock || b instanceof CactusBlock || b instanceof BambooStalkBlock
                || b instanceof BambooSaplingBlock || b instanceof GrowingPlantHeadBlock;
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel level)) return;
        if (GrowthCfg.CROP.get() <= 1.0 && GrowthCfg.TREE.get() <= 1.0) return;
        LevelData d = data.computeIfAbsent(level.dimension(), k -> new LevelData());
        long t = level.getGameTime();

        if (t % 20 == 0) refresh(level, d);
        if (d.active.isEmpty()) return;

        for (int i = 0; i < SCAN_PER_TICK; i++) {
            Long key = d.queue.poll();
            if (key == null) break;
            scan(level, d, key);
        }
        if (t % PROCESS_EVERY == 0) process(level, d);
    }

    private void refresh(ServerLevel level, LevelData d) {
        d.active.clear();
        int radius = GrowthCfg.RADIUS.get();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            ChunkPos pc = p.chunkPosition();
            for (int dx = -radius; dx <= radius; dx++)
                for (int dz = -radius; dz <= radius; dz++)
                    d.active.add(ChunkPos.asLong(pc.x + dx, pc.z + dz));
        }
        if (d.queue.isEmpty()) {
            for (long k : d.active) d.queue.add(k);
        }
    }

    private void scan(ServerLevel level, LevelData d, long chunkKey) {
        int cx = ChunkPos.getX(chunkKey), cz = ChunkPos.getZ(chunkKey);
        LevelChunk ch = level.getChunkSource().getChunkNow(cx, cz);
        if (ch == null) return;
        LevelChunkSection[] secs = ch.getSections();
        for (int i = 0; i < secs.length; i++) {
            LevelChunkSection sec = secs[i];
            if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(Growth::wanted)) continue;
            int baseY = SectionPos.sectionToBlockCoord(ch.getSectionYFromSectionIndex(i));
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        BlockState s = sec.getBlockState(x, y, z);
                        if (wanted(s)) {
                            d.timers.putIfAbsent(new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z).asLong(), 0);
                        }
                    }
        }
    }

    private void process(ServerLevel level, LevelData d) {
        double cm = GrowthCfg.CROP.get(), tm = GrowthCfg.TREE.get();
        int cropBase = GrowthCfg.CROP_STAGE_TICKS.get(), treeBase = GrowthCfg.TREE_STAGE_TICKS.get();
        RandomSource rnd = level.random;
        ObjectIterator<Long2IntMap.Entry> it = d.timers.long2IntEntrySet().fastIterator();
        while (it.hasNext()) {
            Long2IntMap.Entry en = it.next();
            BlockPos pos = BlockPos.of(en.getLongKey());
            long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
            if (!d.active.contains(ck)) continue;
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) { it.remove(); continue; }
            BlockState st = level.getBlockState(pos);
            int cat = category(st);
            if (cat == 0 || (cat == 1 && cm <= 1.0) || (cat == 2 && tm <= 1.0)) { it.remove(); continue; }

            Block b = st.getBlock();
            IntegerProperty age = ageProp(st);
            boolean ageStep = cat == 1 && age != null && !randomOnly(b);
            boolean mature = ageStep && st.getValue(age) >= Collections.max(age.getPossibleValues());
            if (mature && !(b instanceof StemBlock)) { en.setValue(0); continue; }

            int need;
            if (cat == 2) need = treeBase;
            else if (ageStep && !mature) need = cropBase;
            else need = Math.max(1, cropBase / 8);     // random-tick style growth (sugar cane, cactus, bamboo, kelp, mature stems)

            int acc = en.getIntValue() + (int) Math.round(PROCESS_EVERY * (cat == 1 ? cm : tm));
            if (acc < need) { en.setValue(acc); continue; }
            en.setValue(0);

            if (cat == 2) {
                if (b instanceof SaplingBlock sb && level.getMaxLocalRawBrightness(pos.above()) >= 9) {
                    sb.advanceTree(level, pos, st, rnd);
                }
            } else if (ageStep && !mature) {
                if ((b instanceof CropBlock || b instanceof StemBlock) && level.getRawBrightness(pos, 0) < 9) continue;
                level.setBlock(pos, st.setValue(age, st.getValue(age) + 1), 2);
            } else {
                st.randomTick(level, pos, rnd);
            }
        }
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent e) {
        if (!(e.getEntity() instanceof AgeableMob a) || a.level().isClientSide) return;
        int age = a.getAge();
        if (age == 0) return;
        RandomSource r = a.getRandom();
        if (age > 0 && a instanceof Animal) {
            int pct = GrowthCfg.COOLDOWN_PERCENT.get();
            if (pct >= 100) return;
            if (pct <= 0) { a.setAge(0); return; }
            double extra = Math.min(1000.0, 100.0 / pct - 1.0);
            a.setAge(Math.max(0, age - steps(extra, r)));
        } else if (age < 0) {
            double m = GrowthCfg.BABY.get();
            if (m <= 1.0) return;
            a.setAge(Math.min(0, age + steps(m - 1.0, r)));
        }
    }
}
