package com.admincontrol;

import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.*;

/** Contadores de tiempo con barra de jefe visible para todos (o solo participantes). Ej: 5:00. */
public final class TimerManager {
    private TimerManager() {}

    public static final class T {
        public final int id;
        public String name;
        public int ticksLeft, totalTicks;
        public boolean paused, everyone, autoTp;
        final ServerBossBar bar;
        int lastSec = -1;

        T(int id, String name, int ticks, boolean everyone, boolean autoTp) {
            this.id = id;
            this.name = name;
            this.ticksLeft = ticks;
            this.totalTicks = ticks;
            this.everyone = everyone;
            this.autoTp = autoTp;
            this.bar = new ServerBossBar(Text.literal(""), BossBar.Color.GREEN, BossBar.Style.PROGRESS);
            this.bar.setPercent(1f);
        }

        public int secondsLeft() { return Math.max(0, (ticksLeft + 19) / 20); }
        public int totalSeconds() { return totalTicks / 20; }
    }

    private static final List<T> TIMERS = new ArrayList<>();
    private static int nextId = 1;

    public static List<T> all() { return TIMERS; }

    public static T find(int id) {
        for (T t : TIMERS) if (t.id == id) return t;
        return null;
    }

    /** "5:00" = 5 min · "1:30:00" · "90s" · "5m" · "5" (= minutos). Devuelve segundos o -1. */
    public static int parse(String raw) {
        try {
            String s = raw.trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) return -1;
            int r;
            if (s.contains(":")) {
                String[] p = s.split(":");
                if (p.length == 2) r = Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
                else if (p.length == 3) r = Integer.parseInt(p[0].trim()) * 3600 + Integer.parseInt(p[1].trim()) * 60 + Integer.parseInt(p[2].trim());
                else return -1;
            } else if (s.endsWith("s")) {
                r = Integer.parseInt(s.substring(0, s.length() - 1).trim());
            } else if (s.endsWith("m")) {
                r = Integer.parseInt(s.substring(0, s.length() - 1).trim()) * 60;
            } else {
                r = Integer.parseInt(s) * 60;
            }
            return (r < 1 || r > 86400) ? -1 : r;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String fmt(int sec) {
        if (sec >= 3600) return String.format("%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
        return String.format("%d:%02d", sec / 60, sec % 60);
    }

    public static T create(MinecraftServer s, String name, int seconds, boolean everyone, boolean autoTp) {
        if (TIMERS.size() >= 10) return null;
        T t = new T(nextId++, name == null ? "" : name.trim(), seconds * 20, everyone, autoTp);
        TIMERS.add(t);
        syncAudience(s, t);
        refreshBar(t);
        return t;
    }

    public static void setPaused(int id, boolean paused) {
        T t = find(id);
        if (t == null) return;
        t.paused = paused;
        t.lastSec = -1;
    }

    public static void addSeconds(int id, int secs) {
        T t = find(id);
        if (t == null) return;
        t.ticksLeft = Math.max(20, t.ticksLeft + secs * 20);
        t.totalTicks = Math.max(t.totalTicks, t.ticksLeft);
        t.lastSec = -1;
    }

    public static void stop(int id) {
        T t = find(id);
        if (t == null) return;
        t.bar.clearPlayers();
        TIMERS.remove(t);
    }

    public static void stopAll() {
        for (T t : TIMERS) t.bar.clearPlayers();
        TIMERS.clear();
    }

    private static String label(T t) {
        return t.name.isEmpty() ? "Contador" : t.name;
    }

    private static void refreshBar(T t) {
        int sec = t.secondsLeft();
        t.bar.setName(Text.literal("§e⏳ " + label(t) + " §f" + fmt(sec) + (t.paused ? " §7(en pausa)" : "")));
        t.bar.setColor(sec > 60 ? BossBar.Color.GREEN : sec > 10 ? BossBar.Color.YELLOW : BossBar.Color.RED);
    }

    private static void syncAudience(MinecraftServer s, T t) {
        Set<ServerPlayerEntity> want = new HashSet<>();
        if (t.everyone) {
            want.addAll(s.getPlayerManager().getPlayerList());
        } else {
            want.addAll(EventManager.participants(s));
            want.addAll(EventManager.hosts(s));
        }
        for (ServerPlayerEntity p : new ArrayList<>(t.bar.getPlayers())) {
            if (!want.contains(p)) t.bar.removePlayer(p);
        }
        for (ServerPlayerEntity p : want) t.bar.addPlayer(p);
    }

    private static void say(T t, String msg) {
        for (ServerPlayerEntity p : new ArrayList<>(t.bar.getPlayers())) p.sendMessage(Text.literal(msg), false);
    }

    public static void tick(MinecraftServer s) {
        if (TIMERS.isEmpty()) return;
        Iterator<T> it = TIMERS.iterator();
        while (it.hasNext()) {
            T t = it.next();
            if (!t.paused) t.ticksLeft--;
            if (s.getTicks() % 20 == 0) syncAudience(s, t);

            int sec = t.secondsLeft();
            if (sec != t.lastSec) {
                t.lastSec = sec;
                refreshBar(t);
                if (!t.paused && (sec == 60 || sec == 30 || sec == 10) && t.totalSeconds() > sec) {
                    say(t, "§e⏳ " + label(t) + ": quedan §f" + fmt(sec));
                }
                if (!t.paused && sec > 0 && sec <= 10) {
                    for (ServerPlayerEntity p : new ArrayList<>(t.bar.getPlayers())) {
                        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.7f, sec <= 3 ? 1.8f : 1.2f);
                    }
                }
            }
            t.bar.setPercent(Math.max(0f, Math.min(1f, (float) t.ticksLeft / Math.max(1, t.totalTicks))));

            if (t.ticksLeft <= 0) {
                finish(s, t);
                it.remove();
            }
        }
    }

    private static void finish(MinecraftServer s, T t) {
        for (ServerPlayerEntity p : new ArrayList<>(t.bar.getPlayers())) {
            EventManager.title(p, "§6⏰ ¡Tiempo!", "§e" + label(t), 5, 40, 15);
            p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1f);
        }
        t.bar.clearPlayers();
        for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
            if (Perms.isAdmin(p)) {
                p.sendMessage(Text.literal("§a⏰ El contador «" + label(t) + "» terminó."
                        + (t.autoTp ? "" : " Ya puedes hacer el TP.")), false);
            }
        }
        if (t.autoTp) {
            String r = EventManager.start(s, null);
            for (ServerPlayerEntity p : s.getPlayerManager().getPlayerList()) {
                if (Perms.isAdmin(p)) p.sendMessage(Text.literal("§7[TP automático] " + r), false);
            }
        }
    }
}
