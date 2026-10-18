package com.admincontrol;

import com.admincontrol.gui.ChatPrompt;
import com.admincontrol.gui.Menus;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Shearable;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

public final class Events {
    private Events() {}

    private static ActionResult deny(ServerPlayerEntity sp, String msg) {
        sp.sendMessage(Text.literal("§c" + msg), true);
        return ActionResult.FAIL;
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return TypedActionResult.pass(stack);
            if (AdminTools.type(stack).equals("menu_wand") && Perms.isAdmin(sp)) {
                Net.openGui(sp);
                return TypedActionResult.success(stack);
            }
            if (ItemLock.useBlocked(sp, stack)) {
                ItemLock.warn(sp);
                return TypedActionResult.fail(stack);
            }
            ExtremeRules.onItem(sp, stack);
            return TypedActionResult.pass(stack);
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack held = sp.getStackInHand(hand);
            if (ItemLock.useBlocked(sp, held)) {
                ItemLock.warn(sp);
                return ActionResult.FAIL;
            }
            if (AdminControl.CFG.noSaplingPlanting && held.isIn(ItemTags.SAPLINGS)) {
                return deny(sp, "Replantar está desactivado en este servidor.");
            }
            BlockState clicked = world.getBlockState(hit.getBlockPos());
            if (RuleDefs.on("noSleeping") && clicked.isIn(BlockTags.BEDS)) {
                return deny(sp, "Las camas están desactivadas.");
            }
            if (RuleDefs.on("noBonemeal") && held.isOf(Items.BONE_MEAL)) {
                return deny(sp, "El hueso molido está desactivado.");
            }
            ExtremeRules.onUse(sp, clicked);
            if (held.getItem() instanceof BlockItem bi) {
                ExtremeRules.onPlace(sp, bi.getBlock().getDefaultState());
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack held = sp.getStackInHand(hand);
            if (ItemLock.useBlocked(sp, held)) {
                ItemLock.warn(sp);
                return ActionResult.FAIL;
            }
            if (RuleDefs.on("noMilkCows") && held.isOf(Items.BUCKET) && entity instanceof CowEntity)
                return deny(sp, "Ya no se puede sacar leche.");
            if (RuleDefs.on("noBreeding") && entity instanceof AnimalEntity a && a.isBreedingItem(held))
                return deny(sp, "La crianza está desactivada.");
            if (RuleDefs.on("noShearing") && held.isOf(Items.SHEARS) && entity instanceof Shearable)
                return deny(sp, "Esquilar está desactivado.");
            if (RuleDefs.on("noVillagerTrading") && entity instanceof MerchantEntity)
                return deny(sp, "El comercio está desactivado.");
            return ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack held = sp.getStackInHand(hand);
            if (AdminTools.type(held).equals("respawn_stick")) {
                if (Perms.isAdmin(sp) && entity instanceof ServerPlayerEntity target) {
                    AdminTools.sendToSpawn(sp, target);
                }
                return ActionResult.FAIL;
            }
            if (ItemLock.useBlocked(sp, held)) {
                ItemLock.warn(sp);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (ItemLock.useBlocked(sp, sp.getStackInHand(hand))) {
                ItemLock.warn(sp);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!world.isClient() && player instanceof ServerPlayerEntity sp) {
                if (!AdminControl.CFG.protectedBlocks.isEmpty() && !ItemLock.bypass(sp)) {
                    for (String pat : AdminControl.CFG.protectedBlocks) {
                        if (Matchers.block(state, pat)) {
                            sp.sendMessage(Text.literal("§cEste bloque está protegido por el evento."), true);
                            return false;
                        }
                    }
                }
                ExtremeRules.onBreak(sp, state);
            }
            return true;
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) ->
                !ChatPrompt.intercept(sender, message.getSignedContent()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ItemLock.tick(server);
            SpawnControl.tick(server);
            ExtremeRules.tick(server);
            RuleEnforcer.tick(server);
            EventManager.tick(server);
            VanishManager.tick(server);
            TimerManager.tick(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            String uuid = p.getUuidAsString();
            String name = p.getName().getString();
            if (!name.equals(AdminControl.CFG.names.get(uuid))) {
                AdminControl.CFG.names.put(uuid, name);
                AdminControl.CFG.save();
            }
            VanishManager.onJoin(p, server);
            if (Perms.isAdmin(p)) {
                p.sendMessage(Text.literal("§6[Admin Control] §fEscribe §e/ac §fo pulsa §eK §fpara abrir el panel."), false);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                { VanishManager.forget(handler.player.getUuid()); Net.forget(handler.player.getUuid()); });
    }
}
