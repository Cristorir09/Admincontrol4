package com.admincontrol;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;

public final class DamageControl {
    private DamageControl() {}

    /** Escala el daño según QUIÉN lo causa (también proyectiles: cuenta el dueño). Compatible con mobs de otros mods. */
    public static float modify(LivingEntity victim, DamageSource source, float amount) {
        Config c = AdminControl.CFG;
        if (!c.damageEnabled || victim.getWorld().isClient()) return amount;
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof LivingEntity) || attacker == victim) return amount;

        Double specific = Matchers.lookupEntity(c.damageMultipliers, attacker.getType());
        double m;
        if (specific != null) m = specific;
        else if (!(attacker instanceof PlayerEntity)) m = c.globalMobDamageMultiplier;
        else m = 1.0;
        if (m == 1.0) return amount;
        return (float) (amount * m);
    }
}
