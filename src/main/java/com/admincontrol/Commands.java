package com.admincontrol;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.BiConsumer;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class Commands {
    private Commands() {}

    private static final int LEVEL = 4;
    private static final String[] CATS = {"use", "stand", "break", "place", "item"};

    private static final SuggestionProvider<ServerCommandSource> ITEMS =
            (ctx, b) -> CommandSource.suggestIdentifiers(Registries.ITEM.getIds(), b);
    private static final SuggestionProvider<ServerCommandSource> ENTITIES =
            (ctx, b) -> CommandSource.suggestIdentifiers(Registries.ENTITY_TYPE.getIds(), b);
    private static final SuggestionProvider<ServerCommandSource> BLOCKS =
            (ctx, b) -> CommandSource.suggestIdentifiers(Registries.BLOCK.getIds(), b);

    private static Config cfg() { return AdminControl.CFG; }

    private static int msg(CommandContext<ServerCommandSource> ctx, String text) {
        ctx.getSource().sendFeedback(() -> Text.literal(text), false);
        return 1;
    }

    private static int saved(CommandContext<ServerCommandSource> ctx, String text) {
        cfg().save();
        return msg(ctx, text);
    }

    private static String str(CommandContext<ServerCommandSource> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> root = literal("ac").requires(Perms::can);

        root.executes(ctx -> { Net.openGui(ctx.getSource().getPlayerOrThrow()); return 1; });
        root.then(literal("menu").executes(ctx -> { Net.openGui(ctx.getSource().getPlayerOrThrow()); return 1; }));
        root.then(literal("chest").executes(ctx -> { com.admincontrol.gui.Menus.main(ctx.getSource().getPlayerOrThrow()); return 1; }));
        root.then(literal("stick").executes(ctx -> {
            AdminTools.give(ctx.getSource().getPlayerOrThrow(), AdminTools.respawnStick());
            return msg(ctx, "§aBastón de Retorno entregado.");
        }));
        root.then(literal("wand").executes(ctx -> {
            AdminTools.give(ctx.getSource().getPlayerOrThrow(), AdminTools.menuWand());
            return msg(ctx, "§aVarita de menú entregada (clic derecho = abrir menú).");
        }));
        root.then(literal("event")
                .executes(ctx -> { com.admincontrol.gui.Menus.event(ctx.getSource().getPlayerOrThrow(), null); return 1; })
                .then(literal("run").executes(ctx -> msg(ctx, EventManager.start(ctx.getSource().getServer(), ctx.getSource().getPlayer()))))
                .then(literal("shuffle").executes(ctx -> {
                    var plan = EventManager.buildPlan(ctx.getSource().getServer());
                    return msg(ctx, "§aReparto generado: " + plan.size() + " grupos.");
                }))
                .then(literal("return").executes(ctx -> msg(ctx, "§aDevueltos: " + EventManager.returnAll(ctx.getSource().getServer()))))
                .then(literal("cancel").executes(ctx -> { EventManager.cancel(ctx.getSource().getServer()); return msg(ctx, "§eCancelado."); })));
        root.then(literal("timer").then(argument("args", StringArgumentType.greedyString()).executes(ctx -> {
            String a = str(ctx, "args").trim();
            if (a.equalsIgnoreCase("stop")) { TimerManager.stopAll(); return msg(ctx, "§eContadores detenidos."); }
            String[] parts = a.split("\\s+", 2);
            int secs = TimerManager.parse(parts[0]);
            if (secs <= 0) return msg(ctx, "§cTiempo inválido. Ejemplos: /ac timer 5:00 Evento  |  /ac timer 90s  |  /ac timer stop");
            TimerManager.create(ctx.getSource().getServer(), parts.length > 1 ? parts[1] : "", secs, true, false);
            return msg(ctx, "§aContador iniciado: " + TimerManager.fmt(secs));
        })));
        root.then(literal("help").executes(Commands::help));
        root.then(literal("reload").executes(ctx -> {
            AdminControl.CFG = Config.load();
            return msg(ctx, "§aConfiguración recargada desde config/admincontrol.json");
        }));
        root.then(items());
        root.then(spawn());
        root.then(damage());
        root.then(rules());
        root.then(literal("inv")
                .then(argument("target", EntityArgumentType.player())
                        .executes(ctx -> openInv(ctx, false))
                        .then(literal("ender").executes(ctx -> openInv(ctx, true)))));
        root.then(literal("vanish")
                .executes(ctx -> vanish(ctx, ctx.getSource().getPlayerOrThrow()))
                .then(argument("target", EntityArgumentType.player())
                        .executes(ctx -> vanish(ctx, EntityArgumentType.getPlayer(ctx, "target")))));

        dispatcher.register(literal("vanish").requires(Perms::can)
                .executes(ctx -> vanish(ctx, ctx.getSource().getPlayerOrThrow())));
        LiteralCommandNode<ServerCommandSource> node = dispatcher.register(root);
        dispatcher.register(literal("admincontrol").requires(Perms::can).redirect(node));
    }

    // ------------------------------------------------------------------ HELP
    private static int help(CommandContext<ServerCommandSource> ctx) {
        String[] lines = {
                "§6=== Admin Control ===",
                "§a/ac §7(sin nada más) abre el MENÚ con botones. También /ac stick, /ac wand, /ac event, /ac timer 5:00.",
                "§e/ac item block|unblock <patrón> §7- bloquear/desbloquear para todos",
                "§e/ac item allow|disallow <jugadores> <patrón> §7- excepción por jugador",
                "§e/ac item bypass <jugadores> <true|false> §7- ignora todos los bloqueos",
                "§e/ac item option <use|pickup|inventory|ops|enabled> <bool>  §e/ac item list",
                "§e/ac spawn block|unblock <patrón>  mult <x> <patrón>  add <mob> <prob> <biomas...>",
                "§e/ac spawn remove|force|dark|max|group|banbiome|unbanbiome|enable|list",
                "§e/ac damage global <x> | set <x> <patrón> | reset <patrón> | enable <bool> | list",
                "§e/ac rule nosaplingdrops|nosaplingplanting|extreme|affectcreative <bool>",
                "§e/ac rule damage <use|stand|break|place|item> <daño> <bloque/item/#tag>",
                "§e/ac rule undamage <categoría> <clave>  §e/ac rule list",
                "§e/ac inv <jugador> [ender]  §e/ac vanish [jugador]  §e/ac reload",
                "§7Patrones: minecraft:diamond | mimod:* | *:*_spawn_egg | #minecraft:swords"
        };
        for (String l : lines) msg(ctx, l);
        return 1;
    }

    // ------------------------------------------------------------------ ITEMS
    private static LiteralArgumentBuilder<ServerCommandSource> items() {
        LiteralArgumentBuilder<ServerCommandSource> item = literal("item");

        item.then(literal("block").then(argument("pattern", StringArgumentType.greedyString()).suggests(ITEMS)
                .executes(ctx -> {
                    String p = Matchers.norm(str(ctx, "pattern"));
                    cfg().blockedItems.add(p);
                    return saved(ctx, "§cBloqueado para todos: §f" + p);
                })));

        item.then(literal("unblock").then(argument("pattern", StringArgumentType.greedyString()).suggests(ITEMS)
                .executes(ctx -> {
                    String p = Matchers.norm(str(ctx, "pattern"));
                    boolean removed = cfg().blockedItems.remove(p);
                    return saved(ctx, removed ? "§aDesbloqueado para todos: §f" + p
                            : "§eEse patrón exacto no estaba bloqueado (usa /ac item list). Para un item concreto dentro de un patrón usa allow.");
                })));

        item.then(literal("allow").then(argument("players", EntityArgumentType.players())
                .then(argument("pattern", StringArgumentType.greedyString()).suggests(ITEMS).executes(ctx -> {
                    String p = Matchers.norm(str(ctx, "pattern"));
                    int n = 0;
                    for (ServerPlayerEntity sp : EntityArgumentType.getPlayers(ctx, "players")) {
                        cfg().playerExceptions.computeIfAbsent(sp.getUuidAsString(), k -> new LinkedHashSet<>()).add(p);
                        cfg().names.put(sp.getUuidAsString(), sp.getName().getString());
                        n++;
                    }
                    return saved(ctx, "§aPermitido §f" + p + " §apara " + n + " jugador(es).");
                }))));

        item.then(literal("disallow").then(argument("players", EntityArgumentType.players())
                .then(argument("pattern", StringArgumentType.greedyString()).suggests(ITEMS).executes(ctx -> {
                    String p = Matchers.norm(str(ctx, "pattern"));
                    for (ServerPlayerEntity sp : EntityArgumentType.getPlayers(ctx, "players")) {
                        Set<String> s = cfg().playerExceptions.get(sp.getUuidAsString());
                        if (s != null) s.remove(p);
                    }
                    return saved(ctx, "§eExcepción retirada: §f" + p);
                }))));

        item.then(literal("bypass").then(argument("players", EntityArgumentType.players())
                .then(argument("value", BoolArgumentType.bool()).executes(ctx -> {
                    boolean v = BoolArgumentType.getBool(ctx, "value");
                    for (ServerPlayerEntity sp : EntityArgumentType.getPlayers(ctx, "players")) {
                        String u = sp.getUuidAsString();
                        cfg().names.put(u, sp.getName().getString());
                        if (v) cfg().fullBypass.add(u); else cfg().fullBypass.remove(u);
                    }
                    return saved(ctx, "§aBypass total " + (v ? "concedido" : "retirado") + ".");
                }))));

        Map<String, BiConsumer<Config, Boolean>> opts = new LinkedHashMap<>();
        opts.put("use", (c, v) -> c.blockUse = v);
        opts.put("pickup", (c, v) -> c.blockPickup = v);
        opts.put("inventory", (c, v) -> c.blockInventory = v);
        opts.put("ops", (c, v) -> c.opsBypass = v);
        opts.put("enabled", (c, v) -> c.itemsEnabled = v);
        LiteralArgumentBuilder<ServerCommandSource> option = literal("option");
        for (Map.Entry<String, BiConsumer<Config, Boolean>> e : opts.entrySet()) {
            option.then(literal(e.getKey()).then(argument("value", BoolArgumentType.bool()).executes(ctx -> {
                boolean v = BoolArgumentType.getBool(ctx, "value");
                e.getValue().accept(cfg(), v);
                return saved(ctx, "§a" + e.getKey() + " = " + v);
            })));
        }
        item.then(option);

        item.then(literal("list").executes(ctx -> {
            Config c = cfg();
            msg(ctx, "§6Items §7(enabled=" + c.itemsEnabled + ", use=" + c.blockUse + ", pickup=" + c.blockPickup
                    + ", inventory=" + c.blockInventory + ", opsBypass=" + c.opsBypass + ")");
            msg(ctx, "§cBloqueados: §f" + (c.blockedItems.isEmpty() ? "(ninguno)" : String.join(", ", c.blockedItems)));
            for (Map.Entry<String, Set<String>> e : c.playerExceptions.entrySet()) {
                if (!e.getValue().isEmpty())
                    msg(ctx, "§aExcepciones de " + c.nameOf(e.getKey()) + ": §f" + String.join(", ", e.getValue()));
            }
            if (!c.fullBypass.isEmpty()) {
                List<String> n = new ArrayList<>();
                for (String u : c.fullBypass) n.add(c.nameOf(u));
                msg(ctx, "§aBypass total: §f" + String.join(", ", n));
            }
            return 1;
        }));
        return item;
    }

    // ------------------------------------------------------------------ SPAWN
    private static Config.SpawnRule ruleFor(String entity) {
        for (Config.SpawnRule r : cfg().extraSpawns) if (entity.equals(r.entity)) return r;
        return null;
    }

    private static LiteralArgumentBuilder<ServerCommandSource> spawn() {
        LiteralArgumentBuilder<ServerCommandSource> s = literal("spawn");

        s.then(literal("enable").then(argument("value", BoolArgumentType.bool()).executes(ctx -> {
            cfg().spawnEnabled = BoolArgumentType.getBool(ctx, "value");
            return saved(ctx, "§aspawn enabled = " + cfg().spawnEnabled);
        })));

        s.then(literal("block").then(argument("pattern", StringArgumentType.greedyString()).suggests(ENTITIES)
                .executes(ctx -> {
                    String p = Matchers.norm(str(ctx, "pattern"));
                    cfg().blockedSpawns.add(p);
                    return saved(ctx, "§cSpawn natural bloqueado: §f" + p);
                })));
        s.then(literal("unblock").then(argument("pattern", StringArgumentType.greedyString()).suggests(ENTITIES)
                .executes(ctx -> {
                    String p = Matchers.norm(str(ctx, "pattern"));
                    cfg().blockedSpawns.remove(p);
                    return saved(ctx, "§aSpawn natural desbloqueado: §f" + p);
                })));

        s.then(literal("mult").then(argument("value", DoubleArgumentType.doubleArg(0, 100))
                .then(argument("pattern", StringArgumentType.greedyString()).suggests(ENTITIES).executes(ctx -> {
                    double v = DoubleArgumentType.getDouble(ctx, "value");
                    String p = Matchers.norm(str(ctx, "pattern"));
                    if (v == 1.0) cfg().spawnMultipliers.remove(p); else cfg().spawnMultipliers.put(p, v);
                    String extra = v > 1.0 && (p.contains("*") || p.startsWith("#"))
                            ? " §e(para AUMENTAR usa un id exacto, no patrones)" : "";
                    return saved(ctx, "§aMultiplicador de spawn " + p + " = x" + v + extra);
                }))));

        // /ac spawn add <mob> <prob> <biomas...>
        s.then(literal("add").then(argument("entity", IdentifierArgumentType.identifier()).suggests(ENTITIES)
                .then(argument("chance", DoubleArgumentType.doubleArg(0, 1))
                        .then(argument("biomes", StringArgumentType.greedyString()).executes(ctx -> {
                            String ent = IdentifierArgumentType.getIdentifier(ctx, "entity").toString();
                            double chance = DoubleArgumentType.getDouble(ctx, "chance");
                            List<String> biomes = new ArrayList<>();
                            for (String b : str(ctx, "biomes").trim().split("\\s+")) biomes.add(Matchers.norm(b));
                            Config.SpawnRule r = ruleFor(ent);
                            if (r == null) { r = new Config.SpawnRule(); r.entity = ent; cfg().extraSpawns.add(r); }
                            r.chance = chance;
                            r.biomes = biomes;
                            r.enabled = true;
                            return saved(ctx, "§aRegla de spawn: §f" + ent + " §aen " + biomes + " prob=" + chance
                                    + " (por jugador cada " + cfg().spawnerIntervalTicks + " ticks)");
                        })))));

        s.then(literal("remove").then(argument("entity", IdentifierArgumentType.identifier()).suggests(ENTITIES)
                .executes(ctx -> {
                    String ent = IdentifierArgumentType.getIdentifier(ctx, "entity").toString();
                    cfg().extraSpawns.removeIf(r -> ent.equals(r.entity));
                    return saved(ctx, "§eRegla eliminada: §f" + ent);
                })));

        s.then(literal("force").then(argument("entity", IdentifierArgumentType.identifier()).suggests(ENTITIES)
                .then(argument("value", BoolArgumentType.bool()).executes(ctx -> ruleFlag(ctx, "force")))));
        s.then(literal("dark").then(argument("entity", IdentifierArgumentType.identifier()).suggests(ENTITIES)
                .then(argument("value", BoolArgumentType.bool()).executes(ctx -> ruleFlag(ctx, "dark")))));
        s.then(literal("max").then(argument("entity", IdentifierArgumentType.identifier()).suggests(ENTITIES)
                .then(argument("value", IntegerArgumentType.integer(1, 200)).executes(ctx -> {
                    Config.SpawnRule r = ruleFor(IdentifierArgumentType.getIdentifier(ctx, "entity").toString());
                    if (r == null) return msg(ctx, "§cPrimero crea la regla con /ac spawn add");
                    r.maxNearby = IntegerArgumentType.getInteger(ctx, "value");
                    return saved(ctx, "§amaxNearby = " + r.maxNearby);
                }))));
        s.then(literal("group").then(argument("entity", IdentifierArgumentType.identifier()).suggests(ENTITIES)
                .then(argument("min", IntegerArgumentType.integer(1, 50))
                        .then(argument("max", IntegerArgumentType.integer(1, 50)).executes(ctx -> {
                            Config.SpawnRule r = ruleFor(IdentifierArgumentType.getIdentifier(ctx, "entity").toString());
                            if (r == null) return msg(ctx, "§cPrimero crea la regla con /ac spawn add");
                            r.minGroup = IntegerArgumentType.getInteger(ctx, "min");
                            r.maxGroup = Math.max(r.minGroup, IntegerArgumentType.getInteger(ctx, "max"));
                            return saved(ctx, "§agrupo = " + r.minGroup + "-" + r.maxGroup);
                        })))));

        s.then(literal("banbiome").then(argument("entity", IdentifierArgumentType.identifier()).suggests(ENTITIES)
                .then(argument("biomes", StringArgumentType.greedyString()).executes(ctx -> {
                    String ent = IdentifierArgumentType.getIdentifier(ctx, "entity").toString();
                    Set<String> set = cfg().blockedSpawnBiomes.computeIfAbsent(ent, k -> new LinkedHashSet<>());
                    for (String b : str(ctx, "biomes").trim().split("\\s+")) set.add(Matchers.norm(b));
                    return saved(ctx, "§c" + ent + " ya no aparecerá en: §f" + set);
                }))));
        s.then(literal("unbanbiome").then(argument("entity", IdentifierArgumentType.identifier()).suggests(ENTITIES)
                .then(argument("biomes", StringArgumentType.greedyString()).executes(ctx -> {
                    String ent = IdentifierArgumentType.getIdentifier(ctx, "entity").toString();
                    Set<String> set = cfg().blockedSpawnBiomes.get(ent);
                    if (set != null) {
                        for (String b : str(ctx, "biomes").trim().split("\\s+")) set.remove(Matchers.norm(b));
                        if (set.isEmpty()) cfg().blockedSpawnBiomes.remove(ent);
                    }
                    return saved(ctx, "§aBiomas actualizados para " + ent);
                }))));

        s.then(literal("list").executes(ctx -> {
            Config c = cfg();
            msg(ctx, "§6Spawn §7(enabled=" + c.spawnEnabled + ", intervalo=" + c.spawnerIntervalTicks + " ticks)");
            msg(ctx, "§cBloqueados: §f" + (c.blockedSpawns.isEmpty() ? "(ninguno)" : String.join(", ", c.blockedSpawns)));
            msg(ctx, "§eMultiplicadores: §f" + c.spawnMultipliers);
            msg(ctx, "§eProhibido en biomas: §f" + c.blockedSpawnBiomes);
            for (Config.SpawnRule r : c.extraSpawns) {
                msg(ctx, "§aRegla: §f" + r.entity + " biomas=" + r.biomes + " prob=" + r.chance + " max=" + r.maxNearby
                        + " grupo=" + r.minGroup + "-" + r.maxGroup + " force=" + r.forcePlacement
                        + " dark=" + r.requireDark + " on=" + r.enabled);
            }
            return 1;
        }));
        return s;
    }

    private static int ruleFlag(CommandContext<ServerCommandSource> ctx, String flag) throws CommandSyntaxException {
        String ent = IdentifierArgumentType.getIdentifier(ctx, "entity").toString();
        boolean v = BoolArgumentType.getBool(ctx, "value");
        Config.SpawnRule r = ruleFor(ent);
        if (r == null) return msg(ctx, "§cPrimero crea la regla con /ac spawn add");
        if (flag.equals("force")) r.forcePlacement = v; else r.requireDark = v;
        return saved(ctx, "§a" + ent + " " + flag + " = " + v);
    }

    // ------------------------------------------------------------------ DAMAGE
    private static LiteralArgumentBuilder<ServerCommandSource> damage() {
        LiteralArgumentBuilder<ServerCommandSource> d = literal("damage");
        d.then(literal("enable").then(argument("value", BoolArgumentType.bool()).executes(ctx -> {
            cfg().damageEnabled = BoolArgumentType.getBool(ctx, "value");
            return saved(ctx, "§adaño de mobs enabled = " + cfg().damageEnabled);
        })));
        d.then(literal("global").then(argument("value", DoubleArgumentType.doubleArg(0, 1000000)).executes(ctx -> {
            cfg().globalMobDamageMultiplier = DoubleArgumentType.getDouble(ctx, "value");
            return saved(ctx, "§aMultiplicador global de daño de mobs = x" + cfg().globalMobDamageMultiplier);
        })));
        d.then(literal("set").then(argument("value", DoubleArgumentType.doubleArg(0, 1000000))
                .then(argument("pattern", StringArgumentType.greedyString()).suggests(ENTITIES).executes(ctx -> {
                    String p = Matchers.norm(str(ctx, "pattern"));
                    double v = DoubleArgumentType.getDouble(ctx, "value");
                    cfg().damageMultipliers.put(p, v);
                    return saved(ctx, "§aDaño de §f" + p + " §a= x" + v);
                }))));
        d.then(literal("reset").then(argument("pattern", StringArgumentType.greedyString()).suggests(ENTITIES)
                .executes(ctx -> {
                    String p = Matchers.norm(str(ctx, "pattern"));
                    cfg().damageMultipliers.remove(p);
                    return saved(ctx, "§eDaño restablecido para " + p);
                })));
        d.then(literal("list").executes(ctx -> {
            msg(ctx, "§6Daño §7(enabled=" + cfg().damageEnabled + ", global=x" + cfg().globalMobDamageMultiplier + ")");
            msg(ctx, "§eEspecíficos: §f" + cfg().damageMultipliers);
            return 1;
        }));
        return d;
    }

    // ------------------------------------------------------------------ RULES
    private static Map<String, Double> cat(Config c, String k) {
        return switch (k) {
            case "use" -> c.useDamage;
            case "stand" -> c.standDamage;
            case "break" -> c.breakDamage;
            case "place" -> c.placeDamage;
            default -> c.itemDamage;
        };
    }

    private static LiteralArgumentBuilder<ServerCommandSource> rules() {
        LiteralArgumentBuilder<ServerCommandSource> r = literal("rule");

        Map<String, BiConsumer<Config, Boolean>> toggles = new LinkedHashMap<>();
        toggles.put("nosaplingdrops", (c, v) -> c.noSaplingDrops = v);
        toggles.put("nosaplingplanting", (c, v) -> c.noSaplingPlanting = v);
        toggles.put("extreme", (c, v) -> c.extremeDamageEnabled = v);
        toggles.put("affectcreative", (c, v) -> c.extremeAffectCreative = v);
        for (Map.Entry<String, BiConsumer<Config, Boolean>> e : toggles.entrySet()) {
            r.then(literal(e.getKey()).then(argument("value", BoolArgumentType.bool()).executes(ctx -> {
                boolean v = BoolArgumentType.getBool(ctx, "value");
                e.getValue().accept(cfg(), v);
                return saved(ctx, "§a" + e.getKey() + " = " + v);
            })));
        }

        LiteralArgumentBuilder<ServerCommandSource> dmg = literal("damage");
        LiteralArgumentBuilder<ServerCommandSource> undmg = literal("undamage");
        for (String k : CATS) {
            dmg.then(literal(k).then(argument("amount", DoubleArgumentType.doubleArg(0, 1.0E9))
                    .then(argument("key", StringArgumentType.greedyString()).suggests(k.equals("item") ? ITEMS : BLOCKS)
                            .executes(ctx -> {
                                String key = Matchers.norm(str(ctx, "key"));
                                double a = DoubleArgumentType.getDouble(ctx, "amount");
                                cat(cfg(), k).put(key, a);
                                return saved(ctx, "§aRegla [" + k + "] " + key + " → " + a + " de daño"
                                        + (cfg().extremeDamageEnabled ? "" : " §e(recuerda: /ac rule extreme true)"));
                            }))));
            undmg.then(literal(k).then(argument("key", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String key = Matchers.norm(str(ctx, "key"));
                        cat(cfg(), k).remove(key);
                        return saved(ctx, "§eRegla [" + k + "] eliminada: " + key);
                    })));
        }
        r.then(dmg);
        r.then(undmg);

        r.then(literal("list").executes(ctx -> {
            Config c = cfg();
            msg(ctx, "§6Reglas extremas");
            msg(ctx, "§enosaplingdrops=" + c.noSaplingDrops + " nosaplingplanting=" + c.noSaplingPlanting
                    + " extreme=" + c.extremeDamageEnabled + " affectcreative=" + c.extremeAffectCreative);
            for (String k : CATS) msg(ctx, "§e[" + k + "] §f" + cat(c, k));
            return 1;
        }));
        return r;
    }

    // ------------------------------------------------------------------ INV / VANISH
    private static int openInv(CommandContext<ServerCommandSource> ctx, boolean ender) throws CommandSyntaxException {
        ServerPlayerEntity admin = ctx.getSource().getPlayerOrThrow();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        InventoryViewer.open(admin, target, ender);
        return 1;
    }

    private static int vanish(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
        boolean on = VanishManager.toggle(target);
        return msg(ctx, "§aNameHide/Vanish " + (on ? "ACTIVADO" : "desactivado") + " para " + target.getName().getString());
    }
}
