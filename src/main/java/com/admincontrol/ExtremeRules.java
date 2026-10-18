package com.admincontrol;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/** Reglas de daño extremo (usar / pisar / romper / colocar bloques, usar items). */
public final class ExtremeRules {
    private ExtremeRules() {}

    private static void hint(ServerPlayerEntity p, String msg) {
        p.sendMessage(Text.literal(msg), true);
    }

    /** Aplica el daño si el jugador debe recibirlo; si no, avisa por qué no (para que sea fácil depurar). */
    private static void apply(ServerPlayerEntity p, Double amount, boolean quietHints) {
        if (amount == null) return;
        Config c = AdminControl.CFG;
        if (!c.extremeDamageEnabled) {
            if (!quietHints) hint(p, "§7[Regla extrema ignorada: el interruptor general está APAGADO]");
            return;
        }
        if (!p.isAlive() || p.isSpectator()) return;
        if (p.isCreative() && !c.extremeAffectCreative) {
            if (!quietHints) hint(p, "§7[Regla extrema ignorada: estás en creativo. Activa 'Afecta a creativo']");
            return;
        }
        hurt(p, amount);
    }

    private static void hurt(ServerPlayerEntity p, double amount) {
        ServerWorld w = (ServerWorld) p.getWorld();
        float before = p.getHealth() + p.getAbsorptionAmount();
        p.damage(w.getDamageSources().generic(), (float) amount);
        // Si el daño era letal pero algo lo evitó (creativo, resistencia, tótem...), forzar la muerte.
        if (p.isAlive() && amount >= before) {
            p.damage(w.getDamageSources().outOfWorld(), Float.MAX_VALUE);
        }
    }

    private static Double findBlock(Map<String, Double> map, BlockState st) {
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (Matchers.block(st, e.getKey())) return e.getValue();
        }
        return null;
    }

    public static void onUse(ServerPlayerEntity p, BlockState st) {
        apply(p, findBlock(AdminControl.CFG.useDamage, st), false);
    }

    public static void onBreak(ServerPlayerEntity p, BlockState st) {
        apply(p, findBlock(AdminControl.CFG.breakDamage, st), false);
    }

    public static void onPlace(ServerPlayerEntity p, BlockState st) {
        apply(p, findBlock(AdminControl.CFG.placeDamage, st), false);
    }

    public static void onItem(ServerPlayerEntity p, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (Map.Entry<String, Double> e : AdminControl.CFG.itemDamage.entrySet()) {
            if (Matchers.item(stack, e.getKey())) {
                apply(p, e.getValue(), false);
                return;
            }
        }
    }

    public static void tick(MinecraftServer server) {
        Config c = AdminControl.CFG;
        if (c.standDamage.isEmpty()) return;
        if (server.getTicks() % 5 != 0) return;
        boolean quiet = server.getTicks() % 40 != 0; // los avisos solo cada 2 segundos
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            BlockPos feet = p.getBlockPos();
            BlockState[] states = { p.getWorld().getBlockState(feet), p.getWorld().getBlockState(feet.down()) };
            for (BlockState st : states) {
                Double d = findBlock(c.standDamage, st);
                if (d != null) {
                    apply(p, d, quiet);
                    break;
                }
            }
        }
    }
}
