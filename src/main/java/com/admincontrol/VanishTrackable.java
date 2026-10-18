package com.admincontrol;

import net.minecraft.server.MinecraftServer;

/** Interfaz implementada por EntityTrackerEntryMixin. */
public interface VanishTrackable {
    void ac$hideNow(MinecraftServer server);
    void ac$revealAll(MinecraftServer server);
}
