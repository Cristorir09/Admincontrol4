package com.admincontrol;

import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class RuleEnforcer {
    private RuleEnforcer() {}

    /**
     * ¿Está el jugador respirando en una burbuja de aire bajo el agua formada por puertas o trampillas?
     * (cabeza fuera del agua + agua por encima + puerta/trampilla pegada a la cabeza)
     */
    private static boolean inDoorAirPocket(ServerPlayerEntity p) {
        ServerWorld w = (ServerWorld) p.getWorld();
        BlockPos eye = BlockPos.ofFloored(p.getEyePos());
        if (!w.getFluidState(eye).isEmpty()) return false;   // cabeza dentro del agua: se ahoga normal
        if (p.isSubmergedIn(FluidTags.WATER)) return false;

        boolean waterAbove = false;
        for (int i = 1; i <= 10; i++) {
            if (w.getFluidState(eye.up(i)).isIn(FluidTags.WATER)) { waterAbove = true; break; }
        }
        if (!waterAbove) return false;                      // superficie o tierra firme: aire normal

        BlockPos[] around = { eye, eye.up(), eye.down(), eye.north(), eye.south(), eye.east(), eye.west() };
        for (BlockPos b : around) {
            BlockState st = w.getBlockState(b);
            if (st.isIn(BlockTags.DOORS) || st.isIn(BlockTags.TRAPDOORS)) return true;
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (!RuleDefs.on("noDoorAirPockets")) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isCreative() || p.isSpectator() || !p.isAlive()) continue;
            if (p.hasStatusEffect(StatusEffects.WATER_BREATHING) || p.hasStatusEffect(StatusEffects.CONDUIT_POWER)) continue;
            if (!inDoorAirPocket(p)) continue;

            // anula la recuperación de aire de vanilla (+4 por tick) y consume como bajo el agua (-1 neto por tick)
            int air = p.getAir() - 5;
            if (air <= -20) {
                p.setAir(0);
                p.damage(p.getWorld().getDamageSources().drown(), 2.0f);
            } else {
                p.setAir(air);
            }
            if (p.age % 40 == 0) p.sendMessage(Text.literal("§7[Las puertas y trampillas no dan aire aquí]"), true);
        }
    }
}
