package com.admincontrol;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class ItemLock {
    private ItemLock() {}

    public static boolean bypass(ServerPlayerEntity p) {
        Config c = AdminControl.CFG;
        MinecraftServer server = p.getServer();
        if (c.opsBypass && Perms.isAdmin(p)) return true;
        return c.fullBypass.contains(p.getUuidAsString());
    }

    // Caché: ¿está este item bloqueado para todos? (evita recalcular patrones/tags en cada tick y para cada jugador)
    private static Config cacheCfg;
    private static int cacheRev = -1;
    private static final Map<Item, Boolean> GLOBAL = new ConcurrentHashMap<>();

    private static boolean globallyBlocked(ItemStack stack) {
        Config c = AdminControl.CFG;
        if (cacheCfg != c || cacheRev != Config.REV) {
            GLOBAL.clear();
            cacheCfg = c;
            cacheRev = Config.REV;
        }
        return GLOBAL.computeIfAbsent(stack.getItem(), k -> {
            for (String pat : c.blockedItems) if (Matchers.item(stack, pat)) return true;
            return false;
        });
    }

    public static boolean isBlocked(ServerPlayerEntity p, ItemStack stack) {
        Config c = AdminControl.CFG;
        if (!c.itemsEnabled || stack.isEmpty() || c.blockedItems.isEmpty()) return false;
        if (!globallyBlocked(stack)) return false;
        if (bypass(p)) return false;
        Set<String> ex = c.playerExceptions.get(p.getUuidAsString());
        if (ex != null) {
            for (String pat : ex) if (Matchers.item(stack, pat)) return false;
        }
        return true;
    }

    public static boolean recipeBlocked(ServerPlayerEntity p, ItemStack stack) {
        Config c = AdminControl.CFG;
        if (stack.isEmpty() || c.blockedRecipes.isEmpty() || bypass(p)) return false;
        for (String pat : c.blockedRecipes) if (Matchers.item(stack, pat)) return true;
        return false;
    }

    public static boolean useBlocked(ServerPlayerEntity p, ItemStack stack) {
        return AdminControl.CFG.blockUse && isBlocked(p, stack);
    }

    public static void warn(ServerPlayerEntity p) {
        p.sendMessage(Text.literal("§cEste objeto está bloqueado por la administración."), true);
    }

    /** Confisca items bloqueados del inventario (cubre creativo, crafteos, cofres, comandos, otros mods...). */
    public static void tick(MinecraftServer server) {
        Config c = AdminControl.CFG;
        if (!c.itemsEnabled || !c.blockInventory || c.blockedItems.isEmpty()) return;
        if (server.getTicks() % 4 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (bypass(p)) continue;
            int removed = 0;
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.size(); i++) {
                ItemStack s = inv.getStack(i);
                if (!s.isEmpty() && isBlocked(p, s)) {
                    inv.setStack(i, ItemStack.EMPTY);
                    removed++;
                }
            }
            ScreenHandler h = p.currentScreenHandler;
            ItemStack cursor = h.getCursorStack();
            if (!cursor.isEmpty() && isBlocked(p, cursor)) {
                h.setCursorStack(ItemStack.EMPTY);
                removed++;
            }
            if (removed > 0) {
                p.sendMessage(Text.literal("§cSe retiraron objetos bloqueados de tu inventario."), true);
            }
        }
    }
}
