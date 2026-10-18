package com.admincontrol.client;

import com.admincontrol.Config;
import com.admincontrol.Matchers;
import com.admincontrol.Net;
import com.admincontrol.RuleDefs;
import com.admincontrol.TimerManager;
import com.admincontrol.gui.Categories;
import com.admincontrol.gui.Icons;
import com.admincontrol.gui.Kind;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Panel de administración dibujado a mano (modo inmediato). Edita una copia de la config y la envía al servidor. */
public class AdminScreen extends Screen {
    private static final Gson GSON = new Gson();

    // ---------------- paleta ----------------
    private static final int C_PANEL = 0xFF171923, C_PANEL2 = 0xFF212434, C_SIDE = 0xFF11131A, C_LINE = 0xFF30344A,
            C_ACC = 0xFF7C5CFF, C_ACC2 = 0xFF2BB6D6, C_OK = 0xFF2EB872, C_BAD = 0xFFE0494F, C_WARN = 0xFFE59A2E,
            C_TEXT = 0xFFEDEEF5, C_MUTED = 0xFF8D91A6, C_BTN = 0xFF2B3047;

    // ---------------- datos del servidor ----------------
    private Config cfg = new Config();
    private String selfUuid = "";
    private record PInfo(String uuid, String name, boolean admin, boolean vanished) {}
    private record Group(String host, List<String> members) {}
    private record GRule(String name, boolean value) {}
    private final List<PInfo> players = new ArrayList<>();
    private final List<Group> plan = new ArrayList<>();
    private final List<GRule> gamerules = new ArrayList<>();
    private boolean running;
    private int returnable;
    private record TInfo(int id, String name, int left, int total, boolean paused, boolean everyone, boolean auto) {}
    private final List<TInfo> timers = new ArrayList<>();
    private boolean timerEveryone = true, timerAuto = false;
    private long lastRefresh, lastEdit;
    private String timerMsg = "";

    // ---------------- estado de la interfaz ----------------
    private record Sec(String id, String title, Item icon) {}
    private static final List<Sec> SECTIONS = List.of(
            new Sec("items", "Items", Items.BARRIER),
            new Sec("spawn", "Spawn", Items.ZOMBIE_HEAD),
            new Sec("damage", "Daño", Items.IRON_SWORD),
            new Sec("rules", "Reglas", Items.TNT),
            new Sec("players", "Jugadores", Items.PLAYER_HEAD),
            new Sec("event", "Evento", Items.ENDER_PEARL),
            new Sec("admin", "Vanish", Items.ENDER_EYE));

    private record Cat(String id, String title, Item icon) {}
    private static final List<Cat> CATS = List.of(
            new Cat("building", "Construcción", Items.BRICKS), new Cat("decor", "Decoración", Items.POPPY),
            new Cat("redstone", "Redstone", Items.REDSTONE), new Cat("tools", "Herramientas", Items.IRON_PICKAXE),
            new Cat("combat", "Combate", Items.IRON_SWORD), new Cat("armor", "Armadura", Items.IRON_CHESTPLATE),
            new Cat("food", "Comida", Items.APPLE), new Cat("materials", "Materiales", Items.IRON_INGOT),
            new Cat("eggs", "Huevos de mob", Items.ZOMBIE_SPAWN_EGG), new Cat("blocked", "Bloqueados", Items.BARRIER),
            new Cat("all", "Todos", Items.CHEST));

    private String section = "items";
    private String itemView = "grid", itemCat = "building";
    private int itemPage;
    private Identifier selItem;
    private String spawnTab = "blocked", editRule = null, editBan = null;
    private String ruleTab = "switch", ruleCat = "use";
    private String eventTab = "prep";
    private boolean confirmRun;
    private String excPlayer = null;

    private final Map<String, StringBuilder> fields = new HashMap<>();
    private final Map<String, Runnable> submits = new HashMap<>();
    private String active = null;
    private final Map<String, Integer> scroll = new HashMap<>();
    private boolean dirty;
    private int ver;

    // picker (ventana modal para elegir items, mobs, biomas, jugadores)
    private Consumer<String> pickAction;
    private Kind pickKind;
    private boolean pickPlayers, pickClose;
    private String pickTitle = "";
    private Predicate<String> pickMarked;
    private List<String> pickAll = List.of(), pickShown = List.of();
    private String pickQuery = "\u0000";

    // item grid cache
    private List<Identifier> itemCache = List.of();
    private String itemCacheKey = "";
    private final Map<Identifier, ItemStack> stackCache = new HashMap<>();
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    // ---------------- inmediato ----------------
    private DrawContext g;
    private int mx, my, layer, pw, ph, px, py;
    private record Hit(int x, int y, int w, int h, int layer, Runnable run) {}
    private record Area(String key, int x, int y, int w, int h, int layer) {}
    private final List<Hit> hits = new ArrayList<>();
    private final List<Area> areas = new ArrayList<>();
    private List<Text> tip;

    private record RB(String text, int color, Runnable act) {}
    private record Row(ItemStack icon, String label, String sub, List<RB> btns, Runnable click) {}
    private record Tog(String label, String desc, BooleanSupplier get, Consumer<Boolean> set) {}

    public AdminScreen(String json) {
        super(Text.literal("Admin Control"));
        applyState(json);
    }

    // =====================================================================
    // Estado
    // =====================================================================
    public void applyState(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (o.has("config")) {
            Config parsed = GSON.fromJson(o.get("config"), Config.class);
            parsed.normalize();
            // si acabas de editar, no pisar tu copia con un estado del servidor que pudo salir antes de recibir el cambio
            if (System.currentTimeMillis() - lastEdit > 1500) cfg = parsed;
        }
        selfUuid = o.get("self").getAsString();
        players.clear();
        for (JsonElement e : o.getAsJsonArray("players")) {
            JsonObject j = e.getAsJsonObject();
            players.add(new PInfo(j.get("uuid").getAsString(), j.get("name").getAsString(),
                    j.get("admin").getAsBoolean(), j.get("vanished").getAsBoolean()));
        }
        plan.clear();
        for (JsonElement e : o.getAsJsonArray("plan")) {
            JsonObject j = e.getAsJsonObject();
            List<String> ms = new ArrayList<>();
            for (JsonElement m : j.getAsJsonArray("members")) ms.add(m.getAsString());
            plan.add(new Group(j.get("host").getAsString(), ms));
        }
        running = o.get("running").getAsBoolean();
        returnable = o.get("returnable").getAsInt();
        gamerules.clear();
        for (JsonElement e : o.getAsJsonArray("gamerules")) {
            JsonObject j = e.getAsJsonObject();
            gamerules.add(new GRule(j.get("name").getAsString(), j.get("value").getAsBoolean()));
        }
        timers.clear();
        if (o.has("timers")) {
            for (JsonElement e : o.getAsJsonArray("timers")) {
                JsonObject j = e.getAsJsonObject();
                timers.add(new TInfo(j.get("id").getAsInt(), j.get("name").getAsString(), j.get("left").getAsInt(),
                        j.get("total").getAsInt(), j.get("paused").getAsBoolean(), j.get("everyone").getAsBoolean(), j.get("auto").getAsBoolean()));
            }
        }
        ver++;
    }

