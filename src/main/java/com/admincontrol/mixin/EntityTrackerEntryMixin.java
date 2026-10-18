package com.admincontrol.mixin;

import com.admincontrol.VanishManager;
import com.admincontrol.VanishTrackable;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Evita que los jugadores en vanish se envíen (spawn packet) a quienes no deben verlos. */
@Mixin(EntityTrackerEntry.class)
public abstract class EntityTrackerEntryMixin implements VanishTrackable {
    @Shadow @Final private Entity entity;

    @Shadow public abstract void startTracking(ServerPlayerEntity player);

    @Unique private final Set<UUID> ac$tracking = ConcurrentHashMap.newKeySet();
    @Unique private final Set<UUID> ac$hidden = ConcurrentHashMap.newKeySet();

    @Inject(method = "startTracking", at = @At("HEAD"), cancellable = true)
    private void ac$onStart(ServerPlayerEntity viewer, CallbackInfo ci) {
        if (!(this.entity instanceof ServerPlayerEntity target)) return;
        VanishManager.register(target.getUuid(), this);
        ac$tracking.add(viewer.getUuid());
        if (!VanishManager.canSee(viewer, target)) {
            ac$hidden.add(viewer.getUuid());
            ci.cancel();
        }
    }

    @Inject(method = "stopTracking", at = @At("HEAD"))
    private void ac$onStop(ServerPlayerEntity viewer, CallbackInfo ci) {
        ac$tracking.remove(viewer.getUuid());
        ac$hidden.remove(viewer.getUuid());
    }

    @Override
    public void ac$hideNow(MinecraftServer server) {
        if (!(this.entity instanceof ServerPlayerEntity target)) return;
        for (UUID id : new ArrayList<>(ac$tracking)) {
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(id);
            if (viewer == null || VanishManager.canSee(viewer, target)) continue;
            ac$hidden.add(id);
            viewer.networkHandler.sendPacket(new EntitiesDestroyS2CPacket(target.getId()));
        }
    }

    @Override
    public void ac$revealAll(MinecraftServer server) {
        for (UUID id : new ArrayList<>(ac$hidden)) {
            ac$hidden.remove(id);
            ServerPlayerEntity viewer = server.getPlayerManager().getPlayer(id);
            if (viewer != null) this.startTracking(viewer);
        }
    }
}
