package com.admincontrol.mixin;

import com.admincontrol.AdminControl;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Regla extrema: las hojas ya no sueltan brotes. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(method = "getDroppedStacks", at = @At("RETURN"), cancellable = true)
    private void ac$noSaplings(LootContextParameterSet.Builder builder, CallbackInfoReturnable<List<ItemStack>> cir) {
        if (!AdminControl.CFG.noSaplingDrops) return;
        BlockState self = (BlockState) (Object) this;
        if (!self.isIn(BlockTags.LEAVES)) return;
        List<ItemStack> filtered = cir.getReturnValue().stream()
                .filter(s -> !s.isIn(ItemTags.SAPLINGS))
                .toList();
        cir.setReturnValue(filtered);
    }
}
