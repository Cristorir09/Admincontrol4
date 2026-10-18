package com.admincontrol.gui;

import com.admincontrol.AdminControl;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Menú tipo cofre controlado 100% por el servidor (los clientes no necesitan el mod). */
public abstract class Menu {
    final int rows;
    final Text title;
    final SimpleInventory inv;
    final Map<Integer, Consumer<Click>> actions = new HashMap<>();
    protected Consumer<Click> playerSlotAction;
    protected ServerPlayerEntity viewer;
    MenuHandler handler;
    protected Item accent = Items.CYAN_STAINED_GLASS_PANE;

    /** Color del marco del menú. */
    public void theme(Item accent) { this.accent = accent; }

    protected Menu(String title, int rows) {
        this.rows = rows;
        this.title = Text.literal(title);
        this.inv = new SimpleInventory(rows * 9);
    }

    protected abstract void build();

    public void open(ServerPlayerEntity sp) {
        this.viewer = sp;
        render();
        sp.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, playerInv, player) -> {
            handler = new MenuHandler(syncId, playerInv, this);
            return handler;
        }, title));
    }

    protected void render() {
        inv.clear();
        actions.clear();
        playerSlotAction = null;
        build();
    }

    /** Redibuja el menú sin cerrarlo. */
    public void refresh() {
        if (handler == null || viewer == null || viewer.currentScreenHandler != handler) return;
        render();
        handler.syncState();
    }

    protected void set(int slot, ItemStack stack, Consumer<Click> action) {
        if (slot < 0 || slot >= rows * 9) return;
        inv.setStack(slot, stack);
        if (action != null) actions.put(slot, action); else actions.remove(slot);
    }

    protected void fill(int from, int to) {
        for (int i = from; i < to; i++) set(i, Icons.pane(), null);
    }

    /** Marco de color alrededor del menú. */
    protected void border() {
        for (int i = 0; i < rows * 9; i++) {
            int r = i / 9, c = i % 9;
            if (r == 0 || r == rows - 1 || c == 0 || c == 8) set(i, Icons.pane(accent), null);
        }
    }

    void handleClick(MenuHandler h, int slotIndex, int button, SlotActionType type, ServerPlayerEntity sp) {
        if (type != SlotActionType.QUICK_CRAFT && slotIndex >= 0) {
            int size = rows * 9;
            Click c = new Click(slotIndex, button, type, sp);
            try {
                if (slotIndex < size) {
                    Consumer<Click> a = actions.get(slotIndex);
                    if (a != null) a.accept(c);
                } else if (playerSlotAction != null && slotIndex < h.slots.size()) {
                    c.stack = h.slots.get(slotIndex).getStack().copy();
                    playerSlotAction.accept(c);
                }
            } catch (Exception e) {
                AdminControl.LOGGER.error("Error en menú", e);
            }
        }
        if (sp.currentScreenHandler == h) {
            h.setCursorStack(ItemStack.EMPTY);
            h.syncState();
        }
    }
}
