package com.admincontrol.client;

import com.admincontrol.Net;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class AdminControlClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Net.State.ID, (payload, context) -> {
            MinecraftClient mc = context.client();
            if (payload.open()) {
                mc.setScreen(new AdminScreen(payload.json()));
            } else if (mc.currentScreen instanceof AdminScreen screen) {
                screen.applyState(payload.json());
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(Net.Transition.ID, (payload, context) ->
                TransitionOverlay.handle(payload.mode(), payload.ticks(), payload.title(), payload.sub()));
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> TransitionOverlay.render(ctx));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(Net.Hello.ID)) {
                ClientPlayNetworking.send(new Net.Hello("1"));
            }
        });

        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.admincontrol.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.admincontrol"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (client.currentScreen == null && client.player != null && ClientPlayNetworking.canSend(Net.Cmd.ID)) {
                    ClientPlayNetworking.send(new Net.Cmd("open", ""));
                }
            }
        });
    }
}
