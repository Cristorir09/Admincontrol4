package com.admincontrol.gui;

import com.admincontrol.AdminControl;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Menú de botones sueltos (interruptores, ajustes, navegación). */
public class ButtonMenu extends Menu {
    private record Btn(int slot, Supplier<ItemStack> icon, Consumer<Click> action) {}

    private final List<Btn> buttons = new ArrayList<>();

    public ButtonMenu(String title, int rows) {
        super(title, rows);
    }

    public ButtonMenu button(int slot, Supplier<ItemStack> icon, Consumer<Click> action) {
        buttons.add(new Btn(slot, icon, action));
        return this;
    }

    public ButtonMenu toggle(int slot, String label, String desc, BooleanSupplier get, Consumer<Boolean> set) {
        return button(slot, () -> {
            boolean on = get.getAsBoolean();
            ItemStack s = new ItemStack(on ? Items.LIME_CONCRETE : Items.RED_CONCRETE);
            Icons.name(s, (on ? "§a" : "§c") + label + ": " + (on ? "ACTIVADO" : "DESACTIVADO"));
            Icons.lore(s, "§7" + desc, "", "§eClic para cambiar");
            return s;
        }, c -> {
            set.accept(!get.getAsBoolean());
            AdminControl.CFG.save();
            refresh();
        });
    }

    public ButtonMenu toggleIcon(int slot, net.minecraft.item.ItemConvertible iconItem, String label, String desc,
                                 BooleanSupplier get, Consumer<Boolean> set) {
        return button(slot, () -> {
            boolean on = get.getAsBoolean();
            ItemStack s = new ItemStack(iconItem);
            Icons.name(s, (on ? "§a" : "§c") + label + ": " + (on ? "ACTIVADO" : "desactivado"));
            Icons.lore(s, "§7" + desc, "", "§eClic para cambiar");
            if (on) Icons.glint(s);
            return s;
        }, c -> {
            set.accept(!get.getAsBoolean());
            AdminControl.CFG.save();
            refresh();
        });
    }

    public ButtonMenu back(Runnable back) {
        return button(rows * 9 - 5, () -> Icons.of(Items.OAK_DOOR, "§6Volver"), c -> back.run());
    }

    @Override
    protected void build() {
        fill(0, rows * 9);
        border();
        for (Btn b : buttons) set(b.slot(), b.icon().get(), b.action());
    }
}
