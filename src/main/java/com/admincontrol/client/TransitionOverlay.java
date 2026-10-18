package com.admincontrol.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Transición de teletransporte: dos cortinas negras que se cierran desde arriba y abajo (con un filo de luz),
 * título al centro y, tras el TP, las cortinas se abren revelando el destino.
 */
public final class TransitionOverlay {
    private TransitionOverlay() {}

    private static String mode = "";
    private static long start;
    private static int durationMs = 1000;
    private static String title = "", sub = "";

    public static void handle(String m, int ticks, String t, String s) {
        if (m.equals("clear")) {
            mode = "";
            return;
        }
        mode = m;
        start = System.currentTimeMillis();
        durationMs = Math.max(100, ticks * 50);
        title = t;
        sub = s;
    }

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    public static void render(DrawContext ctx) {
        if (mode.isEmpty()) return;
        long now = System.currentTimeMillis();
        float p = (now - start) / (float) durationMs;
        float closed; // 0 = abierto, 1 = todo negro
        if (mode.equals("out")) {
            closed = smooth(p);
            if (now - start > durationMs + 8000) { mode = ""; return; } // seguridad: si nunca llega el "in"
        } else {
            if (p >= 1f) { mode = ""; return; }
            closed = smooth(1f - p);
        }

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int bar = (int) (h / 2f * Math.min(1f, closed * 1.15f));
        ctx.fill(0, 0, w, bar, 0xFF000000);
        ctx.fill(0, h - bar, w, h, 0xFF000000);
        if (bar > 0 && bar < h / 2) {
            ctx.fill(0, bar - 1, w, bar, 0xFF7C5CFF);
            ctx.fill(0, h - bar, w, h - bar + 1, 0xFF7C5CFF);
        }
        float full = Math.max(0f, (closed - 0.7f) / 0.3f);
        if (full > 0f) ctx.fill(0, 0, w, h, ((int) (full * 255) << 24));

        // texto
        float ta = mode.equals("out") ? Math.max(0f, (closed - 0.5f) * 2f) : Math.min(1f, closed * 1.8f);
        int a = (int) (ta * 255);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (a > 8 && !title.isEmpty() && mc.textRenderer != null) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(w / 2f, h / 2f - 14, 0);
            ctx.getMatrices().scale(2f, 2f, 1f);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(title), 0, 0, (a << 24) | 0xFFFFFF);
            ctx.getMatrices().pop();
            if (!sub.isEmpty()) {
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(sub), w / 2, h / 2 + 8, (a << 24) | 0xC8C8E0);
            }
        }
    }
}
