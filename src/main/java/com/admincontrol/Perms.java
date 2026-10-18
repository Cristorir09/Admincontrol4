package com.admincontrol;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

/** Admin = OP, o el dueño del mundo en un mundo de un jugador (aunque los trucos estén desactivados). */
public final class Perms {
    private Perms() {}

    public static boolean isAdmin(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        return s != null && (s.getPlayerManager().isOperator(p.getGameProfile()) || s.isHost(p.getGameProfile()));
    }

    public static boolean can(ServerCommandSource src) {
        if (src.hasPermissionLevel(4)) return true;
        ServerPlayerEntity p = src.getPlayer();
        return p != null && isAdmin(p);
    }
}
