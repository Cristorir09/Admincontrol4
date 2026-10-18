package com.admincontrol;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRemoveS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** NameHide / Vanish: nadie (salvo OPs si está activado) ve tu cuerpo, nombre ni tab list. */
public final class VanishManager {
    private VanishManager() {}

    private static final Map<UUID, VanishTrackable> ENTRIES = new ConcurrentHashMap<>();

    public static void register(UUID id, VanishTrackable t) {
        ENTRIES.put(id, t);
    }

    public static void forget(UUID id) {
        ENTRIES.remove(id);
    }

    public static boolean isVanished(Entity e) {
        return e instanceof ServerPlayerEntity && AdminControl.CFG.vanished.contains(e.getUuidAsString());
    }

    public static boolean canSee(ServerPlayerEntity viewer, Entity target) {
        if (!isVanished(target)) return true;
        if (viewer == target) return true;
        return AdminControl.CFG.vanishVisibleToOps && Perms.isAdmin(viewer);
    }

    public static boolean toggle(ServerPlayerEntity p) {
        boolean on = !AdminControl.CFG.vanished.contains(p.getUuidAsString());
        set(p, on);
        return on;
    }

    public static void set(ServerPlayerEntity p, boolean on) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        String id = p.getUuidAsString();
        if (on) {
            AdminControl.CFG.vanished.add(id);
            applyHide(p, server);
        } else {
            AdminControl.CFG.vanished.remove(id);
            reveal(p, server);
        }
        AdminControl.CFG.save();
    }

    private static void applyHide(ServerPlayerEntity p, MinecraftServer server) {
        PlayerRemoveS2CPacket remove = new PlayerRemoveS2CPacket(List.of(p.getUuid()));
        for (ServerPlayerEntity v : server.getPlayerManager().getPlayerList()) {
            if (v == p || canSee(v, p)) continue;
            v.networkHandler.sendPacket(remove);
        }
        VanishTrackable t = ENTRIES.get(p.getUuid());
        if (t != null) t.ac$hideNow(server);
    }

    private static void reveal(ServerPlayerEntity p, MinecraftServer server) {
        PlayerListS2CPacket add = new PlayerListS2CPacket(
                EnumSet.of(PlayerListS2CPacket.Action.ADD_PLAYER,
                        PlayerListS2CPacket.Action.UPDATE_LISTED,
                        PlayerListS2CPacket.Action.UPDATE_LATENCY,
                        PlayerListS2CPacket.Action.UPDATE_GAME_MODE),
                List.of(p));
        for (ServerPlayerEntity v : server.getPlayerManager().getPlayerList()) {
            if (v != p) v.networkHandler.sendPacket(add);
        }
        VanishTrackable t = ENTRIES.get(p.getUuid());
        if (t != null) t.ac$revealAll(server);
    }

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 40 != 0 || AdminControl.CFG.vanished.isEmpty()) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (isVanished(p)) p.sendMessage(net.minecraft.text.Text.literal("§a[Vanish activo] §7nadie te ve"), true);
        }
    }

    /** Al entrar alguien: oculta los vanish existentes de su tab list; si él es vanish, se oculta de los demás. */
    public static void onJoin(ServerPlayerEntity joined, MinecraftServer server) {
        for (ServerPlayerEntity v : server.getPlayerManager().getPlayerList()) {
            if (v != joined && isVanished(v) && !canSee(joined, v)) {
                joined.networkHandler.sendPacket(new PlayerRemoveS2CPacket(List.of(v.getUuid())));
            }
        }
        if (isVanished(joined)) applyHide(joined, server);
    }
}
