package com.admincontrol;

import com.admincontrol.gui.Menus;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Comunicación con la pantalla del cliente (si el jugador tiene el mod instalado). */
public final class Net {
    private Net() {}

    private static final Gson GSON = new Gson();
    private static final Set<UUID> CLIENTS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String[]> CHUNKS = new ConcurrentHashMap<>();

    // ---------------- Paquetes ----------------
    public record Hello(String version) implements CustomPayload {
        public static final CustomPayload.Id<Hello> ID = new CustomPayload.Id<>(Identifier.of(AdminControl.MOD_ID, "hello"));
        public static final PacketCodec<RegistryByteBuf, Hello> CODEC =
                PacketCodec.tuple(PacketCodecs.string(64), Hello::version, Hello::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Servidor -> cliente: estado completo. open=true abre la pantalla. */
    public record State(boolean open, String json) implements CustomPayload {
        public static final CustomPayload.Id<State> ID = new CustomPayload.Id<>(Identifier.of(AdminControl.MOD_ID, "state"));
        public static final PacketCodec<RegistryByteBuf, State> CODEC =
                PacketCodec.tuple(PacketCodecs.BOOL, State::open, PacketCodecs.string(900_000), State::json, State::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Servidor -> cliente: transición de pantalla (cortinas negras). mode = out | in | clear */
    public record Transition(String mode, int ticks, String title, String sub) implements CustomPayload {
        public static final CustomPayload.Id<Transition> ID = new CustomPayload.Id<>(Identifier.of(AdminControl.MOD_ID, "transition"));
        public static final PacketCodec<RegistryByteBuf, Transition> CODEC =
                PacketCodec.tuple(PacketCodecs.string(16), Transition::mode, PacketCodecs.VAR_INT, Transition::ticks,
                        PacketCodecs.string(200), Transition::title, PacketCodecs.string(200), Transition::sub, Transition::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Cliente -> servidor: configuración editada, troceada. */
    public record ConfigChunk(int index, int total, String part) implements CustomPayload {
        public static final CustomPayload.Id<ConfigChunk> ID = new CustomPayload.Id<>(Identifier.of(AdminControl.MOD_ID, "config"));
        public static final PacketCodec<RegistryByteBuf, ConfigChunk> CODEC =
                PacketCodec.tuple(PacketCodecs.VAR_INT, ConfigChunk::index, PacketCodecs.VAR_INT, ConfigChunk::total,
                        PacketCodecs.string(9000), ConfigChunk::part, ConfigChunk::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    /** Cliente -> servidor: acciones (dar item, vanish, evento...). */
    public record Cmd(String cmd, String arg) implements CustomPayload {
        public static final CustomPayload.Id<Cmd> ID = new CustomPayload.Id<>(Identifier.of(AdminControl.MOD_ID, "cmd"));
        public static final PacketCodec<RegistryByteBuf, Cmd> CODEC =
                PacketCodec.tuple(PacketCodecs.string(64), Cmd::cmd, PacketCodecs.string(2000), Cmd::arg, Cmd::new);
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(Hello.ID, Hello.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigChunk.ID, ConfigChunk.CODEC);
        PayloadTypeRegistry.playC2S().register(Cmd.ID, Cmd.CODEC);
        PayloadTypeRegistry.playS2C().register(State.ID, State.CODEC);
        PayloadTypeRegistry.playS2C().register(Transition.ID, Transition.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Hello.ID, (payload, ctx) -> CLIENTS.add(ctx.player().getUuid()));
        ServerPlayNetworking.registerGlobalReceiver(ConfigChunk.ID, (payload, ctx) -> onChunk(ctx.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(Cmd.ID, (payload, ctx) -> {
            try {
                handleCmd(ctx.player(), payload.cmd(), payload.arg());
            } catch (Exception e) {
                AdminControl.LOGGER.error("Error en comando de cliente {}", payload.cmd(), e);
            }
        });
    }

    public static void forget(UUID id) {
        CLIENTS.remove(id);
        CHUNKS.remove(id);
    }

    public static boolean hasClient(ServerPlayerEntity p) {
        return CLIENTS.contains(p.getUuid());
    }

    /** Abre la pantalla bonita si el jugador tiene el mod; si no, el menú de cofre. */
    public static void openGui(ServerPlayerEntity p) {
        if (!Perms.isAdmin(p)) return; // el panel es solo para OP
        if (hasClient(p)) sendState(p, true); else Menus.main(p);
    }

    public static void sendState(ServerPlayerEntity p, boolean open) {
        sendState(p, open, true);
    }

    public static void sendState(ServerPlayerEntity p, boolean open, boolean withConfig) {
        if (!Perms.isAdmin(p)) return; // nunca se envía información de administración a jugadores normales
        ServerPlayNetworking.send(p, new State(open, buildState(p, withConfig)));
    }

    /** Envía la transición de pantalla si el jugador tiene el mod. @return true si la envió. */
    public static boolean sendTransition(ServerPlayerEntity p, String mode, int ticks, String title, String sub) {
        if (!hasClient(p)) return false;
        ServerPlayNetworking.send(p, new Transition(mode, ticks, title, sub));
        return true;
    }

    // ---------------- Estado ----------------
    public static String buildState(ServerPlayerEntity viewer) {
        return buildState(viewer, true);
    }

    public static String buildState(ServerPlayerEntity viewer, boolean withConfig) {
        MinecraftServer server = viewer.getServer();
        JsonObject o = new JsonObject();
        if (withConfig) o.add("config", GSON.toJsonTree(AdminControl.CFG));
        o.addProperty("self", viewer.getUuidAsString());

        JsonArray pl = new JsonArray();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            JsonObject j = new JsonObject();
            j.addProperty("uuid", p.getUuidAsString());
            j.addProperty("name", p.getName().getString());
            j.addProperty("admin", Perms.isAdmin(p));
            j.addProperty("vanished", VanishManager.isVanished(p));
            pl.add(j);
        }
        o.add("players", pl);

        JsonArray plan = new JsonArray();
        for (Map.Entry<UUID, List<UUID>> e : EventManager.plan().entrySet()) {
            ServerPlayerEntity host = server.getPlayerManager().getPlayer(e.getKey());
            if (host == null) continue;
            JsonObject g = new JsonObject();
            g.addProperty("host", host.getName().getString());
            JsonArray members = new JsonArray();
            for (UUID id : e.getValue()) {
                ServerPlayerEntity m = server.getPlayerManager().getPlayer(id);
                if (m != null) members.add(m.getName().getString());
            }
            g.add("members", members);
            plan.add(g);
        }
        o.add("plan", plan);
        o.addProperty("running", EventManager.running());
        o.addProperty("returnable", EventManager.returnableCount());

        JsonArray grs = new JsonArray();
        GameRules gr = server.getGameRules();
        gr.accept(new GameRules.Visitor() {
            @Override
            public void visitBoolean(GameRules.Key<GameRules.BooleanRule> key, GameRules.Type<GameRules.BooleanRule> type) {
                JsonObject j = new JsonObject();
                j.addProperty("name", key.getName());
                j.addProperty("value", gr.getBoolean(key));
                grs.add(j);
            }
        });
        o.add("gamerules", grs);

        JsonArray tms = new JsonArray();
        for (TimerManager.T t : TimerManager.all()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", t.id);
            j.addProperty("name", t.name);
            j.addProperty("left", t.secondsLeft());
            j.addProperty("total", t.totalSeconds());
            j.addProperty("paused", t.paused);
            j.addProperty("everyone", t.everyone);
            j.addProperty("auto", t.autoTp);
            tms.add(j);
        }
        o.add("timers", tms);
        return GSON.toJson(o);
    }

    // ---------------- Config recibida ----------------
    private static void onChunk(ServerPlayerEntity p, ConfigChunk c) {
        if (!Perms.isAdmin(p)) return;
        if (c.total() <= 0 || c.total() > 400 || c.index() < 0 || c.index() >= c.total()) return;
        UUID id = p.getUuid();
        if (c.index() == 0) CHUNKS.put(id, new String[c.total()]);
        String[] arr = CHUNKS.get(id);
        if (arr == null || arr.length != c.total()) return;
        arr[c.index()] = c.part();
        for (String s : arr) if (s == null) return;
        CHUNKS.remove(id);
        try {
            Config nc = GSON.fromJson(String.join("", arr), Config.class);
            if (nc == null) return;
            nc.normalize();
            Config old = AdminControl.CFG;
            nc.vanished = old.vanished;   // estos los gestiona solo el servidor
            nc.names = old.names;
            AdminControl.CFG = nc;
            nc.save();
        } catch (Exception e) {
            AdminControl.LOGGER.error("Configuración recibida inválida", e);
        }
    }

    // ---------------- Acciones ----------------
    private static void handleCmd(ServerPlayerEntity p, String cmd, String arg) {
        if (!Perms.isAdmin(p)) return;
        MinecraftServer s = p.getServer();
        switch (cmd) {
            case "open" -> sendState(p, true);
            case "refresh" -> sendState(p, false);
            case "refreshlite" -> sendState(p, false, false);
            case "reload" -> {
                AdminControl.CFG = Config.load();
                sendState(p, false);
            }
            case "give" -> {
                String[] a = arg.split("\\|");
                Identifier id = Identifier.tryParse(a[0]);
                int n = a.length > 1 ? Integer.parseInt(a[1]) : 1;
                if (id != null) {
                    Item it = Registries.ITEM.get(id);
                    if (it != Items.AIR) p.getInventory().offerOrDrop(new ItemStack(it, Math.max(1, Math.min(n, it.getMaxCount()))));
                }
            }
            case "tool" -> {
                if (arg.equals("stick")) AdminTools.give(p, AdminTools.respawnStick());
                else AdminTools.give(p, AdminTools.menuWand());
            }
            case "vanish" -> {
                ServerPlayerEntity t = s.getPlayerManager().getPlayer(UUID.fromString(arg));
                if (t != null) VanishManager.toggle(t);
                sendState(p, false);
            }
            case "inv" -> {
                boolean ender = arg.startsWith("ender|");
                String uuid = ender ? arg.substring(6) : arg;
                ServerPlayerEntity t = s.getPlayerManager().getPlayer(UUID.fromString(uuid));
                if (t != null) InventoryViewer.open(p, t, ender);
            }
            case "event" -> {
                switch (arg) {
                    case "shuffle" -> EventManager.buildPlan(s);
                    case "run" -> p.sendMessage(Text.literal(EventManager.start(s, p)), false);
                    case "return" -> p.sendMessage(Text.literal("§aDevueltos: " + EventManager.returnAll(s)), false);
                    case "cancel" -> EventManager.cancel(s);
                    default -> { }
                }
                sendState(p, false);
            }
            case "timer" -> {
                String[] a = arg.split("\\|", -1);
                switch (a[0]) {
                    case "create" -> {
                        int secs = Integer.parseInt(a[2]);
                        if (secs >= 1 && secs <= 86400) TimerManager.create(s, a[1], secs, a[3].equals("1"), a[4].equals("1"));
                    }
                    case "pause" -> TimerManager.setPaused(Integer.parseInt(a[1]), true);
                    case "resume" -> TimerManager.setPaused(Integer.parseInt(a[1]), false);
                    case "add" -> TimerManager.addSeconds(Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                    case "stop" -> TimerManager.stop(Integer.parseInt(a[1]));
                    default -> { }
                }
                sendState(p, false);
            }
            case "gamerule" -> {
                String[] a = arg.split("\\|");
                boolean value = Boolean.parseBoolean(a[1]);
                GameRules gr = s.getGameRules();
                gr.accept(new GameRules.Visitor() {
                    @Override
                    public void visitBoolean(GameRules.Key<GameRules.BooleanRule> key, GameRules.Type<GameRules.BooleanRule> type) {
                        if (key.getName().equals(a[0])) gr.get(key).set(value, s);
                    }
                });
                sendState(p, false);
            }
            default -> { }
        }
    }
}
