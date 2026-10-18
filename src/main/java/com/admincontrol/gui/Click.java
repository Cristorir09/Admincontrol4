package com.admincontrol.gui;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public final class Click {
    public final int slot;
    public final int button;
    public final SlotActionType type;
    public final ServerPlayerEntity player;
    /** Si el clic fue en el inventario del jugador: el item pulsado. */
    public ItemStack stack = ItemStack.EMPTY;

    public Click(int slot, int button, SlotActionType type, ServerPlayerEntity player) {
        this.slot = slot;
        this.button = button;
        this.type = type;
        this.player = player;
    }

    public boolean shift() { return type == SlotActionType.QUICK_MOVE; }
    public boolean drop() { return type == SlotActionType.THROW; }
    public boolean right() { return button == 1 && type == SlotActionType.PICKUP; }
    public boolean left() { return button == 0 && type == SlotActionType.PICKUP; }
    /** +1 con clic izquierdo, -1 con clic derecho. */
    public int sign() { return button == 1 ? -1 : 1; }
    public boolean adjustable() { return type == SlotActionType.PICKUP || type == SlotActionType.QUICK_MOVE; }
}
