package com.admincontrol;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.List;

/** Reglas "extremas" extra, activables desde el menú. */
public final class RuleDefs {
    private RuleDefs() {}

    public record Def(String id, String label, String desc, Item icon) {}

    public static final List<Def> EXTRA = List.of(
            new Def("noMilkCows", "Sin leche de vaca", "No se puede ordeñar vacas ni champiñacas", Items.MILK_BUCKET),
            new Def("noPumpkinGolems", "Sin golems de calabaza", "Las calabazas talladas no crean golems", Items.CARVED_PUMPKIN),
            new Def("noWitherBuild", "Sin invocar Wither", "Las cabezas de esqueleto wither no invocan nada", Items.WITHER_SKELETON_SKULL),
            new Def("noDoorAirPockets", "Sin aire en puertas", "Puertas/trampillas no dan aire bajo el agua", Items.OAK_TRAPDOOR),
            new Def("noSleeping", "Sin dormir", "Las camas no se pueden usar", Items.RED_BED),
            new Def("noBonemeal", "Sin hueso molido", "El hueso molido no funciona", Items.BONE_MEAL),
            new Def("noBreeding", "Sin crianza", "No se puede alimentar animales para criar", Items.WHEAT),
            new Def("noShearing", "Sin esquilar", "Las tijeras no funcionan en ovejas, etc.", Items.SHEARS),
            new Def("noVillagerTrading", "Sin comercio", "No se puede comerciar con aldeanos", Items.EMERALD)
    );

    public static boolean on(String id) {
        return AdminControl.CFG.rules.contains(id);
    }
}
