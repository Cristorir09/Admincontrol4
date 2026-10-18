package com.admincontrol.mixin;

import com.admincontrol.DamageControl;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float ac$scaleDamage(float amount, DamageSource source) {
        return DamageControl.modify((LivingEntity) (Object) this, source, amount);
    }
}
