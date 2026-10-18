package com.admincontrol.mixin;

import com.admincontrol.AdminControl;
import com.admincontrol.ItemLock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Impide sacar items bloqueados de cualquier slot (resultado de crafteo, hornos, cofres...). */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "canTakeItems", at = @At("HEAD"), cancellable = true)
    private void ac$noTake(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (player instanceof ServerPlayerEntity rp && (Object) this instanceof CraftingResultSlot) {
            if (ItemLock.recipeBlocked(rp, ((Slot) (Object) this).getStack())) {
                rp.sendMessage(Text.literal("§cEsta receta está bloqueada."), true);
                cir.setReturnValue(false);
                return;
            }
        }
        if (AdminControl.CFG.blockInventory && player instanceof ServerPlayerEntity sp) {
            if (ItemLock.isBlocked(sp, ((Slot) (Object) this).getStack())) {
                cir.setReturnValue(false);
            }
        }
    }
}
