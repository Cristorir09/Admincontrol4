package com.admincontrol.mixin;

import com.admincontrol.AdminControl;
import com.admincontrol.VanishManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los mobs ignoran a los admins en Vanish. */
@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void ac$ignoreVanished(LivingEntity target, CallbackInfo ci) {
        if (target instanceof ServerPlayerEntity sp && AdminControl.CFG.vanishIgnoredByMobs && VanishManager.isVanished(sp)) {
            ci.cancel();
        }
    }
}
