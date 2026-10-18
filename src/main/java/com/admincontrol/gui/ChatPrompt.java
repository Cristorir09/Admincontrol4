package com.admincontrol.gui;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Para escribir texto/números desde un menú: cierra el menú y captura tu siguiente mensaje de chat. */
public final class ChatPrompt {
    private ChatPrompt() {}

    private record Prompt(Consumer<String> onText, Runnable onCancel) {}

    private static final Map<UUID, Prompt> PROMPTS = new ConcurrentHashMap<>();

    public static void ask(ServerPlayerEntity sp, String question, Consumer<String> onText, Runnable onCancel) {
        sp.closeHandledScreen();
        PROMPTS.put(sp.getUuid(), new Prompt(onText, onCancel));
        sp.sendMessage(Text.literal("§6[Admin] §e" + question + " §7(escribe en el chat, o 'cancelar')"), false);
    }

    /** @return true si el mensaje era una respuesta (y no debe mostrarse en el chat). */
    public static boolean intercept(ServerPlayerEntity sender, String text) {
        Prompt p = PROMPTS.remove(sender.getUuid());
        if (p == null) return false;
        String t = text == null ? "" : text.trim();
        if (t.equalsIgnoreCase("cancelar") || t.isEmpty()) p.onCancel().run();
        else p.onText().accept(t);
        return true;
    }
}
