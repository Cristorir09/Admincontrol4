package com.admincontrol.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Lista paginada (45 entradas por página) con una fila de controles abajo. */
public class ListMenu extends Menu {
    public record Entry(ItemStack icon, Consumer<Click> action) {}
    private record Control(Supplier<ItemStack> icon, Consumer<Click> action) {}

    private Supplier<List<Entry>> entries = List::of;
    private final List<Control> controls = new ArrayList<>();
    private final Runnable back;
    private int page = 0;

    public ListMenu(String title, Runnable back) {
        super(title, 6);
        this.back = back;
    }

    public ListMenu entries(Supplier<List<Entry>> s) {
        this.entries = s;
        return this;
    }

    /** Hasta 5 controles (casillas 46-50 de la fila inferior). */
    public ListMenu control(Supplier<ItemStack> icon, Consumer<Click> action) {
        controls.add(new Control(icon, action));
        return this;
    }

    @Override
    protected void build() {
        List<Entry> list = entries.get();
        int pages = Math.max(1, (list.size() + 44) / 45);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;
        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx >= list.size()) break;
            set(i, list.get(idx).icon(), list.get(idx).action());
        }
        fill(45, 54);
        for (int i = 45; i < 54; i++) set(i, Icons.pane(accent), null);
        final int pg = pages;
        set(45, Icons.of(Items.ARROW, "§ePágina anterior"), c -> { if (page > 0) { page--; refresh(); } });
        set(53, Icons.of(Items.ARROW, "§ePágina siguiente"), c -> { if (page < pg - 1) { page++; refresh(); } });
        set(52, Icons.of(Items.PAPER, "§fPágina " + (page + 1) + "/" + pages, "§7Entradas: " + list.size()), null);
        int slot = 46;
        for (Control ct : controls) {
            if (slot > 50) break;
            set(slot++, ct.icon().get(), ct.action());
        }
        if (back != null) set(51, Icons.of(Items.OAK_DOOR, "§6Volver"), c -> back.run());
    }
}