    private void touch() { dirty = true; ver++; lastEdit = System.currentTimeMillis(); }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        if (!ClientPlayNetworking.canSend(Net.ConfigChunk.ID)) return;
        String json = GSON.toJson(cfg);
        int size = 8000;
        int total = Math.max(1, (json.length() + size - 1) / size);
        for (int i = 0; i < total; i++) {
            ClientPlayNetworking.send(new Net.ConfigChunk(i, total, json.substring(i * size, Math.min(json.length(), (i + 1) * size))));
        }
    }

    private void cmd(String c, String a) {
        flush();
        if (ClientPlayNetworking.canSend(Net.Cmd.ID)) ClientPlayNetworking.send(new Net.Cmd(c, a));
    }

    private PInfo player(String uuid) {
        for (PInfo p : players) if (p.uuid().equals(uuid)) return p;
        return null;
    }

    private String nameOf(String uuid) {
        PInfo p = player(uuid);
        return p != null ? p.name() : cfg.nameOf(uuid);
    }

    // =====================================================================
    // Dibujo base
    // =====================================================================
    private void rr(int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private void txt(String s, int x, int y, int c) { g.drawText(textRenderer, s, x, y, c, false); }

    private void txtC(String s, int cx, int y, int c) { g.drawText(textRenderer, s, cx - textRenderer.getWidth(s) / 2, y, c, false); }

    private String cut(String s, int maxW) {
        if (maxW <= 6) return "";
        if (textRenderer.getWidth(s) <= maxW) return s;
        int ew = textRenderer.getWidth("…");
        int end = s.length();
        while (end > 0 && textRenderer.getWidth(s.substring(0, end)) + ew > maxW) end--;
        return s.substring(0, end) + "…";
    }

    private String cutTail(String s, int maxW) {
        while (!s.isEmpty() && textRenderer.getWidth(s) > maxW) s = s.substring(1);
        return s;
    }

    private void wrap(String s, int x, int y, int w, int color) {
        int yy = y;
        for (OrderedText line : textRenderer.wrapLines(Text.literal(s), w)) {
            g.drawText(textRenderer, line, x, yy, color, false);
            yy += 10;
        }
    }

    private static int lighten(int c) {
        int a = (c >>> 24) & 255, r = Math.min(255, ((c >> 16) & 255) + 28);
        int gg = Math.min(255, ((c >> 8) & 255) + 28), b = Math.min(255, (c & 255) + 28);
        return (a << 24) | (r << 16) | (gg << 8) | b;
    }

    private boolean pickerOpen() { return pickAction != null; }

    private boolean over(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h && (!pickerOpen() || layer == 1);
    }

    private void hit(int x, int y, int w, int h, Runnable r) { hits.add(new Hit(x, y, w, h, layer, r)); }

    private void tip(String... lines) {
        List<Text> l = new ArrayList<>();
        for (String s : lines) for (String part : wrapWords(s, 170)) l.add(Text.literal(part));
        tip = l;
    }

    private List<String> wrapWords(String s, int maxW) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String t = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && textRenderer.getWidth(t) > maxW) {
                out.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(t);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }

    private final Map<Item, ItemStack> itemIcons = new HashMap<>();

    private ItemStack iconStack(Item item) {
        return itemIcons.computeIfAbsent(item, ItemStack::new);
    }

    private final Map<Identifier, Boolean> blockedCache = new HashMap<>();
    private int blockedCacheVer = -1;

    private boolean blockedCached(Identifier id, ItemStack st) {
        if (blockedCacheVer != ver) { blockedCache.clear(); blockedCacheVer = ver; }
        return blockedCache.computeIfAbsent(id, k -> Categories.blockedMatch(st.getItem(), cfg));
    }

    private void button(String label, int x, int y, int w, int h, int color, Runnable act) {
        boolean hov = over(x, y, w, h);
        rr(x, y, w, h, hov ? lighten(color) : color);
        txtC(cut(label, w - 4), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        hit(x, y, w, h, act);
    }

    private void chip(int x, int y, Item icon, String name, boolean sel, Runnable act) {
        boolean hov = over(x, y, 18, 18);
        rr(x, y, 18, 18, sel ? C_ACC : hov ? 0xFF383D58 : C_BTN);
        g.drawItem(iconStack(icon), x + 1, y + 1);
        hit(x, y, 18, 18, act);
        if (hov) tip(name);
    }

    private void toggle(String label, String desc, int x, int y, int w, boolean v, Consumer<Boolean> set) {
        boolean hov = over(x, y, w, 18);
        rr(x, y, w, 18, hov ? 0xFF2A2E44 : C_PANEL2);
        txt(cut(label, w - 46), x + 6, y + 5, C_TEXT);
        int pxx = x + w - 34;
        rr(pxx, y + 4, 28, 10, v ? C_OK : 0xFF474C66);
        rr(v ? pxx + 16 : pxx + 2, y + 5, 10, 8, 0xFFFFFFFF);
        hit(x, y, w, 18, () -> { set.accept(!v); touch(); });
        if (hov && desc != null) tip(desc);
    }

    private void stepper(String label, String value, int x, int y, int w, Runnable minus, Runnable plus) {
        rr(x, y, w, 18, C_PANEL2);
        txt(cut(label, w - 90), x + 6, y + 5, C_TEXT);
        button("-", x + w - 78, y + 1, 16, 16, C_BTN, minus);
        txtC(value, x + w - 44, y + 5, C_ACC2);
        button("+", x + w - 22, y + 1, 16, 16, C_BTN, plus);
    }

    private void tabs(int x, int y, int w, String[][] tabs, String cur, Consumer<String> set) {
        int tw = w / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            final String id = tabs[i][0];
            boolean sel = id.equals(cur);
            int tx = x + i * tw;
            boolean hov = over(tx, y, tw - 2, 18);
            rr(tx, y, tw - 2, 18, sel ? C_ACC : hov ? 0xFF383D58 : C_BTN);
            txtC(tabs[i][1], tx + (tw - 2) / 2, y + 5, sel ? 0xFFFFFFFF : C_MUTED);
            hit(tx, y, tw - 2, 18, () -> { set.accept(id); active = null; });
        }
    }

    // ---------------- campos de texto ----------------
    private String fieldVal(String key) { return fields.computeIfAbsent(key, k -> new StringBuilder()).toString(); }

    private void fieldClear(String key) { fields.put(key, new StringBuilder()); }

    private void field(String key, String placeholder, int x, int y, int w, int h, Runnable onEnter) {
        boolean act = key.equals(active);
        rr(x, y, w, h, act ? 0xFF2A2F48 : 0xFF1B1E2C);
        g.drawBorder(x, y, w, h, act ? C_ACC : C_LINE);
        String v = fieldVal(key);
        boolean empty = v.isEmpty();
        String shown = empty && !act ? placeholder : cutTail(v + (act && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : ""), w - 10);
        txt(shown, x + 4, y + (h - 8) / 2, empty && !act ? C_MUTED : C_TEXT);
        hit(x, y, w, h, () -> active = key);
        if (onEnter != null) submits.put(key, onEnter);
    }

    // ---------------- filas con scroll ----------------
    private void rows(String key, int x, int y, int w, int h, List<Row> list) {
        rows(key, x, y, w, h, list, "(vacío)");
    }

    private void rows(String key, int x, int y, int w, int h, List<Row> list, String empty) {
        int rh = 22;
        int vis = Math.max(1, h / rh);
        int max = Math.max(0, list.size() - vis);
        int off = Math.max(0, Math.min(scroll.getOrDefault(key, 0), max));
        scroll.put(key, off);
        areas.add(new Area(key, x, y, w, h, layer));
        rr(x, y, w, h, C_PANEL2);
        if (list.isEmpty()) { txt(cut(empty, w - 16), x + 8, y + 8, C_MUTED); return; }
        for (int i = 0; i < vis && off + i < list.size(); i++) {
            Row r = list.get(off + i);
            int ry = y + i * rh;
            if (i > 0) g.fill(x + 4, ry, x + w - 10, ry + 1, C_LINE);
            if (r.click() != null) {
                if (over(x, ry, w - 6, rh)) g.fill(x, ry, x + w - 6, ry + rh, 0x22FFFFFF);
                hit(x, ry, w - 6, rh, r.click());
            }
            if (r.icon() != null) g.drawItem(r.icon(), x + 4, ry + 3);
            int bx = x + w - 10;
            for (int b = r.btns().size() - 1; b >= 0; b--) {
                RB rb = r.btns().get(b);
                int bw = textRenderer.getWidth(rb.text()) + 10;
                bx -= bw;
                button(rb.text(), bx, ry + 3, bw, 16, rb.color(), rb.act());
                bx -= 3;
            }
            int maxLabel = bx - (x + 26) - 2;
            if (r.sub() == null) {
                txt(cut(r.label(), maxLabel), x + 26, ry + 7, C_TEXT);
            } else {
                txt(cut(r.label(), maxLabel), x + 26, ry + 3, C_TEXT);
                txt(cut(r.sub(), maxLabel), x + 26, ry + 12, C_MUTED);
            }
        }
        if (max > 0) {
            int bh = Math.max(12, h * vis / list.size());
            int by = y + (h - bh) * off / max;
            g.fill(x + w - 4, y, x + w - 1, y + h, 0x33FFFFFF);
            g.fill(x + w - 4, by, x + w - 1, by + bh, C_ACC);
        }
    }

    private ItemStack iconFor(Kind kind, String id) {
        return iconCache.computeIfAbsent(kind + "|" + id, k -> Icons.forPattern(id, kind));
    }

    private ItemStack stackOf(Identifier id) {
        return stackCache.computeIfAbsent(id, k -> new ItemStack(Registries.ITEM.get(k)));
    }

    // =====================================================================
    // Pantalla
    // =====================================================================
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xC00B0C12);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() { flush(); super.removed(); }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        g = ctx; mx = mouseX; my = mouseY; layer = 0;
        hits.clear(); areas.clear(); submits.clear(); tip = null;
        super.render(ctx, mouseX, mouseY, delta);

        pw = Math.min(width - 12, 500);
        ph = Math.min(height - 12, 310);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        rr(px - 1, py - 1, pw + 2, ph + 2, C_ACC);
        rr(px, py, pw, ph, C_PANEL);
        g.fill(px + 1, py, px + pw - 1, py + 22, C_SIDE);
        txt("✦ §lAdmin Control", px + 10, py + 7, C_TEXT);
        txt("K abre este panel · /ac · /vanish", px + 120, py + 7, C_MUTED);
        button("X", px + pw - 22, py + 4, 16, 14, C_BAD, this::close);

        // barra lateral
        int sy = py + 28;
        for (Sec s : SECTIONS) {
            boolean sel = s.id().equals(section);
            boolean hov = over(px + 6, sy, 86, 24);
            rr(px + 6, sy, 86, 24, sel ? C_ACC : hov ? 0xFF2A2E44 : C_SIDE);
            g.drawItem(iconStack(s.icon()), px + 9, sy + 4);
            txt(cut(s.title(), 58), px + 29, sy + 8, sel ? 0xFFFFFFFF : C_MUTED);
            final String id = s.id();
            hit(px + 6, sy, 86, 24, () -> { section = id; active = null; confirmRun = false; });
            sy += 27;
        }

        int cx = px + 98, cy = py + 28, cw = pw - 98 - 8, ch = ph - 28 - 8;
        switch (section) {
            case "items" -> secItems(cx, cy, cw, ch);
            case "spawn" -> secSpawn(cx, cy, cw, ch);
            case "damage" -> secDamage(cx, cy, cw, ch);
            case "rules" -> secRules(cx, cy, cw, ch);
            case "players" -> secPlayers(cx, cy, cw, ch);
            case "event" -> secEvent(cx, cy, cw, ch);
            default -> secAdmin(cx, cy, cw, ch);
        }

        if (pickerOpen()) drawPicker();
        if (tip != null) g.drawTooltip(textRenderer, tip, mouseX, mouseY);
    }

    // =====================================================================
    // Entrada
    // =====================================================================
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return super.mouseClicked(x, y, button);
        int cur = pickerOpen() ? 1 : 0;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.layer() != cur) continue;
            if (x >= h.x() && x < h.x() + h.w() && y >= h.y() && y < h.y() + h.h()) {
                active = null;
                confirmRunReset(h);
                h.run().run();
                flush();
                return true;
            }
        }
        active = null;
        return true;
    }

    private void confirmRunReset(Hit h) { confirmRun = false; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hAmt, double vAmt) {
        int cur = pickerOpen() ? 1 : 0;
        for (Area a : areas) {
            if (a.layer() != cur) continue;
            if (mouseX >= a.x() && mouseX < a.x() + a.w() && mouseY >= a.y() && mouseY < a.y() + a.h()) {
                if (a.key().equals("grid")) itemPage = Math.max(0, itemPage + (vAmt > 0 ? -1 : 1));
                else scroll.merge(a.key(), vAmt > 0 ? -2 : 2, Integer::sum);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, hAmt, vAmt);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (active != null && chr >= 32 && chr != 127) {
            StringBuilder sb = fields.computeIfAbsent(active, k -> new StringBuilder());
            if (sb.length() < 120) sb.append(chr);
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (active != null) {
            StringBuilder sb = fields.computeIfAbsent(active, k -> new StringBuilder());
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (sb.length() > 0) sb.setLength(sb.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                Runnable r = submits.get(active);
                if (r != null) { r.run(); flush(); }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { active = null; return true; }
            if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_V && client != null) {
                String clip = client.keyboard.getClipboard();
                if (clip != null) sb.append(clip, 0, Math.min(clip.length(), Math.max(0, 120 - sb.length())));
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && pickerOpen()) { closePicker(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // =====================================================================
    // Utilidades de secciones
    // =====================================================================
    private void toggleSet(Set<String> set, String v) {
        if (!set.remove(v)) set.add(v);
        touch();
    }

    private static double round2(double d) { return Math.round(d * 100.0) / 100.0; }

    private static String fmt(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.2f", d);
    }

    private String firstMatch(Set<String> set, ItemStack st) {
        for (String pat : set) if (Matchers.item(st, pat)) return pat;
        return null;
    }

    /** Lista editable de patrones: quitar, añadir por texto o elegir de una lista. */
    private void patternPanel(int x, int y, int w, int h, String title, String desc, Set<String> set, Kind kind) {
        txt(title, x, y, C_TEXT);
        txt(cut(desc, w), x, y + 11, C_MUTED);
        List<Row> rs = new ArrayList<>();
        for (String p : new ArrayList<>(set)) {
            rs.add(new Row(iconFor(kind, p), p, null, List.of(new RB("Quitar", C_BAD, () -> { set.remove(p); touch(); })), null));
        }
        rows("pat:" + title, x, y + 24, w, h - 24 - 24, rs);
        int by = y + h - 20;
        String fk = "pf:" + title;
        Runnable add = () -> {
            String v = fieldVal(fk).trim();
            if (!v.isEmpty()) { set.add(Matchers.norm(v)); fieldClear(fk); touch(); }
        };
        field(fk, "mod:* | #tag | id", x, by, Math.max(60, w - 200), 18, add);
        button("+ Patrón", x + w - 196, by, 58, 18, C_BTN, add);
        button("+ Elegir de la lista", x + w - 134, by, 134, 18, C_ACC, () ->
                openPicker(kind, title, set::contains, id -> set.add(id), false));
    }

    /** Lista de multiplicadores (spawn / daño) con -, + y quitar. */
    private void multPanel(int x, int y, int w, int h, String title, String desc, Map<String, Double> map,
                           double step, double max, Kind kind, double defNew) {
        txt(title, x, y, C_TEXT);
        txt(cut(desc, w), x, y + 11, C_MUTED);
        List<Row> rs = new ArrayList<>();
        for (String key : new ArrayList<>(map.keySet())) {
            double val = map.get(key);
            rs.add(new Row(iconFor(kind, key), key, "x" + fmt(val), List.of(
                    new RB("-", C_BTN, () -> map.put(key, round2(Math.max(0, map.get(key) - (Screen.hasShiftDown() ? step * 10 : step))))),
                    new RB("+", C_BTN, () -> map.put(key, round2(Math.min(max, map.get(key) + (Screen.hasShiftDown() ? step * 10 : step))))),
                    new RB("Quitar", C_BAD, () -> map.remove(key))), null));
        }
        rows("mult:" + title, x, y + 24, w, h - 24 - 24, rs);
        int by = y + h - 20;
        String fk = "mf:" + title;
        Runnable add = () -> {
            String v = fieldVal(fk).trim();
            if (!v.isEmpty()) { map.putIfAbsent(Matchers.norm(v), defNew); fieldClear(fk); touch(); }
        };
        field(fk, "mod:* | id", x, by, Math.max(60, w - 200), 18, add);
        button("+ Patrón", x + w - 196, by, 58, 18, C_BTN, add);
        button("+ Elegir de la lista", x + w - 134, by, 134, 18, C_ACC, () ->
                openPicker(kind, title, map::containsKey, id -> { map.putIfAbsent(id, defNew); touch(); }, false));
    }

    // =====================================================================
    // ITEMS
    // =====================================================================
    private void secItems(int x, int y, int w, int h) {
        int cx = x, cy = y;
        for (Cat c : CATS) {
            if (cx + 20 > x + w) { cx = x; cy += 20; }
            final Cat cat = c;
            chip(cx, cy, cat.icon(), cat.title(), itemView.equals("grid") && itemCat.equals(cat.id()),
                    () -> { itemView = "grid"; itemCat = cat.id(); itemPage = 0; selItem = null; });
            cx += 20;
        }
        cx += 8;
        String[][] extra = {{"patterns", "Patrones bloqueados"}, {"recipes", "Recetas bloqueadas"}, {"protected", "Bloques protegidos"}, {"options", "Opciones de bloqueo"}};
        Item[] icons = {Items.WRITABLE_BOOK, Items.CRAFTING_TABLE, Items.OBSIDIAN, Items.COMPARATOR};
        for (int i = 0; i < extra.length; i++) {
            if (cx + 20 > x + w) { cx = x; cy += 20; }
            final String id = extra[i][0];
            chip(cx, cy, icons[i], extra[i][1], itemView.equals(id), () -> { itemView = id; });
            cx += 20;
        }
        int top = cy + 24;
        int hh = h - (top - y);
        switch (itemView) {
            case "patterns" -> patternPanel(x, top, w, hh, "Patrones bloqueados", "Aquí se desbloquea para todos. Admite mod:* y #tags", cfg.blockedItems, Kind.ITEM);
            case "recipes" -> patternPanel(x, top, w, hh, "Recetas bloqueadas", "No se pueden craftear en mesa (siguen existiendo)", cfg.blockedRecipes, Kind.ITEM);
            case "protected" -> patternPanel(x, top, w, hh, "Bloques protegidos", "Nadie (sin bypass) puede romperlos", cfg.protectedBlocks, Kind.BLOCK);
            case "options" -> {
                toggle("Sistema de bloqueo activo", "Interruptor general", x, top, w, cfg.itemsEnabled, v -> cfg.itemsEnabled = v);
                toggle("Bloquear USO", "Usar, colocar y atacar con el item", x, top + 22, w, cfg.blockUse, v -> cfg.blockUse = v);
                toggle("Bloquear RECOGER", "Recogerlo del suelo", x, top + 44, w, cfg.blockPickup, v -> cfg.blockPickup = v);
                toggle("Bloquear INVENTARIO", "Tenerlo: creativo, crafteo, cofres...", x, top + 66, w, cfg.blockInventory, v -> cfg.blockInventory = v);
                toggle("Los admins ignoran los bloqueos", "Apágalo para probar los bloqueos contigo mismo", x, top + 88, w, cfg.opsBypass, v -> cfg.opsBypass = v);
            }
            default -> itemGrid(x, top, w, hh);
        }
    }

    private List<Identifier> visibleItems() {
        String q = fieldVal("isearch").toLowerCase();
        String key = itemCat + "|" + q + "|" + ver;
        if (!key.equals(itemCacheKey)) {
            List<Identifier> out = new ArrayList<>();
            for (Identifier id : Registries.ITEM.getIds()) {
                if (!Categories.has(id, itemCat, cfg)) continue;
                if (!q.isEmpty() && !id.toString().contains(q)
                        && !Registries.ITEM.get(id).getName().getString().toLowerCase().contains(q)) continue;
                out.add(id);
            }
            out.sort(Comparator.comparing(Identifier::toString));
            itemCache = out;
            itemCacheKey = key;
        }
        return itemCache;
    }

    private void itemGrid(int x, int y, int w, int h) {
        int detailW = 140;
        int gridW = w - detailW - 6;
        field("isearch", "Buscar item...", x, y, Math.min(gridW - 64, 170), 16, null);
        List<Identifier> list = visibleItems();
        int cols = Math.max(1, gridW / 20);
        int gy = y + 22;
        int rowsN = Math.max(1, (h - 22 - 4) / 20);
        int per = cols * rowsN;
        int pages = Math.max(1, (list.size() + per - 1) / per);
        itemPage = Math.max(0, Math.min(itemPage, pages - 1));

        int nx = x + gridW - 58;
        button("<", nx, y, 16, 16, C_BTN, () -> itemPage = Math.max(0, itemPage - 1));
        txtC((itemPage + 1) + "/" + pages, nx + 29, y + 4, C_MUTED);
        button(">", nx + 42, y, 16, 16, C_BTN, () -> itemPage = Math.min(pages - 1, itemPage + 1));

        areas.add(new Area("grid", x, gy, gridW, rowsN * 20, layer));
        for (int i = 0; i < per; i++) {
            int idx = itemPage * per + i;
            if (idx >= list.size()) break;
            Identifier id = list.get(idx);
            int cx = x + (i % cols) * 20, cy = gy + (i / cols) * 20;
            boolean sel = id.equals(selItem);
            boolean hov = over(cx, cy, 18, 18);
            rr(cx, cy, 18, 18, sel ? C_ACC : hov ? 0xFF383D58 : C_PANEL2);
            ItemStack st = stackOf(id);
            g.drawItem(st, cx + 1, cy + 1);
            if (blockedCached(id, st)) g.fill(cx, cy, cx + 18, cy + 18, 0x66E0494F);
            hit(cx, cy, 18, 18, () -> selItem = id);
            if (hov) tip(st.getName().getString(), "§8" + id);
        }
        if (list.isEmpty()) txt("Sin resultados", x + 6, gy + 6, C_MUTED);
        itemDetail(x + gridW + 6, y, detailW, h);
    }

    private void itemDetail(int x, int y, int w, int h) {
        rr(x, y, w, h, C_PANEL2);
        if (selItem == null) {
            wrap("Haz clic en un item para ver su detalle y bloquearlo.", x + 6, y + 8, w - 12, C_MUTED);
            return;
        }
        Item item = Registries.ITEM.get(selItem);
        ItemStack st = stackOf(selItem);
        String key = selItem.toString();
        g.getMatrices().push();
        g.getMatrices().translate(x + 6, y + 6, 0);
        g.getMatrices().scale(2f, 2f, 1f);
        g.drawItem(st, 0, 0);
        g.getMatrices().pop();
        txt(cut(st.getName().getString(), w - 46), x + 42, y + 8, C_TEXT);
        txt(cut(key, w - 46), x + 42, y + 20, C_MUTED);
        txt("Mod: " + selItem.getNamespace(), x + 6, y + 42, C_MUTED);

        String bp = firstMatch(cfg.blockedItems, st);
        String rp = firstMatch(cfg.blockedRecipes, st);
        boolean isBlock = item instanceof BlockItem;
        String blockKey = null;
        boolean prot = false;
        if (isBlock) {
            var blk = ((BlockItem) item).getBlock();
            blockKey = Registries.BLOCK.getId(blk).toString();
            for (String pat : cfg.protectedBlocks) if (Matchers.block(blk.getDefaultState(), pat)) prot = true;
        }
        txt("Ítem: " + (bp == null ? "Permitido" : "BLOQUEADO"), x + 6, y + 54, bp == null ? C_OK : C_BAD);
        txt("Receta: " + (rp == null ? "Permitida" : "BLOQUEADA"), x + 6, y + 64, rp == null ? C_OK : C_BAD);
        if (isBlock) txt("Bloque: " + (prot ? "PROTEGIDO" : "Minable"), x + 6, y + 74, prot ? C_BAD : C_OK);

        int by = y + 88, bw = w - 12, bh = 15, gap = 17;
        final String fk = key;
        boolean blockedExact = cfg.blockedItems.contains(key);
        button(blockedExact ? "Desbloquear ítem" : "Bloquear ítem", x + 6, by, bw, bh, blockedExact ? C_OK : C_BAD, () -> toggleSet(cfg.blockedItems, fk));
        boolean recExact = cfg.blockedRecipes.contains(key);
        button(recExact ? "Desbloquear receta" : "Bloquear receta", x + 6, by + gap, bw, bh, recExact ? C_OK : C_WARN, () -> toggleSet(cfg.blockedRecipes, fk));
        if (isBlock) {
            final String bk = blockKey;
            boolean pe = cfg.protectedBlocks.contains(bk);
            button(pe ? "Quitar protección" : "Proteger bloque", x + 6, by + gap * 2, bw, bh, pe ? C_OK : C_ACC, () -> toggleSet(cfg.protectedBlocks, bk));
        }
        button("Obtener x1", x + 6, by + gap * 3, bw / 2 - 1, bh, C_BTN, () -> cmd("give", fk + "|1"));
        final int stack = item.getMaxCount();
        button("Stack", x + 6 + bw / 2 + 1, by + gap * 3, bw / 2 - 1, bh, C_BTN, () -> cmd("give", fk + "|" + stack));
        button("Permitir a jugador...", x + 6, by + gap * 4, bw, bh, C_ACC2, () ->
                openPlayerPicker("Permitir " + fk + " a...",
                        id -> cfg.playerExceptions.getOrDefault(id, Set.of()).contains(fk),
                        uuid -> cfg.playerExceptions.computeIfAbsent(uuid, k -> new LinkedHashSet<>()).add(fk), false));
    }

    // =====================================================================
    // SPAWN
    // =====================================================================
    private Config.SpawnRule ruleOf(String entity) {
        if (entity == null) return null;
        for (Config.SpawnRule r : cfg.extraSpawns) if (entity.equals(r.entity)) return r;
        return null;
    }

    private void secSpawn(int x, int y, int w, int h) {
        tabs(x, y, w, new String[][]{{"blocked", "Bloqueados"}, {"mult", "Más / menos"}, {"rules", "Biomas +"}, {"ban", "Prohibir"}, {"opt", "Ajustes"}},
                spawnTab, v -> { spawnTab = v; editRule = null; editBan = null; });
        int top = y + 24, hh = h - 24;
        switch (spawnTab) {
            case "blocked" -> patternPanel(x, top, w, hh, "Mobs sin spawn natural", "Admite mod:* (todos los mobs de un mod)", cfg.blockedSpawns, Kind.ENTITY);
            case "mult" -> multPanel(x, top, w, hh, "Multiplicador de spawn", "x<1 reduce · x>1 aumenta (mob exacto). Shift = pasos grandes", cfg.spawnMultipliers, 0.25, 100, Kind.ENTITY, 2.0);
            case "rules" -> {
                if (ruleOf(editRule) == null) ruleList(x, top, w, hh); else ruleEditor(x, top, w, hh, ruleOf(editRule));
            }
            case "ban" -> {
                if (editBan == null || !cfg.blockedSpawnBiomes.containsKey(editBan)) banList(x, top, w, hh); else banEditor(x, top, w, hh);
            }
            default -> {
                toggle("Sistema de spawn activo", "Interruptor general", x, top, w, cfg.spawnEnabled, v -> cfg.spawnEnabled = v);
                stepper("Intervalo del spawner (ticks)", String.valueOf(cfg.spawnerIntervalTicks), x, top + 24, w,
                        () -> { cfg.spawnerIntervalTicks = Math.max(10, cfg.spawnerIntervalTicks - (Screen.hasShiftDown() ? 100 : 10)); touch(); },
                        () -> { cfg.spawnerIntervalTicks = Math.min(1200, cfg.spawnerIntervalTicks + (Screen.hasShiftDown() ? 100 : 10)); touch(); });
                wrap("Cada cuántos ticks el mod intenta crear mobs extra (20 ticks = 1 s).", x, top + 48, w, C_MUTED);
            }
        }
    }

    private void ruleList(int x, int y, int w, int h) {
        txt("Hacer que un mob aparezca en otros biomas", x, y, C_TEXT);
        txt("Ej: ahogados en sabana o taiga", x, y + 11, C_MUTED);
        List<Row> rs = new ArrayList<>();
        for (Config.SpawnRule r : new ArrayList<>(cfg.extraSpawns)) {
            rs.add(new Row(iconFor(Kind.ENTITY, r.entity), r.entity + (r.enabled ? "" : " (apagada)"),
                    "Biomas: " + r.biomes + " · prob " + fmt(r.chance), List.of(
                    new RB("Editar", C_ACC, () -> editRule = r.entity),
                    new RB("Borrar", C_BAD, () -> { cfg.extraSpawns.remove(r); touch(); })), null));
        }
        rows("rules", x, y + 24, w, h - 24 - 24, rs);
        button("+ Nueva regla (elegir mob)", x, y + h - 20, w, 18, C_ACC, () ->
                openPicker(Kind.ENTITY, "Elige el mob", id -> ruleOf(id) != null, id -> {
                    if (id.contains("*") || id.startsWith("#")) return;
                    if (ruleOf(id) == null) { Config.SpawnRule r = new Config.SpawnRule(); r.entity = id; cfg.extraSpawns.add(r); }
                    editRule = id;
                }, true));
    }

    private void ruleEditor(int x, int y, int w, int h, Config.SpawnRule r) {
        txt("Regla: " + r.entity, x, y + 3, C_TEXT);
        button("< Volver", x + w - 130, y, 60, 16, C_BTN, () -> editRule = null);
        button("Borrar", x + w - 66, y, 66, 16, C_BAD, () -> { cfg.extraSpawns.remove(r); editRule = null; touch(); });
        int colW = (w - 6) / 2;
        int cy = y + 22;
        boolean shift = Screen.hasShiftDown();
        stepper("Probabilidad", fmt(r.chance), x, cy, colW,
                () -> { r.chance = round2(Math.max(0, r.chance - (shift ? 0.5 : 0.05))); touch(); },
                () -> { r.chance = round2(Math.min(1, r.chance + (shift ? 0.5 : 0.05))); touch(); });
        stepper("Máximo cerca", String.valueOf(r.maxNearby), x, cy + 21, colW,
                () -> { r.maxNearby = Math.max(1, r.maxNearby - (shift ? 5 : 1)); touch(); },
                () -> { r.maxNearby = Math.min(200, r.maxNearby + (shift ? 5 : 1)); touch(); });
        stepper("Grupo mínimo", String.valueOf(r.minGroup), x, cy + 42, colW,
                () -> { r.minGroup = Math.max(1, r.minGroup - 1); touch(); },
                () -> { r.minGroup = Math.min(50, r.minGroup + 1); if (r.maxGroup < r.minGroup) r.maxGroup = r.minGroup; touch(); });
        stepper("Grupo máximo", String.valueOf(r.maxGroup), x, cy + 63, colW,
                () -> { r.maxGroup = Math.max(r.minGroup, r.maxGroup - 1); touch(); },
                () -> { r.maxGroup = Math.min(50, r.maxGroup + 1); touch(); });
        toggle("Forzar colocación", "Ignora reglas vanilla (ej. ahogado en tierra)", x, cy + 86, colW, r.forcePlacement, v -> r.forcePlacement = v);
        toggle("Solo en oscuridad", "Solo aparece con poca luz", x, cy + 107, colW, r.requireDark, v -> r.requireDark = v);
        toggle("Regla activa", "Apagar sin borrarla", x, cy + 128, colW, r.enabled, v -> r.enabled = v);
        biomePanel("rb", x + colW + 6, y + 22, colW, h - 22, r.biomes, true);
    }

    private void banList(int x, int y, int w, int h) {
        txt("Prohibir mobs en biomas", x, y, C_TEXT);
        txt("Ej: zombies no aparecen en desierto", x, y + 11, C_MUTED);
        cfg.blockedSpawnBiomes.values().removeIf(Set::isEmpty);
        List<Row> rs = new ArrayList<>();
        for (String ent : new ArrayList<>(cfg.blockedSpawnBiomes.keySet())) {
            rs.add(new Row(iconFor(Kind.ENTITY, ent), ent, "Prohibido en: " + cfg.blockedSpawnBiomes.get(ent), List.of(
                    new RB("Editar", C_ACC, () -> editBan = ent),
                    new RB("Quitar", C_BAD, () -> { cfg.blockedSpawnBiomes.remove(ent); touch(); })), null));
        }
        rows("ban", x, y + 24, w, h - 24 - 24, rs);
        button("+ Elegir mob", x, y + h - 20, w, 18, C_ACC, () ->
                openPicker(Kind.ENTITY, "Elige el mob", id -> cfg.blockedSpawnBiomes.containsKey(id), id -> {
                    cfg.blockedSpawnBiomes.computeIfAbsent(id, k -> new LinkedHashSet<>());
                    editBan = id;
                }, true));
    }

    private void banEditor(int x, int y, int w, int h) {
        txt("Prohibido: " + editBan, x, y + 3, C_TEXT);
        button("< Volver", x + w - 62, y, 62, 16, C_BTN, () -> editBan = null);
        biomePanel("bb", x, y + 22, w, h - 22, cfg.blockedSpawnBiomes.get(editBan), false);
    }

    /** Lista de biomas (patrones) editable. */
    private void biomePanel(String id, int x, int y, int w, int h, Collection<String> col, boolean allowAll) {
        txt("Biomas", x, y + 2, C_TEXT);
        List<Row> rs = new ArrayList<>();
        for (String b : new ArrayList<>(col)) {
            rs.add(new Row(iconFor(Kind.BIOME, b), b, null, List.of(new RB("x", C_BAD, () -> { col.remove(b); touch(); })), null));
        }
        rows("bio:" + id, x, y + 14, w, h - 14 - 44, rs);
        int by = y + h - 40;
        String fk = "bf:" + id;
        Runnable add = () -> {
            String v = fieldVal(fk).trim();
            if (!v.isEmpty()) { col.add(Matchers.norm(v)); fieldClear(fk); touch(); }
        };
        field(fk, "bioma o #tag", x, by, w - 56, 18, add);
        button("+ Añadir", x + w - 52, by, 52, 18, C_BTN, add);
        int half = allowAll ? (w - 4) / 2 : w;
        button("+ Elegir bioma", x, by + 21, half, 18, C_ACC, () ->
                openPicker(Kind.BIOME, "Elige biomas", col::contains, bid -> { if (!col.contains(bid)) col.add(bid); }, false));
        if (allowAll) button("+ Todos (*)", x + half + 4, by + 21, w - half - 4, 18, C_ACC2, () -> { if (!col.contains("*")) col.add("*"); touch(); });
    }

    // =====================================================================
    // DAÑO DE MOBS
    // =====================================================================
    private void secDamage(int x, int y, int w, int h) {
        toggle("Sistema de daño activo", "Interruptor general", x, y, w, cfg.damageEnabled, v -> cfg.damageEnabled = v);
        boolean shift = Screen.hasShiftDown();
        stepper("Multiplicador global (mobs)", "x" + fmt(cfg.globalMobDamageMultiplier), x, y + 22, w,
                () -> { cfg.globalMobDamageMultiplier = round2(Math.max(0, cfg.globalMobDamageMultiplier - (shift ? 2.5 : 0.25))); touch(); },
                () -> { cfg.globalMobDamageMultiplier = round2(Math.min(1000, cfg.globalMobDamageMultiplier + (shift ? 2.5 : 0.25))); touch(); });
        multPanel(x, y + 48, w, h - 48, "Daño por mob", "Daño que HACE cada mob (incluye mobs de mods). Shift = pasos grandes",
                cfg.damageMultipliers, 0.25, 1000, Kind.ENTITY, 2.0);
    }

    // =====================================================================
    // REGLAS EXTREMAS
    // =====================================================================
    private final ItemStack leverIcon = new ItemStack(Items.LEVER);
    private final ItemStack headIcon = new ItemStack(Items.PLAYER_HEAD);

    private Map<String, Double> catMap(String k) {
        return switch (k) {
            case "use" -> cfg.useDamage;
            case "stand" -> cfg.standDamage;
            case "break" -> cfg.breakDamage;
            case "place" -> cfg.placeDamage;
            default -> cfg.itemDamage;
        };
    }

    private void secRules(int x, int y, int w, int h) {
        tabs(x, y, w, new String[][]{{"switch", "Interruptores"}, {"dmg", "Daño extremo"}, {"gr", "Gamerules"}}, ruleTab, v -> ruleTab = v);
        int top = y + 24, hh = h - 24;
        switch (ruleTab) {
            case "dmg" -> ruleDamage(x, top, w, hh);
            case "gr" -> {
                List<Row> rs = new ArrayList<>();
                for (int i = 0; i < gamerules.size(); i++) {
                    final int idx = i;
                    GRule gr = gamerules.get(i);
                    rs.add(new Row(leverIcon, gr.name(), null, List.of(new RB(gr.value() ? "ACTIVADO" : "desactivado", gr.value() ? C_OK : C_BTN, () -> {
                        cmd("gamerule", gr.name() + "|" + !gr.value());
                        gamerules.set(idx, new GRule(gr.name(), !gr.value()));
                    })), null));
                }
                rows("gr", x, top, w, hh, rs);
            }
            default -> {
                List<Tog> t = new ArrayList<>();
                t.add(new Tog("Hojas sin brotes", "Las hojas ya no sueltan brotes", () -> cfg.noSaplingDrops, v -> cfg.noSaplingDrops = v));
                t.add(new Tog("Sin replantar", "No se pueden plantar brotes", () -> cfg.noSaplingPlanting, v -> cfg.noSaplingPlanting = v));
                for (RuleDefs.Def d : RuleDefs.EXTRA) {
                    t.add(new Tog(d.label(), d.desc(), () -> cfg.rules.contains(d.id()), v -> {
                        if (v) cfg.rules.add(d.id()); else cfg.rules.remove(d.id());
                    }));
                }
                int colW = (w - 6) / 2;
                for (int i = 0; i < t.size(); i++) {
                    Tog tg = t.get(i);
                    toggle(tg.label(), tg.desc(), x + (i % 2) * (colW + 6), top + (i / 2) * 21, colW, tg.get().getAsBoolean(), tg.set());
                }
            }
        }
    }

    private void addDmg(Map<String, Double> map, String key) {
        map.putIfAbsent(key, 1_000_000.0);
        cfg.extremeDamageEnabled = true;
        touch();
    }

    private void ruleDamage(int x, int y, int w, int h) {
        int half = (w - 4) / 2;
        toggle("DAÑO EXTREMO (general)", "Interruptor de estas reglas", x, y, half, cfg.extremeDamageEnabled, v -> cfg.extremeDamageEnabled = v);
        toggle("Afecta a creativo", "Si no, creativo y espectador se salvan", x + half + 4, y, w - half - 4, cfg.extremeAffectCreative, v -> cfg.extremeAffectCreative = v);
        tabs(x, y + 22, w, new String[][]{{"use", "Usar"}, {"stand", "Pisar"}, {"break", "Romper"}, {"place", "Colocar"}, {"item", "Usar item"}}, ruleCat, v -> ruleCat = v);
        Map<String, Double> map = catMap(ruleCat);
        Kind kind = ruleCat.equals("item") ? Kind.ITEM : Kind.BLOCK;
        List<Row> rs = new ArrayList<>();
        for (String key : new ArrayList<>(map.keySet())) {
            double val = map.get(key);
            rs.add(new Row(iconFor(kind, key), key, fmt(val) + " de daño", List.of(
                    new RB("/10", C_BTN, () -> map.put(key, Math.max(1, map.get(key) / 10))),
                    new RB("/2", C_BTN, () -> map.put(key, Math.max(1, map.get(key) / 2))),
                    new RB("x2", C_BTN, () -> map.put(key, Math.min(1.0E9, map.get(key) * 2))),
                    new RB("x10", C_BTN, () -> map.put(key, Math.min(1.0E9, map.get(key) * 10))),
                    new RB("x", C_BAD, () -> map.remove(key))), null));
        }
        rows("dm:" + ruleCat, x, y + 46, w, h - 46 - 24, rs);

        int by = y + h - 20;
        String fk = "df:" + ruleCat;
        Runnable add = () -> {
            String v = fieldVal(fk).trim();
            if (!v.isEmpty()) { addDmg(map, Matchers.norm(v)); fieldClear(fk); }
        };
        List<String[]> presets = ruleCat.equals("use")
                ? List.of(new String[]{"Puertas", "#minecraft:doors"}, new String[]{"Botones", "#minecraft:buttons"})
                : ruleCat.equals("stand") ? List.of(new String[]{"Magma", "minecraft:magma_block"}) : List.of();
        int bx = x + w;
        for (int i = presets.size() - 1; i >= 0; i--) {
            String[] p = presets.get(i);
            int bw = textRenderer.getWidth(p[0]) + 10;
            bx -= bw;
            button(p[0], bx, by, bw, 18, C_ACC2, () -> addDmg(map, p[1]));
            bx -= 3;
        }
        bx -= 50;
        button("+ Lista", bx, by, 50, 18, C_ACC, () -> openPicker(kind, "Elige qué dañará", map::containsKey, id -> addDmg(map, id), false));
        bx -= 3;
        bx -= 58;
        button("+ Patrón", bx, by, 58, 18, C_BTN, add);
        bx -= 3;
        field(fk, "id | #tag (1.000.000 de daño)", x, by, Math.max(40, bx - x), 18, add);
    }

    // =====================================================================
    // JUGADORES
    // =====================================================================
    private void secPlayers(int x, int y, int w, int h) {
        if (excPlayer != null) {
            final String me = excPlayer;
            Set<String> mine = cfg.playerExceptions.computeIfAbsent(me, k -> new LinkedHashSet<>());
            txt(cut("Items permitidos a " + nameOf(me), w - 262), x, y + 5, C_TEXT);
            int bx = x + w;
            bx -= 54;
            button("< Volver", bx, y, 54, 16, C_BTN, () -> { excPlayer = null; excMsg = ""; });
            bx -= 73;
            button("Copiar de...", bx, y, 70, 16, C_ACC2, () ->
                    openPlayerPicker("Copiar las excepciones DE... hacia " + nameOf(me), id -> false, id -> copyExc(id, me), true));
            bx -= 51;
            button("A todos", bx, y, 48, 16, C_ACC, () -> {
                int n = 0;
                for (String id : knownPlayers()) if (!id.equals(me)) { copyExc(me, id); n++; }
                excMsg = "Copiado a " + n + " jugadores";
            });
            bx -= 69;
            button("Copiar a...", bx, y, 66, 16, C_ACC, () ->
                    openPlayerPicker("Copiar las excepciones de " + nameOf(me) + " a...",
                            id -> !id.equals(me) && cfg.playerExceptions.getOrDefault(id, Set.of()).containsAll(mine),
                            id -> copyExc(me, id), false));
            toggleLocal("Reemplazar lo que ya tengan (si no, se suman)", "Al copiar: borra antes las excepciones del destino", x, y + 20, w, copyReplace, v -> copyReplace = v);
            if (!excMsg.isEmpty()) txt(cut(excMsg, w), x, y + 41, C_OK);
            patternPanel(x, y + 52, w, h - 52, "Items permitidos a " + nameOf(me), "Permitidos aunque estén bloqueados para todos", mine, Kind.ITEM);
            return;
        }
        txt("Jugadores en línea", x, y, C_TEXT);
        txt(cut("Inventario y ender chest se abren en vivo y se pueden editar", w), x, y + 11, C_MUTED);
        List<Row> rs = new ArrayList<>();
        for (PInfo p : players) {
            boolean bypass = cfg.fullBypass.contains(p.uuid());
            rs.add(new Row(headIcon, p.name() + (p.admin() ? " §6(admin)" : ""), null, List.of(
                    new RB("Inv", C_ACC, () -> { cmd("inv", p.uuid()); close(); }),
                    new RB("Ender", C_ACC, () -> { cmd("inv", "ender|" + p.uuid()); close(); }),
                    new RB(p.vanished() ? "Vanish ✓" : "Vanish", p.vanished() ? C_OK : C_BTN, () -> cmd("vanish", p.uuid())),
                    new RB(bypass ? "Bypass ✓" : "Bypass", bypass ? C_OK : C_BTN, () -> toggleSet(cfg.fullBypass, p.uuid())),
                    new RB("Items", C_ACC2, () -> excPlayer = p.uuid())), null));
        }
        rows("players", x, y + 24, w, h - 24, rs);
    }

    private boolean copyReplace = false;
    private String excMsg = "";

    /** Jugadores conectados + todos los que han entrado alguna vez (para copiar a offline también). */
    private List<String> knownPlayers() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (PInfo p : players) set.add(p.uuid());
        set.addAll(cfg.names.keySet());
        return new ArrayList<>(set);
    }

    private void copyExc(String from, String to) {
        if (from.equals(to)) return;
        Set<String> src = cfg.playerExceptions.get(from);
        Set<String> dst = cfg.playerExceptions.computeIfAbsent(to, k -> new LinkedHashSet<>());
        if (copyReplace) dst.clear();
        if (src != null) dst.addAll(src);
        excMsg = "Copiado a " + nameOf(to);
        touch();
    }

    // =====================================================================
    // EVENTO
    // =====================================================================
    private void secEvent(int x, int y, int w, int h) {
        tabs(x, y, w, new String[][]{{"prep", "Preparar"}, {"prev", "Vista previa"}, {"timer", "Contadores"}}, eventTab, v -> eventTab = v);
        if (eventTab.equals("timer")) {
            secTimers(x, y + 24, w, h - 24);
            return;
        }
        int top = y + 24, bodyH = h - 24 - 24;
        if (eventTab.equals("prep")) {
            int tableW = w - 160;
            List<Row> rs = new ArrayList<>();
            int hosts = 0, parts = 0;
            for (PInfo p : players) {
                boolean host = cfg.eventHosts.contains(p.uuid());
                boolean ex = cfg.eventExcluded.contains(p.uuid());
                if (host) hosts++; else if (!ex) parts++;
                rs.add(new Row(headIcon, p.name() + (p.admin() ? " §6(admin)" : ""), null, List.of(
                        new RB(host ? "Anfitrión ✓" : "Anfitrión", host ? C_OK : C_BTN, () -> toggleSet(cfg.eventHosts, p.uuid())),
                        new RB(ex ? "Excluido ✓" : "Excluir", ex ? C_BAD : C_BTN, () -> toggleSet(cfg.eventExcluded, p.uuid()))), null));
            }
            rows("evplayers", x, top, tableW, bodyH, rs);
            int rx = x + tableW + 6, rw = w - tableW - 6;
            rr(rx, top, rw, bodyH, C_PANEL2);
            txt("Anfitriones: " + hosts, rx + 6, top + 6, C_TEXT);
            txt("Participantes: " + parts, rx + 6, top + 17, C_TEXT);
            String per = hosts == 0 ? "-" : (parts / hosts) + (parts % hosts == 0 ? "" : " a " + (parts / hosts + 1));
            txt("Por anfitrión: " + per, rx + 6, top + 28, C_ACC2);
            boolean shift = Screen.hasShiftDown();
            stepper("Radio", String.valueOf(cfg.eventRadius), rx + 4, top + 44, rw - 8,
                    () -> { cfg.eventRadius = Math.max(0, cfg.eventRadius - (shift ? 5 : 1)); touch(); },
                    () -> { cfg.eventRadius = Math.min(100, cfg.eventRadius + (shift ? 5 : 1)); touch(); });
            stepper("Cuenta (s)", String.valueOf(cfg.eventCountdown), rx + 4, top + 66, rw - 8,
                    () -> { cfg.eventCountdown = Math.max(0, cfg.eventCountdown - (shift ? 5 : 1)); touch(); },
                    () -> { cfg.eventCountdown = Math.min(60, cfg.eventCountdown + (shift ? 5 : 1)); touch(); });
            button("Admins = anfitriones", rx + 4, top + 92, rw - 8, 16, C_BTN, () -> {
                for (PInfo p : players) if (p.admin()) cfg.eventHosts.add(p.uuid());
                touch();
            });
            button("Limpiar selección", rx + 4, top + 111, rw - 8, 16, C_BTN, () -> {
                cfg.eventHosts.clear();
                cfg.eventExcluded.clear();
                touch();
            });
            if (running) txt("¡Cuenta atrás en curso!", rx + 6, top + 134, C_WARN);
        } else {
            List<Row> rs = new ArrayList<>();
            for (Group gr : plan) {
                rs.add(new Row(headIcon, gr.host() + "  —  " + gr.members().size() + " jugadores", String.join(", ", gr.members()), List.of(), null));
            }
            rows("evplan", x, top, w, bodyH, rs, "Pulsa «Barajar» para generar un reparto aleatorio");
        }

        int by = y + h - 20, bw = (w - 9) / 4;
        button("Barajar", x, by, bw, 18, C_ACC2, () -> { cmd("event", "shuffle"); eventTab = "prev"; });
        final boolean cr = confirmRun;
        button(cr ? "¿SEGURO? OK" : "EJECUTAR", x + bw + 3, by, bw, 18, cr ? C_WARN : C_OK, () -> {
            if (cr) { cmd("event", "run"); confirmRun = false; } else confirmRun = true;
        });
        button("Devolver (" + returnable + ")", x + (bw + 3) * 2, by, bw, 18, C_BTN, () -> cmd("event", "return"));
        button("Cancelar", x + (bw + 3) * 3, by, w - (bw + 3) * 3, 18, C_BAD, () -> cmd("event", "cancel"));
    }


    // =====================================================================
    // CONTADORES DE TIEMPO
    // =====================================================================
    private static String mmss(int sec) {
        return TimerManager.fmt(Math.max(0, sec));
    }

    /** Interruptor que NO toca la configuración (ajuste local de esta pestaña). */
    private void toggleLocal(String label, String desc, int x, int y, int w, boolean v, Consumer<Boolean> set) {
        boolean hov = over(x, y, w, 18);
        rr(x, y, w, 18, hov ? 0xFF2A2E44 : C_PANEL2);
        txt(cut(label, w - 46), x + 6, y + 5, C_TEXT);
        int pxx = x + w - 34;
        rr(pxx, y + 4, 28, 10, v ? C_OK : 0xFF474C66);
        rr(v ? pxx + 16 : pxx + 2, y + 5, 10, 8, 0xFFFFFFFF);
        hit(x, y, w, 18, () -> set.accept(!v));
        if (hov && desc != null) tip(desc);
    }

    private void secTimers(int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        if (!timers.isEmpty() && now - lastRefresh > 1000) {
            lastRefresh = now;
            if (ClientPlayNetworking.canSend(Net.Cmd.ID)) ClientPlayNetworking.send(new Net.Cmd("refreshlite", ""));
        }

        txt("Nuevo contador", x, y, C_TEXT);
        txt(cut("Barra de tiempo arriba. Al llegar a 0 se avisa a los admins para el TP.", w), x, y + 11, C_MUTED);
        int half = (w - 6) / 2;
        Runnable create = () -> {
            int secs = TimerManager.parse(fieldVal("ttime"));
            if (secs <= 0) { timerMsg = "Tiempo inválido. Ejemplos: 5:00 · 90s · 10"; return; }
            String name = fieldVal("tname").replace("|", "/").trim();
            cmd("timer", "create|" + name + "|" + secs + "|" + (timerEveryone ? 1 : 0) + "|" + (timerAuto ? 1 : 0));
            fieldClear("tname");
            timerMsg = "";
        };
        field("tname", "Nombre (opcional)", x, y + 24, half, 18, null);
        field("ttime", "Tiempo: 5:00 (min:seg)", x + half + 6, y + 24, w - half - 6, 18, create);

        String[] presets = {"1:00", "3:00", "5:00", "10:00", "15:00", "30:00"};
        int pw2 = (w - 5 * 3) / presets.length;
        for (int i = 0; i < presets.length; i++) {
            final String ptxt = presets[i];
            button(ptxt, x + i * (pw2 + 3), y + 46, pw2, 16, C_BTN, () -> fields.put("ttime", new StringBuilder(ptxt)));
        }

        toggleLocal("Visible para todos", "Si está apagado, solo participantes y anfitriones", x, y + 66, half, timerEveryone, v -> timerEveryone = v);
        toggleLocal("TP automático al terminar", "Ejecuta el evento (reparto actual) cuando llegue a 0", x + half + 6, y + 66, w - half - 6, timerAuto, v -> timerAuto = v);
        button("▶ Crear e iniciar contador", x, y + 88, w, 18, C_OK, create);
        if (!timerMsg.isEmpty()) txt(cut(timerMsg, w), x, y + 109, C_BAD);

        List<Row> rs = new ArrayList<>();
        for (TInfo t : timers) {
            String title = (t.name().isEmpty() ? "Contador #" + t.id() : t.name()) + "  —  " + mmss(t.left());
            String sub = (t.paused() ? "En pausa" : "Corriendo") + " · " + (t.everyone() ? "para todos" : "participantes")
                    + (t.auto() ? " · TP automático" : "") + " · de " + mmss(t.total());
            rs.add(new Row(iconStack(Items.CLOCK), title, sub, List.of(
                    new RB(t.paused() ? "Seguir" : "Pausa", t.paused() ? C_OK : C_WARN, () -> cmd("timer", (t.paused() ? "resume|" : "pause|") + t.id())),
                    new RB("+1m", C_BTN, () -> cmd("timer", "add|" + t.id() + "|60")),
                    new RB("-1m", C_BTN, () -> cmd("timer", "add|" + t.id() + "|-60")),
                    new RB("x", C_BAD, () -> cmd("timer", "stop|" + t.id()))), null));
        }
        int ly = y + (timerMsg.isEmpty() ? 112 : 122);
        rows("timers", x, ly, w, Math.max(30, h - (ly - y)), rs, "Sin contadores activos");
    }

    // =====================================================================
    // VANISH / ADMIN
    // =====================================================================
    private void secAdmin(int x, int y, int w, int h) {
        PInfo me = player(selfUuid);
        boolean on = me != null && me.vanished();
        rr(x, y, w, 40, C_PANEL2);
        txt("VANISH", x + 8, y + 8, on ? C_OK : C_TEXT);
        txt(cut("Invisible para todos: cuerpo, nombre y lista", w - 110), x + 8, y + 22, C_MUTED);
        button(on ? "ACTIVADO" : "Desactivado", x + w - 92, y + 11, 84, 18, on ? C_OK : C_BTN, () -> cmd("vanish", selfUuid));
        toggle("Otros admins te ven", "Los OP siguen viéndote", x, y + 46, w, cfg.vanishVisibleToOps, v -> cfg.vanishVisibleToOps = v);
        toggle("Los mobs te ignoran", "No te atacan estando en vanish", x, y + 68, w, cfg.vanishIgnoredByMobs, v -> cfg.vanishIgnoredByMobs = v);
        toggle("No recoger items", "No recoges objetos del suelo en vanish", x, y + 90, w, cfg.vanishNoPickup, v -> cfg.vanishNoPickup = v);

        txt("Herramientas", x, y + 118, C_TEXT);
        int bw = (w - 6) / 3;
        button("Bastón de Retorno", x, y + 132, bw, 18, C_ACC, () -> cmd("tool", "stick"));
        button("Varita de Admin", x + bw + 3, y + 132, bw, 18, C_ACC, () -> cmd("tool", "wand"));
        button("Recargar config", x + (bw + 3) * 2, y + 132, w - (bw + 3) * 2, 18, C_BTN, () -> cmd("reload", ""));
        wrap("Bastón de Retorno: golpea a un jugador y lo manda a su último punto de reaparición. "
                + "Varita de Admin: clic derecho abre este panel. En un mundo tú solo, el vanish no se nota (no hay a quién ocultarse).",
                x, y + 158, w, C_MUTED);
    }

    // =====================================================================
    // PICKER (ventana modal)
    // =====================================================================
    private void openPicker(Kind kind, String title, Predicate<String> marked, Consumer<String> action, boolean close) {
        pickKind = kind;
        pickPlayers = false;
        pickTitle = title;
        pickMarked = marked;
        pickAction = action;
        pickClose = close;
        List<String> out = new ArrayList<>();
        switch (kind) {
            case ITEM -> { for (Identifier id : Registries.ITEM.getIds()) if (!id.getPath().equals("air")) out.add(id.toString()); }
            case BLOCK -> { for (Identifier id : Registries.BLOCK.getIds()) if (!id.getPath().equals("air")) out.add(id.toString()); }
            case ENTITY -> {
                for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
                    EntityType<?> t = Registries.ENTITY_TYPE.get(id);
                    if (t.isSummonable() && t.getSpawnGroup() != SpawnGroup.MISC) out.add(id.toString());
                }
            }
            case BIOME -> {
                if (client != null && client.world != null) {
                    for (Identifier id : client.world.getRegistryManager().get(RegistryKeys.BIOME).getIds()) out.add(id.toString());
                }
            }
        }
        Collections.sort(out);
        pickAll = out;
        fieldClear("picksearch");
        active = "picksearch";
        pickQuery = "\u0000";
    }

    private void openPlayerPicker(String title, Consumer<String> action) {
        openPlayerPicker(title, null, action, true);
    }

    private void openPlayerPicker(String title, Predicate<String> marked, Consumer<String> action, boolean close) {
        pickKind = null;
        pickPlayers = true;
        pickTitle = title;
        pickMarked = marked;
        pickAction = action;
        pickClose = close;
        List<String> out = knownPlayers();
        out.sort(Comparator.comparing(id -> nameOf(id).toLowerCase()));
        pickAll = out;
        fieldClear("picksearch");
        active = "picksearch";
        pickQuery = "\u0000";
    }

    private void closePicker() {
        pickAction = null;
        active = null;
    }

    private void drawPicker() {
        layer = 1;
        g.fill(0, 0, width, height, 0xA0000000);
        int w = Math.min(width - 24, 340), h = Math.min(height - 24, 260);
        int x = (width - w) / 2, y = (height - h) / 2;
        rr(x - 1, y - 1, w + 2, h + 2, C_ACC);
        rr(x, y, w, h, C_PANEL);
        txt(cut(pickTitle, w - 70), x + 8, y + 8, C_TEXT);
        button("Hecho", x + w - 50, y + 4, 44, 16, C_OK, this::closePicker);
        field("picksearch", pickPlayers ? "Buscar jugador..." : "Buscar (escribe mod: para ver un mod)", x + 8, y + 24, w - 16, 16, null);

        String q = fieldVal("picksearch").toLowerCase();
        if (!q.equals(pickQuery)) {
            pickQuery = q;
            List<String> f = new ArrayList<>();
            for (String id : pickAll) {
                String label = pickPlayers ? nameOf(id).toLowerCase() : id;
                if (q.isEmpty() || label.contains(q)) {
                    f.add(id);
                    if (f.size() >= 600) break;
                }
            }
            pickShown = f;
        }

        final Consumer<String> act = pickAction;
        final boolean close = pickClose;
        List<Row> rs = new ArrayList<>();
        for (String id : pickShown) {
            boolean marked = pickMarked != null && pickMarked.test(id);
            ItemStack icon = pickPlayers ? headIcon : iconFor(pickKind, id);
            String label = pickPlayers ? nameOf(id) : id;
            rs.add(new Row(icon, label, marked ? "✔ ya está en la lista" : null, List.of(), () -> {
                act.accept(id);
                touch();
                if (close) closePicker();
            }));
        }
        boolean wildcard = false;
        String ns = null;
        if (!pickPlayers && q.contains(":")) {
            ns = q.substring(0, q.indexOf(':'));
            for (String id : pickAll) if (id.startsWith(ns + ":")) { wildcard = true; break; }
        }
        int listH = h - 44 - (wildcard ? 48 : 28);
        rows("picker", x + 8, y + 44, w - 16, listH, rs);
        if (wildcard) {
            final String wns = ns;
            button("Elegir TODO el mod  " + wns + ":*", x + 8, y + h - 44, w - 16, 18, C_ACC2, () -> {
                act.accept(wns + ":*");
                touch();
                if (close) closePicker();
            });
        }
        txt(pickShown.size() + " resultados", x + 8, y + h - 16, C_MUTED);
        if (pickPlayers) {
            button("Todos (" + pickShown.size() + ")", x + w - 86, y + h - 22, 78, 16, C_ACC2, () -> {
                for (String id : new ArrayList<>(pickShown)) act.accept(id);
                touch();
                if (close) closePicker();
            });
        }
    }
}
