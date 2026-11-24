package com.dmzprodigy;

import com.dragonminez.common.combat.SilentDamage;
import com.dragonminez.common.passives.ClassPassives;
import com.dragonminez.common.quest.PartyManager;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Adds the "prodigy" class to DragonMineZ.
 *  - registers the combined passive (ProdigyPassive)
 *  - writes a "prodigy" entry into every config/dragonminez/races/&lt;race&gt;/stats.json that lacks one, then /dmzreload config
 *  - re-creates DragonMineZ's Paladin party effects for Prodigy players
 */
@Mod(ProdigyClass.ID)
public class ProdigyClass {
    public static final String ID = "dmzprodigy";
    private static final Logger LOG = LogManager.getLogger("dmzprodigy");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String[] BASE = {"warrior", "martialartist", "spiritualist", "berserker", "paladin", "tank", "cleric", "unversed"};

    public ProdigyClass() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ProdigyCfg.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(FMLCommonSetupEvent e) {
        // DragonMineZ keeps its passives in a plain map, so this is safe from any thread
        e.enqueueWork(() -> ClassPassives.register(new ProdigyPassive()));
    }

    // ------------------------------------------------------------------ class entry in the config files

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent e) {
        if (!ProdigyCfg.ENABLED.get()) return;
        int changed;
        try {
            changed = patch();
        } catch (Exception ex) {
            LOG.error("[Prodigy] Could not add the class to the DragonMineZ files", ex);
            return;
        }
        LOG.info("[Prodigy] Added the Prodigy class to {} race file(s).", changed);
        if (changed > 0 && ProdigyCfg.RELOAD.get()) {
            MinecraftServer server = e.getServer();
            try {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "dmzreload config");
                LOG.info("[Prodigy] Ran /dmzreload config.");
            } catch (Exception ex) {
                LOG.warn("[Prodigy] Could not run /dmzreload config; restart the game so the class shows up.", ex);
            }
        }
    }

    private static double num(JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber() ? el.getAsDouble() : Double.NaN;
    }

    /** class entry: 10 in every base stat, everything else is the average of the 8 normal classes (a balanced middle) */
    static JsonObject buildClass(JsonObject classes) {
        List<JsonObject> src = new ArrayList<>();
        for (String k : BASE) {
            JsonElement c = classes.get(k);
            if (c != null && c.isJsonObject()) src.add(c.getAsJsonObject());
        }
        if (src.isEmpty()) return null;

        JsonObject out = new JsonObject();
        JsonObject base = new JsonObject();
        for (String s : new String[]{"STR", "SKP", "RES", "VIT", "PWR", "ENE"}) base.addProperty(s, ProdigyCfg.STAT_VALUE.get());
        out.add("baseStats", base);

        // statScaling: average of every key the classes have
        JsonObject sc = new JsonObject();
        for (String key : keys(src, "statScaling")) sc.addProperty(key, avg(src, "statScaling", key));
        out.add("statScaling", sc);

        for (String key : keys(src, null)) {
            if (key.equals("baseStats") || key.equals("statScaling") || key.equals("passive")) continue;
            double v = avg(src, null, key);
            if (!Double.isNaN(v)) out.addProperty(key, v);
        }

        // passive: on, with every number of every class's passive (first one wins when two classes share a name)
        JsonObject passive = new JsonObject();
        passive.addProperty("enabled", true);
        Map<String, Double> vals = new LinkedHashMap<>();
        for (JsonObject c : src) {
            JsonObject p = c.has("passive") && c.get("passive").isJsonObject() ? c.getAsJsonObject("passive") : null;
            JsonObject v = p != null && p.has("values") && p.get("values").isJsonObject() ? p.getAsJsonObject("values") : null;
            if (v == null) continue;
            for (Map.Entry<String, JsonElement> en : v.entrySet()) vals.putIfAbsent(en.getKey(), num(en.getValue()));
        }
        vals.putIfAbsent("redirectPct", 0.15);
        vals.putIfAbsent("lifestealPct", 0.15);
        JsonObject values = new JsonObject();
        for (Map.Entry<String, Double> en : vals.entrySet()) if (!Double.isNaN(en.getValue())) values.addProperty(en.getKey(), en.getValue());
        passive.add("values", values);
        out.add("passive", passive);
        return out;
    }

    private static List<String> keys(List<JsonObject> src, String sub) {
        List<String> out = new ArrayList<>();
        for (JsonObject c : src) {
            JsonObject o = sub == null ? c : c.has(sub) && c.get(sub).isJsonObject() ? c.getAsJsonObject(sub) : null;
            if (o == null) continue;
            for (String k : o.keySet()) {
                if (!Double.isNaN(num(o.get(k))) && !out.contains(k)) out.add(k);
            }
        }
        return out;
    }

    private static double avg(List<JsonObject> src, String sub, String key) {
        double sum = 0;
        int n = 0;
        for (JsonObject c : src) {
            JsonObject o = sub == null ? c : c.has(sub) && c.get(sub).isJsonObject() ? c.getAsJsonObject(sub) : null;
            if (o == null) continue;
            double v = num(o.get(key));
            if (!Double.isNaN(v)) { sum += v; n++; }
        }
        return n == 0 ? Double.NaN : Math.round(sum / n * 10000.0) / 10000.0;
    }

    static int patch() throws IOException {
        Path races = FMLPaths.CONFIGDIR.get().resolve("dragonminez").resolve("races");
        if (!Files.isDirectory(races)) {
            LOG.warn("[Prodigy] {} does not exist yet. Start the world once, then restart.", races);
            return 0;
        }
        List<Path> files;
        try (Stream<Path> w = Files.walk(races)) {
            files = w.filter(p -> p.getFileName().toString().equals("stats.json")).toList();
        }
        int changed = 0;
        for (Path f : files) {
            JsonObject root;
            try {
                JsonElement el = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonElement.class);
                if (el == null || !el.isJsonObject()) continue;
                root = el.getAsJsonObject();
            } catch (Exception ex) {
                LOG.warn("[Prodigy] Could not read {}: {}", f, ex.getMessage());
                continue;
            }
            if (!root.has("classes") || !root.get("classes").isJsonObject()) continue;
            JsonObject classes = root.getAsJsonObject("classes");
            if (classes.has(ProdigyPassive.KEY)) continue;
            JsonObject made = buildClass(classes);
            if (made == null) continue;
            classes.add(ProdigyPassive.KEY, made);
            Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
            changed++;
        }
        return changed;
    }

    // ------------------------------------------------------------------ Paladin party effects (copy of DragonMineZ's, for Prodigy)

    private static StatsData data(ServerPlayer p) {
        return StatsProvider.get(StatsCapability.INSTANCE, p).orElse(null);
    }

    private static boolean paladinOn() {
        return ProdigyCfg.ENABLED.get() && ProdigyCfg.PALADIN_ON.get()
                && ProdigyCfg.PASSIVES.get().stream().anyMatch(s -> s.trim().equalsIgnoreCase("paladin"));
    }

    private static ServerPlayer findPartyProdigy(ServerPlayer member) {
        List<ServerPlayer> party = PartyManager.getAllPartyMembers(member);
        if (party == null || party.size() <= 1) return null;
        for (ServerPlayer c : party) {
            if (c == member || !c.isAlive() || c.level() != member.level()) continue;
            if (c.distanceToSqr(member) > 2500.0) continue;
            StatsData d = data(c);
            if (d != null && ClassPassives.is(d, ProdigyPassive.KEY)) return c;
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || !paladinOn()) return;
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) lifesteal(attacker, event.getEntity(), event.getAmount());
        if (event.getEntity() instanceof ServerPlayer victim) redirect(victim, event);
    }

    private static void lifesteal(ServerPlayer member, LivingEntity victim, float dealt) {
        if (dealt <= 0.0f || dealt < victim.getMaxHealth() * 0.01f) return;
        ServerPlayer prodigy = findPartyProdigy(member);
        if (prodigy == null) return;
        double pct = ClassPassives.value(data(prodigy), "lifestealPct", 0.15);
        if (pct > 0.0) prodigy.heal((float) (dealt * pct));
    }

    private static void redirect(ServerPlayer victim, LivingHurtEvent event) {
        boolean hasRaw = victim.getPersistentData().contains("dmz_raw_damage");
        double raw = hasRaw ? victim.getPersistentData().getDouble("dmz_raw_damage") : event.getAmount();
        if (raw < victim.getMaxHealth() * 0.01f) return;
        ServerPlayer prodigy = findPartyProdigy(victim);
        if (prodigy == null || event.getSource().getEntity() == prodigy) return;
        StatsData pd = data(prodigy);
        double pct = ClassPassives.value(pd, "redirectPct", 0.15);
        if (pct <= 0.0) return;
        double redirect = raw * pct;
        if (redirect <= 0.0) return;
        double taken = pd != null ? pd.calculatePostMitigationDamage(redirect, false, 0.0) : redirect;
        if (!Double.isFinite(taken) || taken < 0.0) return;
        if (taken > 0.0) {
            float available = SilentDamage.available(prodigy, SilentDamage.DEFAULT_MIN_HEALTH);
            if (available <= 0.0F) return;
            if (taken > available) {
                redirect *= available / taken;
                taken = available;
            }
        }
        if (hasRaw) victim.getPersistentData().putDouble("dmz_raw_damage", Math.max(0.0, raw - redirect));
        else event.setAmount((float) Math.max(0.0, raw - redirect));
        SilentDamage.apply(prodigy, taken);
    }
}
