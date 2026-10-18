package com.admincontrol;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

/**
 * Evento de reparto: divide a los participantes de forma ALEATORIA y EQUILIBRADA entre los anfitriones (admins)
 * y los teletransporta repartidos alrededor de cada anfitrión, con una transición (cortinas negras + título).
 */
public final class EventManager {
    private EventManager() {}

    private record Origin(RegistryKey<World> world, double x, double y, double z, float yaw, float pitch) {}

    /** Ticks que dura el oscurecimiento de pantalla antes de teletransportar. */
    private static final int FADE_OUT_TICKS = 24;

    private static final Map<UUID, Origin> ORIGINS = new LinkedHashMap<>();
    private static Map<UUID, List<UUID>> plan = new LinkedHashMap<>();
    private static int countdown = -1; // cuenta atrás previa
    private static int fade = -1;      // transición de salida (pantalla negra) justo antes del TP
    private static final Random RNG = new Random();

    public static List<ServerPlayerEntity> hosts(MinecraftServer s) {
        List<ServerPlayerEntity> l = new ArrayList<>();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (AdminControl.CFG.eventHosts.contains(p.getUuidAsString())) l.add(p);
        }
        return l;
    }

    public static List<ServerPlayerEntity> participants(MinecraftServer s) {
        List<ServerPlayerEntity> l = new ArrayList<>();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            String u = p.getUuidAsString();
            if (AdminControl.CFG.eventHosts.contains(u) || AdminControl.CFG.eventExcluded.contains(u)) continue;
            l.add(p);
        }
        return l;
    }

    public static Map<UUID, List<UUID>> plan() { return plan; }

    public static boolean running() { return countdown >= 0 || fade >= 0; }

    public static int returnableCount() { return ORIGINS.size(); }

    /** Baraja participantes y anfitriones y reparte por turnos: los grupos quedan con diferencia máxima de 1. */
    public static Map<UUID, List<UUID>> buildPlan(MinecraftServer s) {
        List<ServerPlayerEntity> hs = hosts(s);
        List<ServerPlayerEntity> ps = participants(s);
        Collections.shuffle(hs, RNG);
        Collections.shuffle(ps, RNG);
        Map<UUID, List<UUID>> np = new LinkedHashMap<>();
        for (ServerPlayerEntity h : hs) np.put(h.getUuid(), new ArrayList<>());
        if (!hs.isEmpty()) {
            for (int i = 0; i < ps.size(); i++) {
                np.get(hs.get(i % hs.size()).getUuid()).add(ps.get(i).getUuid());
            }
        }
        plan = np;
        return np;
    }

    /** Inicia la cuenta atrás (o la transición directa si es 0). Usa el reparto actual si existe, si no crea uno. */
    public static String start(MinecraftServer s, ServerPlayerEntity by) {
        if (running()) return "§cYa hay un evento en curso.";
        if (hosts(s).isEmpty()) return "§cNo hay anfitriones conectados. Elígelos primero.";
        if (participants(s).isEmpty()) return "§cNo hay participantes conectados.";
        if (plan.isEmpty()) buildPlan(s);
        int secs = Math.max(0, AdminControl.CFG.eventCountdown);
        if (secs == 0) beginFade(s); else countdown = secs * 20;
        return "§aEvento iniciado.";
    }

    public static void cancel(MinecraftServer s) {
        if (!running()) return;
        countdown = -1;
        fade = -1;
        for (UUID id : allPlanned()) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
            if (p == null) continue;
            Net.sendTransition(p, "clear", 1, "", "");
            p.removeStatusEffect(StatusEffects.BLINDNESS);
            p.sendMessage(Text.literal("§c¡Evento cancelado!"), true);
        }
    }

    public static void tick(MinecraftServer s) {
        if (fade >= 0) {
            if (fade == 0) {
                fade = -1;
                execute(s);
            } else {
                fade--;
            }
            return;
        }
        if (countdown < 0) return;
        if (countdown == 0) {
            countdown = -1;
            beginFade(s);
            return;
        }
        if (countdown % 20 == 0) {
            int sec = countdown / 20;
            for (UUID id : allPlanned()) {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
                if (p == null) continue;
                p.sendMessage(Text.literal("§e¡Teletransporte en §6" + sec + "§e!"), true);
                p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1f);
            }
        }
        countdown--;
    }

    private static List<UUID> allPlanned() {
        List<UUID> all = new ArrayList<>();
        for (Map.Entry<UUID, List<UUID>> e : plan.entrySet()) {
            all.add(e.getKey());
            all.addAll(e.getValue());
        }
        return all;
    }

    /** Título con fundido (también lo usa el sistema de contadores). */
    public static void title(ServerPlayerEntity p, String title, String sub, int in, int stay, int out) {
        p.networkHandler.sendPacket(new TitleFadeS2CPacket(in, stay, out));
        p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(sub)));
        p.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(title)));
    }

    /** Fase 1: las cortinas negras se cierran. Quien tiene el mod ve la transición; los demás reciben título + ceguera. */
    private static void beginFade(MinecraftServer s) {
        fade = FADE_OUT_TICKS;
        for (Map.Entry<UUID, List<UUID>> e : plan.entrySet()) {
            for (UUID id : e.getValue()) {
                ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
                if (p == null) continue;
                p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_PORTAL_TRIGGER, SoundCategory.PLAYERS, 0.5f, 1.4f);
                if (!Net.sendTransition(p, "out", 20, "§d✦ §fTeletransportando §d✦", "")) {
                    title(p, "§d✦ §fTeletransportando §d✦", "§7Prepárate...", 5, 30, 5);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0, false, false, false));
                }
            }
        }
    }

    /** Fase 2: teletransporte con la pantalla en negro y fase 3: las cortinas se abren en el destino. */
    private static void execute(MinecraftServer s) {
        PlayerManager pm = s.getPlayerManager();
        int moved = 0;
        for (Map.Entry<UUID, List<UUID>> e : plan.entrySet()) {
            ServerPlayerEntity host = pm.getPlayer(e.getKey());
            if (host == null) continue;
            ServerWorld hw = (ServerWorld) host.getWorld();
            String hostName = host.getName().getString();
            int size = e.getValue().size();
            for (UUID id : e.getValue()) {
                ServerPlayerEntity p = pm.getPlayer(id);
                if (p == null) continue;
                ORIGINS.put(id, new Origin(p.getWorld().getRegistryKey(), p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch()));
                Vec3d dest = AdminTools.scatterAround(hw, host.getBlockPos(), AdminControl.CFG.eventRadius, RNG);

                p.stopRiding();
                float yaw = (float) Math.toDegrees(Math.atan2(host.getZ() - dest.z, host.getX() - dest.x)) - 90f;
                p.teleport(hw, dest.x, dest.y, dest.z, yaw, 0f);
                p.fallDistance = 0;

                hw.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1.1f);
                hw.spawnParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1, dest.z, 40, 0.4, 0.8, 0.4, 0.15);

                String t = "§6¡Equipo de §e" + hostName + "§6!";
                String sub = "§7Te esperan §f" + Math.max(0, size - 1) + " §7compañeros";
                if (!Net.sendTransition(p, "in", 30, t, sub)) {
                    p.removeStatusEffect(StatusEffects.BLINDNESS);
                    title(p, t, sub, 10, 50, 20);
                }
                p.sendMessage(Text.literal("§6¡Evento! §fTe tocó el equipo de §e" + hostName), false);
                moved++;
            }
            host.sendMessage(Text.literal("§aTu equipo llegó: §f" + size + " jugadores."), false);
        }
        plan = new LinkedHashMap<>();
        AdminControl.LOGGER.info("Evento: {} jugadores repartidos", moved);
    }

    /** Devuelve a todos a donde estaban antes del reparto (con fundido de entrada). */
    public static int returnAll(MinecraftServer s) {
        int n = 0;
        for (Map.Entry<UUID, Origin> e : new ArrayList<>(ORIGINS.entrySet())) {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(e.getKey());
            if (p == null) continue;
            Origin o = e.getValue();
            ServerWorld w = s.getWorld(o.world());
            if (w == null) w = s.getOverworld();
            p.stopRiding();
            p.teleport(w, o.x(), o.y(), o.z(), o.yaw(), o.pitch());
            p.fallDistance = 0;
            w.playSound(null, o.x(), o.y(), o.z(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
            if (!Net.sendTransition(p, "in", 25, "§6Fin del evento", "§7Volviste a tu posición")) {
                title(p, "§6Fin del evento", "§7Volviste a tu posición", 5, 30, 15);
            }
            n++;
        }
        ORIGINS.clear();
        return n;
    }
}
