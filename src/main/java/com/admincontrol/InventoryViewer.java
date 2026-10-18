package com.admincontrol;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Abre el inventario (o ender chest) de otro jugador en vivo, editable. Solo usa GUIs vanilla. */
public final class InventoryViewer {
    private InventoryViewer() {}

    public static void open(ServerPlayerEntity admin, ServerPlayerEntity target, boolean ender) {
        String name = target.getName().getString();
        if (ender) {
            Inventory inv = target.getEnderChestInventory();
            admin.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, player) -> GenericContainerScreenHandler.createGeneric9x3(syncId, playerInv, inv),
                    Text.literal("Ender Chest de " + name)));
        } else {
            PlayerInventoryView view = new PlayerInventoryView(admin, target);
            admin.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, player) -> new GenericContainerScreenHandler(
                            ScreenHandlerType.GENERIC_9X5, syncId, playerInv, view, 5),
                    Text.literal("Inventario de " + name)));
        }
    }

    /**
     * Fila 1-3: inventario | Fila 4: hotbar | Fila 5: casco, peto, pantalón, botas, mano secundaria (+4 huecos vacíos).
     */
    public static class PlayerInventoryView implements Inventory {
        private static final int SIZE = 45;
        private final ServerPlayerEntity admin;
        private final ServerPlayerEntity target;

        public PlayerInventoryView(ServerPlayerEntity admin, ServerPlayerEntity target) {
            this.admin = admin;
            this.target = target;
        }

        private static int map(int slot) {
            if (slot < 27) return slot + 9;            // inventario principal
            if (slot < 36) return slot - 27;           // hotbar
            if (slot < 40) return 39 - (slot - 36);    // casco(39) peto(38) pantalón(37) botas(36)
            if (slot == 40) return 40;                 // mano secundaria
            return -1;                                 // huecos sin uso
        }

        @Override public int size() { return SIZE; }

        @Override public boolean isEmpty() {
            for (int i = 0; i < SIZE; i++) if (!getStack(i).isEmpty()) return false;
            return true;
        }

        @Override public ItemStack getStack(int slot) {
            int m = map(slot);
            return m < 0 ? ItemStack.EMPTY : target.getInventory().getStack(m);
        }

        @Override public ItemStack removeStack(int slot, int amount) {
            int m = map(slot);
            return m < 0 ? ItemStack.EMPTY : target.getInventory().removeStack(m, amount);
        }

        @Override public ItemStack removeStack(int slot) {
            int m = map(slot);
            return m < 0 ? ItemStack.EMPTY : target.getInventory().removeStack(m);
        }

        @Override public void setStack(int slot, ItemStack stack) {
            int m = map(slot);
            if (m >= 0) {
                target.getInventory().setStack(m, stack);
            } else if (!stack.isEmpty()) {
                admin.getInventory().offerOrDrop(stack); // no perder items en huecos sin uso
            }
        }

        @Override public void markDirty() { target.getInventory().markDirty(); }

        @Override public boolean canPlayerUse(PlayerEntity player) { return !target.isRemoved(); }

        @Override public boolean isValid(int slot, ItemStack stack) { return map(slot) >= 0; }

        @Override public void clear() { target.getInventory().clear(); }
    }
}
