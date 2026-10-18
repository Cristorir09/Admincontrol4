package com.admincontrol.mixin;

import com.admincontrol.SpawnControl;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(SpawnHelper.class)
public abstract class SpawnHelperMixin {
    @Inject(method = "pickRandomSpawnEntry", at = @At("RETURN"), cancellable = true)
    private static void ac$filterSpawn(ServerWorld world, StructureAccessor structureAccessor,
                                       ChunkGenerator chunkGenerator, SpawnGroup spawnGroup, Random random,
                                       BlockPos pos, CallbackInfoReturnable<Optional<SpawnSettings.SpawnEntry>> cir) {
        Optional<SpawnSettings.SpawnEntry> result = cir.getReturnValue();
        if (result != null && result.isPresent() && SpawnControl.shouldBlock(world, pos, result.get().type)) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
