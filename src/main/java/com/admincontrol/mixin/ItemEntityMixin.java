package com.admincontrol.mixin;

import com.admincontrol.AdminControl;
import com.admincontrol.ItemLock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void ac$blockPickup(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity vp && AdminControl.CFG.vanishNoPickup
                && com.admincontrol.VanishManager.isVanished(vp)) {
            ci.cancel();
            return;
        }
        if (AdminControl.CFG.blockPickup && player instanceof ServerPlayerEntity sp) {
            if (ItemLock.isBlocked(sp, ((ItemEntity) (Object) this).getStack())) {
                ci.cancel();
            }
        }
    }
}
