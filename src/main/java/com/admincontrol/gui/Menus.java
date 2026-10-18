package com.admincontrol.gui;

import com.admincontrol.AdminControl;
import com.admincontrol.AdminTools;
import com.admincontrol.Config;
import com.admincontrol.InventoryViewer;
import com.admincontrol.EventManager;
import com.admincontrol.Matchers;
import com.admincontrol.Perms;
import com.admincontrol.RuleDefs;
import com.admincontrol.VanishManager;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Todos los menús del mod. Punto de entrada: Menus.main(jugador). */
public final class Menus {
    private Menus() {}

    private static Config cfg() { return AdminControl.CFG; }
    private static void save() { cfg().save(); }
    private static void tell(ServerPlayerEntity p, String s) { p.sendMessage(Text.literal(s), false); }

    private static String fmt(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
        return String.format(Locale.ROOT, "%.2f", d);
    }

    private static Consumer<Click> step(DoubleSupplier get, DoubleConsumer set, double step, double min, double max, Runnable after) {
        return c -> {
            if (!c.adjustable()) return;
            double d = get.getAsDouble() + c.sign() * step * (c.shift() ? 10 : 1);
            d = Math.max(min, Math.min(max, d));
            d = Math.round(d * 100.0) / 100.0;
            set.accept(d);
            save();
            after.run();
        };
    }

    private static Consumer<Click> istep(IntSupplier get, IntConsumer set, int step, int min, int max, Runnable after) {
        return c -> {
            if (!c.adjustable()) return;
            int d = get.getAsInt() + c.sign() * step * (c.shift() ? 5 : 1);
            set.accept(Math.max(min, Math.min(max, d)));
            save();
            after.run();
        };
    }

    // =====================================================================
    // MENÚ PRINCIPAL
    // =====================================================================
    public static void main(ServerPlayerEntity sp) {
        ButtonMenu m = new ButtonMenu("✦ Admin Control", 5);
        m.theme(Items.PURPLE_STAINED_GLASS_PANE);
        m.button(4, () -> Icons.of(Items.NETHER_STAR, "§6§lAdmin Control", "§7Panel de control del servidor", "§8Elige una sección"), null);
        m.button(10, () -> Icons.of(Items.BARRIER, "§c§lItems y bloqueos", "§7Catálogo por categorías, bloquear items,", "§7recetas y bloques protegidos", "", "§eClic para abrir"),
                c -> items(c.player, () -> main(c.player)));
        m.button(11, () -> Icons.of(Items.ZOMBIE_HEAD, "§a§lSpawn de mobs", "§7Aumentar, reducir, bloquear", "§7y cambiar los biomas de aparición", "", "§eClic para abrir"),
                c -> spawnRoot(c.player, () -> main(c.player)));
        m.button(12, () -> Icons.of(Items.IRON_SWORD, "§e§lDaño de mobs", "§7Sube o baja el daño de cualquier mob", "", "§eClic para abrir"),
                c -> damageRoot(c.player, () -> main(c.player)));
        m.button(13, () -> Icons.of(Items.TNT, "§4§lReglas extremas", "§7Puertas que matan, sin leche,", "§7sin golems, gamerules...", "", "§eClic para abrir"),
                c -> rulesRoot(c.player, () -> main(c.player)));
        m.button(14, () -> Icons.of(Items.PLAYER_HEAD, "§b§lJugadores", "§7Ver/editar inventarios, ender chest,", "§7NameHide y permisos de items", "", "§eClic para abrir"),
                c -> playersManage(c.player, () -> main(c.player)));
        m.button(15, () -> {
            ItemStack s2 = Icons.of(Items.ENDER_PEARL, "§6§lEvento: repartir jugadores", "§7Divide a todos de forma aleatoria", "§7entre los anfitriones y los teletransporta", "", "§eClic para abrir");
            Icons.glint(s2);
            return s2;
        }, c -> event(c.player, () -> main(c.player)));
        m.button(16, () -> Icons.of(Items.STICK, "§d§lHerramientas", "§7Bastón de Retorno y Varita de menú", "", "§eClic para abrir"),
                c -> tools(c.player, () -> main(c.player)));
        m.button(29, () -> {
            boolean on = VanishManager.isVanished(sp);
            ItemStack s2 = Icons.of(on ? Items.ENDER_EYE : Items.ENDER_PEARL,
                    (on ? "§a§lNameHide: ACTIVADO" : "§7§lNameHide: desactivado"),
                    "§7Nadie (salvo admins) te ve,", "§7ni tu nombre ni en la lista", "", "§eClic para cambiar");
            if (on) Icons.glint(s2);
            return s2;
        }, c -> vanishMenu(c.player, () -> main(c.player)));
        m.button(31, () -> Icons.of(Items.REDSTONE, "§7Recargar config", "§7Vuelve a leer admincontrol.json"), c -> {
            AdminControl.CFG = Config.load();
            tell(c.player, "§aConfiguración recargada.");
        });
        m.button(33, () -> Icons.of(Items.RED_DYE, "§cCerrar"), c -> c.player.closeHandledScreen());
        m.open(sp);
    }

    private static void tools(ServerPlayerEntity sp, Runnable back) {
        ButtonMenu m = new ButtonMenu("Herramientas", 3);
        m.button(11, () -> Icons.of(Items.STICK, "§dBastón de Retorno", "§7Golpea a un jugador y lo envía a su", "§7último punto de reaparición", "§7(con sonido)", "", "§eClic: recibir"),
                c -> { AdminTools.give(c.player, AdminTools.respawnStick()); tell(c.player, "§aBastón entregado."); });
        m.button(15, () -> Icons.of(Items.BLAZE_ROD, "§6Varita de Admin", "§7Clic derecho con ella abre este menú", "", "§eClic: recibir"),
                c -> { AdminTools.give(c.player, AdminTools.menuWand()); tell(c.player, "§aVarita entregada."); });
        m.back(back);
        m.open(sp);
    }

    // =====================================================================
    // ITEMS
    // =====================================================================
    private static void patternsList(ServerPlayerEntity sp, Runnable back) {
        ListMenu m = new ListMenu("Patrones bloqueados", back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (String pat : new ArrayList<>(cfg().blockedItems)) {
                ItemStack ic = Icons.forPattern(pat, Kind.ITEM);
                Icons.name(ic, "§c" + pat);
                Icons.lore(ic, "§7Bloqueado para todos", "", "§eClic izq: §fdesbloquear para todos", "§eClic der: §fpermitir a un jugador");
                l.add(new ListMenu.Entry(ic, c -> {
                    if (c.right()) {
                        players("¿A qué jugador le permites " + pat + "?", c.player, () -> m.open(c.player), t -> {
                            cfg().playerExceptions.computeIfAbsent(t.getUuidAsString(), k -> new LinkedHashSet<>()).add(pat);
                            cfg().names.put(t.getUuidAsString(), t.getName().getString());
                            save();
                            tell(c.player, "§aPermitido " + pat + " para " + t.getName().getString());
                            m.open(c.player);
                        });
                    } else {
                        cfg().blockedItems.remove(pat);
                        save();
                        m.refresh();
                    }
                }));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.LIME_DYE, "§aBloquear items...", "§7Elige de una lista (todos los mods)", "§7o toca un item de tu inventario"),
                c -> new Browser("Elige items a bloquear", Kind.ITEM, pat -> { cfg().blockedItems.add(pat); save(); },
                        () -> m.open(c.player), pat -> cfg().blockedItems.contains(pat)).open(c.player));
        m.control(() -> Icons.of(Items.WRITABLE_BOOK, "§aBloquear por patrón", "§7Ej: mimod:*  |  *:*_spawn_egg", "§7#minecraft:swords"),
                c -> ChatPrompt.ask(c.player, "Escribe el patrón a bloquear", t -> {
                    cfg().blockedItems.add(Matchers.norm(t)); save(); m.open(c.player);
                }, () -> m.open(c.player)));
        m.control(() -> Icons.of(Items.COMPARATOR, "§eOpciones", "§7Qué se bloquea y quién ignora el bloqueo"),
                c -> itemOptions(c.player, () -> m.open(c.player)));
        m.control(() -> Icons.of(Items.PLAYER_HEAD, "§bExcepciones por jugador", "§7Permitir items a jugadores concretos"),
                c -> playersManage(c.player, () -> m.open(c.player)));
        m.open(sp);
    }

    private static void itemOptions(ServerPlayerEntity sp, Runnable back) {
        ButtonMenu m = new ButtonMenu("Opciones de bloqueo", 3);
        m.toggle(10, "Sistema de bloqueo", "Interruptor general", () -> cfg().itemsEnabled, v -> cfg().itemsEnabled = v);
        m.toggle(11, "Bloquear USO", "Usar, colocar y atacar con el item", () -> cfg().blockUse, v -> cfg().blockUse = v);
        m.toggle(12, "Bloquear RECOGER", "Recogerlo del suelo", () -> cfg().blockPickup, v -> cfg().blockPickup = v);
        m.toggle(13, "Bloquear INVENTARIO", "Tenerlo: creativo, crafteo, cofres...", () -> cfg().blockInventory, v -> cfg().blockInventory = v);
        m.toggle(14, "Admins ignoran bloqueos", "Si está activo TÚ puedes usar todo (apágalo para probar)", () -> cfg().opsBypass, v -> cfg().opsBypass = v);
        m.back(back);
        m.open(sp);
    }

    // =====================================================================
    // JUGADORES
    // =====================================================================
    public static void playersManage(ServerPlayerEntity sp, Runnable back) {
        players("Jugadores en línea", sp, back, t -> playerMenu(sp, t, () -> playersManage(sp, back)));
    }

    private static void players(String title, ServerPlayerEntity sp, Runnable back, Consumer<ServerPlayerEntity> onSelect) {
        ListMenu m = new ListMenu(title, back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (ServerPlayerEntity p : sp.getServer().getPlayerManager().getPlayerList()) {
                ItemStack ic = Icons.head(p, "§f" + p.getName().getString(), "§eClic para elegir");
                l.add(new ListMenu.Entry(ic, c -> onSelect.accept(p)));
            }
            return l;
        });
        m.open(sp);
    }

    private static void playerMenu(ServerPlayerEntity admin, ServerPlayerEntity t, Runnable back) {
        String name = t.getName().getString();
        String uuid = t.getUuidAsString();
        ButtonMenu m = new ButtonMenu("Jugador: " + name, 3);
        m.button(10, () -> Icons.of(Items.CHEST, "§bInventario de " + name, "§7Ver y editar en vivo", "§7(agregar / quitar items)"),
                c -> InventoryViewer.open(admin, t, false));
        m.button(11, () -> Icons.of(Items.ENDER_CHEST, "§5Ender Chest de " + name, "§7Ver y editar en vivo"),
                c -> InventoryViewer.open(admin, t, true));
        m.button(12, () -> {
            boolean on = VanishManager.isVanished(t);
            return Icons.of(on ? Items.ENDER_EYE : Items.ENDER_PEARL, on ? "§aNameHide: ACTIVADO" : "§7NameHide: desactivado", "§eClic para cambiar");
        }, c -> { VanishManager.toggle(t); m.refresh(); });
        m.toggle(13, "Ignora bloqueos de items", "Puede usar TODOS los items bloqueados",
                () -> cfg().fullBypass.contains(uuid), v -> {
                    cfg().names.put(uuid, name);
                    if (v) cfg().fullBypass.add(uuid); else cfg().fullBypass.remove(uuid);
                });
        m.button(14, () -> Icons.of(Items.BOOK, "§aExcepciones de items",
                        "§7Items permitidos solo para " + name, "§7Actuales: " + cfg().playerExceptions.getOrDefault(uuid, Set.of()).size()),
                c -> exceptions(admin, t, () -> playerMenu(admin, t, back)));
        m.back(back);
        m.open(admin);
    }

    private static void exceptions(ServerPlayerEntity admin, ServerPlayerEntity t, Runnable back) {
        String uuid = t.getUuidAsString();
        ListMenu m = new ListMenu("Excepciones de " + t.getName().getString(), back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (String pat : new ArrayList<>(cfg().playerExceptions.getOrDefault(uuid, new LinkedHashSet<>()))) {
                ItemStack ic = Icons.forPattern(pat, Kind.ITEM);
                Icons.name(ic, "§a" + pat);
                Icons.lore(ic, "§7Permitido para este jugador", "", "§eClic: quitar excepción");
                l.add(new ListMenu.Entry(ic, c -> {
                    Set<String> s = cfg().playerExceptions.get(uuid);
                    if (s != null) s.remove(pat);
                    save();
                    m.refresh();
                }));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.LIME_DYE, "§aPermitir un item..."), c ->
                new Browser("Permitir items a " + t.getName().getString(), Kind.ITEM, pat -> {
                    cfg().playerExceptions.computeIfAbsent(uuid, k -> new LinkedHashSet<>()).add(pat);
                    save();
                }, () -> m.open(c.player), pat -> cfg().playerExceptions.getOrDefault(uuid, Set.of()).contains(pat)).open(c.player));
        m.control(() -> Icons.of(Items.WRITABLE_BOOK, "§aPermitir por patrón"), c ->
                ChatPrompt.ask(c.player, "Escribe el patrón a permitir", text -> {
                    cfg().playerExceptions.computeIfAbsent(uuid, k -> new LinkedHashSet<>()).add(Matchers.norm(text));
                    save();
                    m.open(c.player);
                }, () -> m.open(c.player)));
        m.open(admin);
    }

    // =====================================================================
    // SPAWN
    // =====================================================================
    private static void spawnRoot(ServerPlayerEntity sp, Runnable back) {
        ButtonMenu m = new ButtonMenu("Spawn de mobs", 3);
        m.toggle(9, "Sistema de spawn", "Interruptor general", () -> cfg().spawnEnabled, v -> cfg().spawnEnabled = v);
        m.button(11, () -> Icons.of(Items.BARRIER, "§cMobs bloqueados", "§7Mobs que ya no aparecen solos"),
                c -> mobsBlocked(c.player, () -> spawnRoot(c.player, back)));
        m.button(12, () -> Icons.of(Items.BONE_MEAL, "§eMás / menos spawn", "§7Multiplicador por mob (x0.5, x3...)"),
                c -> mults(c.player, () -> spawnRoot(c.player, back)));
        m.button(13, () -> Icons.of(Items.FILLED_MAP, "§aAparecer en otros biomas", "§7Ej: ahogados en sabana o taiga"),
                c -> spawnRules(c.player, () -> spawnRoot(c.player, back)));
        m.button(14, () -> Icons.of(Items.RED_STAINED_GLASS_PANE, "§cProhibir en biomas", "§7Ej: zombies no aparecen en desierto"),
                c -> banList(c.player, () -> spawnRoot(c.player, back)));
        m.button(16, () -> Icons.of(Items.CLOCK, "§eIntervalo del spawner: §f" + cfg().spawnerIntervalTicks + " ticks",
                        "§7Cada cuánto intenta crear mobs extra", "§7Clic izq +10 / der -10"),
                istep(() -> cfg().spawnerIntervalTicks, v -> cfg().spawnerIntervalTicks = v, 10, 10, 1200, m::refresh));
        m.back(back);
        m.open(sp);
    }

    private static void mobsBlocked(ServerPlayerEntity sp, Runnable back) {
        ListMenu m = new ListMenu("Mobs con spawn bloqueado", back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (String pat : new ArrayList<>(cfg().blockedSpawns)) {
                ItemStack ic = Icons.forPattern(pat, Kind.ENTITY);
                Icons.name(ic, "§c" + pat);
                Icons.lore(ic, "§7No aparece de forma natural", "", "§eClic: permitir de nuevo");
                l.add(new ListMenu.Entry(ic, c -> { cfg().blockedSpawns.remove(pat); save(); m.refresh(); }));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.LIME_DYE, "§aBloquear un mob..."), c ->
                new Browser("Elige mobs a bloquear", Kind.ENTITY, pat -> { cfg().blockedSpawns.add(pat); save(); },
                        () -> m.open(c.player), pat -> cfg().blockedSpawns.contains(pat)).open(c.player));
        m.control(() -> Icons.of(Items.WRITABLE_BOOK, "§aBloquear por patrón", "§7Ej: mimod:*"), c ->
                ChatPrompt.ask(c.player, "Escribe el patrón de mobs", t -> {
                    cfg().blockedSpawns.add(Matchers.norm(t)); save(); m.open(c.player);
                }, () -> m.open(c.player)));
        m.open(sp);
    }

    private static void mults(ServerPlayerEntity sp, Runnable back) {
        ListMenu m = new ListMenu("Multiplicador de spawn", back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (String pat : new ArrayList<>(cfg().spawnMultipliers.keySet())) {
                double val = cfg().spawnMultipliers.get(pat);
                ItemStack ic = Icons.forPattern(pat, Kind.ENTITY);
                Icons.name(ic, "§e" + pat + " §fx" + fmt(val));
                Icons.lore(ic, val < 1 ? "§7Aparece MENOS" : val > 1 ? "§7Aparece MÁS" : "§7Normal", "",
                        "§eClic izq: +0.25  §eClic der: -0.25", "§eShift: ±2.5", "§eQ (tirar): eliminar");
                Consumer<Click> stepper = step(() -> cfg().spawnMultipliers.getOrDefault(pat, 1.0),
                        v -> cfg().spawnMultipliers.put(pat, v), 0.25, 0, 100, m::refresh);
                l.add(new ListMenu.Entry(ic, c -> {
                    if (c.drop()) { cfg().spawnMultipliers.remove(pat); save(); m.refresh(); }
                    else stepper.accept(c);
                }));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.LIME_DYE, "§aAñadir mob...", "§7Para AUMENTAR usa un mob concreto"), c ->
                new Browser("Elige un mob", Kind.ENTITY, pat -> {
                    cfg().spawnMultipliers.putIfAbsent(pat, (pat.contains("*") || pat.startsWith("#")) ? 0.5 : 2.0);
                    save();
                }, () -> m.open(c.player), pat -> cfg().spawnMultipliers.containsKey(pat)).open(c.player));
        m.control(() -> Icons.of(Items.WRITABLE_BOOK, "§aAñadir por patrón"), c ->
                ChatPrompt.ask(c.player, "Escribe el patrón de mobs", t -> {
                    cfg().spawnMultipliers.putIfAbsent(Matchers.norm(t), 0.5); save(); m.open(c.player);
                }, () -> m.open(c.player)));
        m.open(sp);
    }

    private static Config.SpawnRule ruleFor(String entity) {
        for (Config.SpawnRule r : cfg().extraSpawns) if (entity.equals(r.entity)) return r;
        return null;
    }

    private static void spawnRules(ServerPlayerEntity sp, Runnable back) {
        ListMenu m = new ListMenu("Aparecer en otros biomas", back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (Config.SpawnRule r : new ArrayList<>(cfg().extraSpawns)) {
                ItemStack ic = Icons.forPattern(r.entity, Kind.ENTITY);
                Icons.name(ic, "§a" + r.entity + (r.enabled ? "" : " §c(apagada)"));
                Icons.lore(ic, "§7Biomas: " + r.biomes, "§7Probabilidad: " + fmt(r.chance) + "  Máx cerca: " + r.maxNearby,
                        "§7Grupo: " + r.minGroup + "-" + r.maxGroup + "  Forzar: " + r.forcePlacement, "", "§eClic: editar");
                l.add(new ListMenu.Entry(ic, c -> ruleEditor(c.player, r, () -> spawnRules(c.player, back))));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.LIME_DYE, "§aNueva regla...", "§7Elige el mob y luego los biomas"), c ->
                new Browser("Elige el mob", Kind.ENTITY, pat -> {
                    if (pat.contains("*") || pat.startsWith("#")) { tell(c.player, "§cElige un mob concreto, no un patrón."); return; }
                    Config.SpawnRule r = ruleFor(pat);
                    if (r == null) { r = new Config.SpawnRule(); r.entity = pat; cfg().extraSpawns.add(r); save(); }
                    final Config.SpawnRule fr = r;
                    ruleEditor(c.player, fr, () -> spawnRules(c.player, back));
                }, () -> m.open(c.player), pat -> ruleFor(pat) != null).open(c.player));
        m.open(sp);
    }

    private static void ruleEditor(ServerPlayerEntity sp, Config.SpawnRule r, Runnable back) {
        ButtonMenu m = new ButtonMenu("Regla: " + r.entity, 4);
        m.button(10, () -> Icons.of(Items.CLOCK, "§eProbabilidad: §f" + fmt(r.chance), "§7Por jugador cada intervalo del spawner",
                        "§7Clic izq +0.05 / der -0.05 / shift ±0.5"),
                step(() -> r.chance, v -> r.chance = v, 0.05, 0, 1, m::refresh));
        m.button(11, () -> Icons.of(Items.HOPPER, "§eMáximo cerca: §f" + r.maxNearby, "§7Límite de ese mob alrededor del jugador",
                "§7Clic izq +1 / der -1 / shift ±5"), istep(() -> r.maxNearby, v -> r.maxNearby = v, 1, 1, 200, m::refresh));
        m.button(12, () -> Icons.of(Items.LEAD, "§eGrupo mínimo: §f" + r.minGroup, "§7Clic izq +1 / der -1"),
                istep(() -> r.minGroup, v -> { r.minGroup = v; if (r.maxGroup < v) r.maxGroup = v; }, 1, 1, 50, m::refresh));
        m.button(13, () -> Icons.of(Items.LEAD, "§eGrupo máximo: §f" + r.maxGroup, "§7Clic izq +1 / der -1"),
                istep(() -> r.maxGroup, v -> r.maxGroup = Math.max(v, r.minGroup), 1, 1, 50, m::refresh));
        m.toggleIcon(14, Items.GRASS_BLOCK, "Forzar colocación", "Ignora las reglas vanilla (ej. ahogado en tierra)",
                () -> r.forcePlacement, v -> r.forcePlacement = v);
        m.toggleIcon(15, Items.COAL, "Solo en oscuridad", "Solo aparece con poca luz", () -> r.requireDark, v -> r.requireDark = v);
        m.toggleIcon(16, Items.LEVER, "Regla activa", "Apagar sin borrarla", () -> r.enabled, v -> r.enabled = v);
        m.button(19, () -> Icons.of(Items.FILLED_MAP, "§aBiomas donde aparece", "§7Actuales: " + r.biomes, "", "§eClic: editar biomas"),
                c -> biomesEditor(c.player, r.biomes, "Biomas de " + r.entity, () -> ruleEditor(c.player, r, back), true));
        m.button(25, () -> Icons.of(Items.TNT, "§cBorrar regla"), c -> {
            cfg().extraSpawns.remove(r); save(); back.run();
        });
        m.back(back);
        m.open(sp);
    }

    /** Edita una colección de patrones de bioma (lista de la regla, o biomas prohibidos). */
    private static void biomesEditor(ServerPlayerEntity sp, Collection<String> col, String title, Runnable back, boolean allowAll) {
        ListMenu m = new ListMenu(title, back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (String pat : new ArrayList<>(col)) {
                ItemStack ic = Icons.of(Items.GRASS_BLOCK, "§f" + pat, "§7Clic: quitar");
                l.add(new ListMenu.Entry(ic, c -> { col.remove(pat); save(); m.refresh(); }));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.LIME_DYE, "§aAñadir bioma..."), c ->
                new Browser("Elige biomas", Kind.BIOME, pat -> { if (!col.contains(pat)) col.add(pat); save(); },
                        () -> m.open(c.player), col::contains).open(c.player));
        m.control(() -> Icons.of(Items.WRITABLE_BOOK, "§aAñadir por patrón / tag", "§7Ej: #minecraft:is_taiga"), c ->
                ChatPrompt.ask(c.player, "Escribe el bioma o tag", t -> {
                    col.add(Matchers.norm(t)); save(); m.open(c.player);
                }, () -> m.open(c.player)));
        if (allowAll) {
            m.control(() -> Icons.of(Items.NETHER_STAR, "§dTodos los biomas (*)"), c -> {
                if (!col.contains("*")) col.add("*"); save(); m.refresh();
            });
        }
        m.open(sp);
    }

    private static void banList(ServerPlayerEntity sp, Runnable back) {
        ListMenu m = new ListMenu("Prohibir mobs en biomas", back);
        m.entries(() -> {
            cfg().blockedSpawnBiomes.values().removeIf(Set::isEmpty);
            List<ListMenu.Entry> l = new ArrayList<>();
            for (String pat : new ArrayList<>(cfg().blockedSpawnBiomes.keySet())) {
                Set<String> set = cfg().blockedSpawnBiomes.get(pat);
                ItemStack ic = Icons.forPattern(pat, Kind.ENTITY);
                Icons.name(ic, "§c" + pat);
                Icons.lore(ic, "§7Prohibido en: " + set, "", "§eClic: editar biomas", "§eQ (tirar): quitar todo");
                l.add(new ListMenu.Entry(ic, c -> {
                    if (c.drop()) { cfg().blockedSpawnBiomes.remove(pat); save(); m.refresh(); }
                    else biomesEditor(c.player, set, "Prohibido: " + pat, () -> banList(c.player, back), false);
                }));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.LIME_DYE, "§aElegir mob..."), c ->
                new Browser("Elige el mob", Kind.ENTITY, pat -> {
                    Set<String> set = cfg().blockedSpawnBiomes.computeIfAbsent(pat, k -> new LinkedHashSet<>());
                    save();
                    biomesEditor(c.player, set, "Prohibido: " + pat, () -> banList(c.player, back), false);
                }, () -> m.open(c.player), pat -> cfg().blockedSpawnBiomes.containsKey(pat)).open(c.player));
        m.open(sp);
    }

    // =====================================================================
    // DAÑO DE MOBS
    // =====================================================================
    private static void damageRoot(ServerPlayerEntity sp, Runnable back) {
        ButtonMenu m = new ButtonMenu("Daño de mobs", 3);
        m.toggle(10, "Sistema de daño", "Interruptor general", () -> cfg().damageEnabled, v -> cfg().damageEnabled = v);
        m.button(12, () -> Icons.of(Items.IRON_SWORD, "§eMultiplicador global: §fx" + fmt(cfg().globalMobDamageMultiplier),
                        "§7Afecta a todos los mobs (no jugadores)", "§7salvo los que tengan valor propio",
                        "§7Clic izq +0.25 / der -0.25 / shift ±2.5"),
                step(() -> cfg().globalMobDamageMultiplier, v -> cfg().globalMobDamageMultiplier = v, 0.25, 0, 1000, m::refresh));
        m.button(14, () -> Icons.of(Items.ZOMBIE_SPAWN_EGG, "§aDaño por mob", "§7Valor propio para cada mob", "§7(incluye mobs de otros mods)"),
                c -> damageList(c.player, () -> damageRoot(c.player, back)));
        m.back(back);
        m.open(sp);
    }

    private static void damageList(ServerPlayerEntity sp, Runnable back) {
        ListMenu m = new ListMenu("Daño por mob", back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (String pat : new ArrayList<>(cfg().damageMultipliers.keySet())) {
                double val = cfg().damageMultipliers.get(pat);
                ItemStack ic = Icons.forPattern(pat, Kind.ENTITY);
                Icons.name(ic, "§e" + pat + " §fx" + fmt(val));
                Icons.lore(ic, "§7Multiplica el daño que HACE este mob", "", "§eClic izq: +0.25  §eClic der: -0.25",
                        "§eShift: ±2.5", "§eQ (tirar): eliminar");
                Consumer<Click> stepper = step(() -> cfg().damageMultipliers.getOrDefault(pat, 1.0),
                        v -> cfg().damageMultipliers.put(pat, v), 0.25, 0, 1000, m::refresh);
                l.add(new ListMenu.Entry(ic, c -> {
                    if (c.drop()) { cfg().damageMultipliers.remove(pat); save(); m.refresh(); }
                    else stepper.accept(c);
                }));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.LIME_DYE, "§aAñadir mob..."), c ->
                new Browser("Elige un mob", Kind.ENTITY, pat -> { cfg().damageMultipliers.putIfAbsent(pat, 2.0); save(); },
                        () -> m.open(c.player), pat -> cfg().damageMultipliers.containsKey(pat)).open(c.player));
        m.control(() -> Icons.of(Items.WRITABLE_BOOK, "§aAñadir por patrón", "§7Ej: mimod:*  o  minecraft:player"), c ->
                ChatPrompt.ask(c.player, "Escribe el patrón de mobs", t -> {
                    cfg().damageMultipliers.putIfAbsent(Matchers.norm(t), 2.0); save(); m.open(c.player);
                }, () -> m.open(c.player)));
        m.open(sp);
    }

    // =====================================================================
    // REGLAS EXTREMAS
    // =====================================================================
    private static Map<String, Double> cat(String k) {
        Config c = cfg();
        return switch (k) {
            case "use" -> c.useDamage;
            case "stand" -> c.standDamage;
            case "break" -> c.breakDamage;
            case "place" -> c.placeDamage;
            default -> c.itemDamage;
        };
    }

    private static void rulesRoot(ServerPlayerEntity sp, Runnable back) {
        ButtonMenu m = new ButtonMenu("Reglas extremas", 6);
        m.toggleIcon(1, Items.OAK_SAPLING, "Hojas sin brotes", "Las hojas ya no sueltan brotes", () -> cfg().noSaplingDrops, v -> cfg().noSaplingDrops = v);
        m.toggleIcon(2, Items.DIRT, "Sin replantar", "No se pueden plantar brotes", () -> cfg().noSaplingPlanting, v -> cfg().noSaplingPlanting = v);
        m.toggleIcon(4, Items.TNT, "DAÑO EXTREMO (general)", "Interruptor de las reglas de daño de abajo", () -> cfg().extremeDamageEnabled, v -> cfg().extremeDamageEnabled = v);
        m.toggleIcon(5, Items.GOLDEN_APPLE, "Afecta a creativo", "Si no, creativo/espectador se salvan", () -> cfg().extremeAffectCreative, v -> cfg().extremeAffectCreative = v);

        int slot = 9;
        for (RuleDefs.Def d : RuleDefs.EXTRA) {
            m.toggleIcon(slot++, d.icon(), d.label(), d.desc(), () -> RuleDefs.on(d.id()), v -> {
                if (v) cfg().rules.add(d.id()); else cfg().rules.remove(d.id());
            });
        }
        m.button(19, () -> Icons.of(Items.OAK_DOOR, "§cDaño al USAR bloques", "§7Puertas, botones, palancas, cofres...", "§7Cada uno con su daño"),
                c -> damageCat(c.player, "use", () -> rulesRoot(c.player, back)));
        m.button(20, () -> Icons.of(Items.MAGMA_BLOCK, "§cDaño al PISAR bloques", "§7Magma, hierba, arena de almas..."),
                c -> damageCat(c.player, "stand", () -> rulesRoot(c.player, back)));
        m.button(21, () -> Icons.of(Items.IRON_PICKAXE, "§cDaño al ROMPER bloques", "§7Troncos, piedra, minerales..."),
                c -> damageCat(c.player, "break", () -> rulesRoot(c.player, back)));
        m.button(22, () -> Icons.of(Items.BRICKS, "§cDaño al COLOCAR bloques"),
                c -> damageCat(c.player, "place", () -> rulesRoot(c.player, back)));
        m.button(23, () -> Icons.of(Items.APPLE, "§cDaño al USAR items", "§7Comer, lanzar, disparar..."),
                c -> damageCat(c.player, "item", () -> rulesRoot(c.player, back)));
        m.button(25, () -> Icons.of(Items.COMMAND_BLOCK, "§6Gamerules de Minecraft", "§7keepInventory, mobGriefing,", "§7regeneración natural..."),
                c -> gamerules(c.player, () -> rulesRoot(c.player, back)));
        m.back(back);
        m.open(sp);
    }

    private static final Map<String, String[][]> PRESETS = Map.of(
            "use", new String[][]{{"Puertas", "#minecraft:doors"}, {"Botones", "#minecraft:buttons"}},
            "stand", new String[][]{{"Magma", "minecraft:magma_block"}}
    );

    private static void damageCat(ServerPlayerEntity sp, String category, Runnable back) {
        Map<String, Double> map = cat(category);
        Kind kind = category.equals("item") ? Kind.ITEM : Kind.BLOCK;
        String label = switch (category) {
            case "use" -> "usar bloques";
            case "stand" -> "pisar bloques";
            case "break" -> "romper bloques";
            case "place" -> "colocar bloques";
            default -> "usar items";
        };
        ListMenu m = new ListMenu("Daño al " + label, back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (String key : new ArrayList<>(map.keySet())) {
                double val = map.get(key);
                ItemStack ic = Icons.forPattern(key, kind);
                Icons.name(ic, "§c" + key + " §f→ " + fmt(val) + " de daño");
                Icons.lore(ic, "§eClic izq: ×10   §eClic der: ÷10", "§eShift+clic: escribir el valor", "§eQ (tirar): eliminar");
                l.add(new ListMenu.Entry(ic, c -> {
                    if (c.drop()) { map.remove(key); save(); m.refresh(); }
                    else if (c.shift()) {
                        ChatPrompt.ask(c.player, "Escribe el daño para " + key, t -> {
                            try { map.put(key, Math.max(0, Double.parseDouble(t.replace(',', '.')))); save(); }
                            catch (NumberFormatException ex) { tell(c.player, "§cNúmero inválido."); }
                            m.open(c.player);
                        }, () -> m.open(c.player));
                    } else if (c.adjustable()) {
                        double cur = map.getOrDefault(key, 1.0);
                        map.put(key, c.right() ? Math.max(1, cur / 10) : Math.min(1.0E9, cur * 10));
                        save();
                        m.refresh();
                    }
                }));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.LIME_DYE, "§aAñadir...", "§7Se crea con 1.000.000 de daño"), c ->
                new Browser("Elige qué dañará", kind, pat -> { map.putIfAbsent(pat, 1_000_000.0); cfg().extremeDamageEnabled = true; save(); },
                        () -> m.open(c.player), map::containsKey).open(c.player));
        m.control(() -> Icons.of(Items.WRITABLE_BOOK, "§aAñadir por patrón / tag", "§7Ej: #minecraft:doors"), c ->
                ChatPrompt.ask(c.player, "Escribe el patrón o #tag", t -> {
                    map.putIfAbsent(Matchers.norm(t), 1_000_000.0); cfg().extremeDamageEnabled = true; save(); m.open(c.player);
                }, () -> m.open(c.player)));
        for (String[] p : PRESETS.getOrDefault(category, new String[0][])) {
            m.control(() -> Icons.of(Items.NAME_TAG, "§dPreset: " + p[0], "§7Añade " + p[1] + " con 1.000.000"), c -> {
                map.putIfAbsent(p[1], 1_000_000.0); cfg().extremeDamageEnabled = true; save(); m.refresh();
            });
        }
        m.control(() -> {
            boolean on = cfg().extremeDamageEnabled;
            return Icons.of(on ? Items.LIME_CONCRETE : Items.RED_CONCRETE, (on ? "§aDaño extremo: ACTIVADO" : "§cDaño extremo: APAGADO"),
                    "§7Interruptor general", "§eClic para cambiar");
        }, c -> { cfg().extremeDamageEnabled = !cfg().extremeDamageEnabled; save(); m.refresh(); });
        m.open(sp);
    }

    private static void gamerules(ServerPlayerEntity sp, Runnable back) {
        ListMenu m = new ListMenu("Gamerules de Minecraft", back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            GameRules gr = sp.getServer().getGameRules();
            gr.accept(new GameRules.Visitor() {
                @Override
                public void visitBoolean(GameRules.Key<GameRules.BooleanRule> key, GameRules.Type<GameRules.BooleanRule> type) {
                    boolean on = gr.getBoolean(key);
                    ItemStack ic = new ItemStack(on ? Items.LIME_CONCRETE : Items.RED_CONCRETE);
                    Icons.name(ic, (on ? "§a" : "§c") + key.getName());
                    Icons.lore(ic, "§7Valor: " + on, "", "§eClic para cambiar");
                    l.add(new ListMenu.Entry(ic, c -> { gr.get(key).set(!on, sp.getServer()); m.refresh(); }));
                }
            });
            return l;
        });
        m.open(sp);
    }

    // =====================================================================
    // CATÁLOGO DE ITEMS (como el panel del video)
    // =====================================================================
    private record Cat(String id, String title, Item icon) {}

    private static final List<Cat> CATS_ITEMS = List.of(
            new Cat("building", "Construcción", Items.BRICKS),
            new Cat("decor", "Decoración", Items.POPPY),
            new Cat("redstone", "Redstone", Items.REDSTONE),
            new Cat("tools", "Herramientas", Items.IRON_PICKAXE),
            new Cat("combat", "Combate", Items.IRON_SWORD),
            new Cat("armor", "Armadura", Items.IRON_CHESTPLATE),
            new Cat("food", "Comida", Items.APPLE),
            new Cat("materials", "Materiales", Items.IRON_INGOT),
            new Cat("eggs", "Huevos de mob", Items.ZOMBIE_SPAWN_EGG),
            new Cat("blocked", "Bloqueados", Items.BARRIER),
            new Cat("all", "Todos", Items.CHEST));

    private static final int[] CAT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22};

    public static void items(ServerPlayerEntity sp, Runnable back) {
        ButtonMenu m = new ButtonMenu("Items y bloqueos", 5);
        m.theme(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
        m.button(4, () -> Icons.of(Items.BOOK, "§b§lCatálogo de items",
                "§7Elige una categoría y toca un item", "§7para ver su detalle y bloquearlo",
                "", "§7Bloqueados: §f" + cfg().blockedItems.size() + " patrones",
                "§7Recetas bloqueadas: §f" + cfg().blockedRecipes.size(),
                "§7Bloques protegidos: §f" + cfg().protectedBlocks.size()), null);
        for (int i = 0; i < CATS_ITEMS.size(); i++) {
            Cat cat = CATS_ITEMS.get(i);
            m.button(CAT_SLOTS[i], () -> Icons.of(cat.icon(), "§f§l" + cat.title(), "§7Ver items de esta categoría", "", "§eClic para abrir"),
                    c -> catalog(c.player, cat, "", back));
        }
        m.button(23, () -> Icons.of(Items.COMPASS, "§b§lBuscar item", "§7Escribe un nombre en el chat", "", "§eClic para buscar"),
                c -> ChatPrompt.ask(c.player, "Escribe el nombre o id del item a buscar",
                        t -> catalog(c.player, CATS_ITEMS.get(CATS_ITEMS.size() - 1), t, back),
                        () -> items(c.player, back)));
        m.button(24, () -> Icons.of(Items.COMPARATOR, "§e§lOpciones", "§7Qué se bloquea y quién lo ignora"),
                c -> itemOptions(c.player, () -> items(c.player, back)));
        m.button(25, () -> Icons.of(Items.PLAYER_HEAD, "§b§lExcepciones por jugador", "§7Permitir items a jugadores concretos"),
                c -> playersManage(c.player, () -> items(c.player, back)));
        m.button(29, () -> Icons.of(Items.WRITABLE_BOOK, "§c§lPatrones bloqueados", "§7Lista completa (incluye mod:*, #tags)", "§7Aquí se desbloquea para todos"),
                c -> patternsList(c.player, () -> items(c.player, back)));
        m.button(30, () -> Icons.of(Items.CRAFTING_TABLE, "§6§lRecetas bloqueadas", "§7Items que no se pueden craftear", "§7(siguen existiendo por otros medios)"),
                c -> patternSetMenu(c.player, "Recetas bloqueadas", cfg().blockedRecipes, Kind.ITEM,
                        "§7No se puede craftear", () -> items(c.player, back)));
        m.button(31, () -> Icons.of(Items.OBSIDIAN, "§5§lBloques protegidos", "§7Bloques que nadie puede romper"),
                c -> patternSetMenu(c.player, "Bloques protegidos", cfg().protectedBlocks, Kind.BLOCK,
                        "§7No se puede romper", () -> items(c.player, back)));
        m.back(back);
        m.open(sp);
    }

    private static void catalog(ServerPlayerEntity sp, Cat cat, String search, Runnable back) {
        Runnable again = () -> catalog(sp, cat, search, back);
        new Browser(cat.title(), Kind.ITEM, key -> {
            if (key.endsWith(":*")) {
                if (!cfg().blockedItems.remove(key)) cfg().blockedItems.add(key);
                save();
                tell(sp, "§eAlternado el bloqueo de §f" + key);
                return;
            }
            itemDetail(sp, key, again);
        }, () -> items(sp, back), key -> {
            Identifier id = Identifier.tryParse(key);
            return id != null && Categories.blockedMatch(Registries.ITEM.get(id));
        }).filter(id -> Categories.has(id, cat.id()))
                .markedLabel("§c✖ BLOQUEADO")
                .pickLabel("§eClic: ver detalle")
                .search(search)
                .open(sp);
    }

    private static String firstMatch(Set<String> set, ItemStack stack) {
        for (String pat : set) if (Matchers.item(stack, pat)) return pat;
        return null;
    }

    private static void itemDetail(ServerPlayerEntity sp, String key, Runnable back) {
        Identifier id = Identifier.tryParse(key);
        if (id == null) return;
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) { tell(sp, "§cEse item no existe."); return; }
        String blockKey = item instanceof BlockItem bi ? Registries.BLOCK.getId(bi.getBlock()).toString() : null;

        ButtonMenu m = new ButtonMenu("Detalle del ítem", 5);
        m.theme(Items.LIGHT_BLUE_STAINED_GLASS_PANE);
        m.button(13, () -> {
            ItemStack st = new ItemStack(item);
            String bp = firstMatch(cfg().blockedItems, st);
            String rp = firstMatch(cfg().blockedRecipes, st);
            List<String> lore = new ArrayList<>();
            lore.add("§8" + key);
            lore.add("§7Mod: §f" + id.getNamespace());
            lore.add("");
            lore.add("§7Ítem:   " + (bp == null ? "§aPermitido" : "§cBloqueado §8(" + bp + ")"));
            lore.add("§7Receta: " + (rp == null ? "§aPermitida" : "§cBloqueada §8(" + rp + ")"));
            if (blockKey != null) {
                boolean prot = false;
                for (String pat : cfg().protectedBlocks) if (Matchers.block(((BlockItem) item).getBlock().getDefaultState(), pat)) prot = true;
                lore.add("§7Bloque: " + (prot ? "§cProtegido" : "§aMinable"));
            }
            Icons.lore(st, lore);
            return st;
        }, null);

        m.button(29, () -> {
            boolean on = cfg().blockedItems.contains(key);
            return Icons.of(on ? Items.LIME_CONCRETE : Items.RED_CONCRETE, on ? "§a§lDesbloquear ítem" : "§c§lBloquear ítem",
                    "§7Bloquea uso, recogida e inventario", "§7(también en creativo)", "", "§eClic para cambiar");
        }, c -> {
            if (!cfg().blockedItems.remove(key)) cfg().blockedItems.add(key);
            save();
            m.refresh();
        });
        m.button(30, () -> {
            boolean on = cfg().blockedRecipes.contains(key);
            return Icons.of(on ? Items.LIME_CONCRETE : Items.ORANGE_CONCRETE, on ? "§a§lDesbloquear receta" : "§6§lBloquear receta",
                    "§7No se podrá craftear en mesa", "", "§eClic para cambiar");
        }, c -> {
            if (!cfg().blockedRecipes.remove(key)) cfg().blockedRecipes.add(key);
            save();
            m.refresh();
        });
        m.button(31, () -> {
            if (blockKey == null) return Icons.of(Items.GRAY_DYE, "§8No es un bloque");
            boolean on = cfg().protectedBlocks.contains(blockKey);
            return Icons.of(on ? Items.LIME_CONCRETE : Items.PURPLE_CONCRETE, on ? "§a§lQuitar protección" : "§5§lProteger bloque",
                    "§7Nadie podrá romper este bloque", "", "§eClic para cambiar");
        }, c -> {
            if (blockKey == null) return;
            if (!cfg().protectedBlocks.remove(blockKey)) cfg().protectedBlocks.add(blockKey);
            save();
            m.refresh();
        });
        m.button(32, () -> Icons.of(Items.PLAYER_HEAD, "§b§lPermitir a un jugador", "§7Excepción solo para él"), c ->
                players("¿A quién le permites " + key + "?", c.player, () -> itemDetail(c.player, key, back), t -> {
                    cfg().playerExceptions.computeIfAbsent(t.getUuidAsString(), k -> new LinkedHashSet<>()).add(key);
                    cfg().names.put(t.getUuidAsString(), t.getName().getString());
                    save();
                    tell(c.player, "§aPermitido " + key + " para " + t.getName().getString());
                    itemDetail(c.player, key, back);
                }));
        m.button(33, () -> Icons.of(Items.CHEST, "§f§lObtener", "§7Clic: 1 unidad", "§7Shift+clic: stack completo"), c -> {
            c.player.getInventory().offerOrDrop(new ItemStack(item, c.shift() ? item.getMaxCount() : 1));
        });
        m.back(back);
        m.open(sp);
    }

    /** Lista editable de patrones (recetas bloqueadas, bloques protegidos...). */
    private static void patternSetMenu(ServerPlayerEntity sp, String title, Set<String> set, Kind kind, String desc, Runnable back) {
        ListMenu m = new ListMenu(title, back);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (String pat : new ArrayList<>(set)) {
                ItemStack ic = Icons.forPattern(pat, kind);
                Icons.name(ic, "§c" + pat);
                Icons.lore(ic, desc, "", "§eClic: quitar");
                l.add(new ListMenu.Entry(ic, c -> { set.remove(pat); save(); m.refresh(); }));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.LIME_DYE, "§aAñadir..."), c ->
                new Browser(title, kind, pat -> { set.add(pat); save(); }, () -> m.open(c.player), set::contains).open(c.player));
        m.control(() -> Icons.of(Items.WRITABLE_BOOK, "§aAñadir por patrón / tag", "§7Ej: #minecraft:logs  |  mimod:*"), c ->
                ChatPrompt.ask(c.player, "Escribe el patrón", t -> { set.add(Matchers.norm(t)); save(); m.open(c.player); }, () -> m.open(c.player)));
        m.open(sp);
    }

    // =====================================================================
    // EVENTO: reparto aleatorio de jugadores entre anfitriones
    // =====================================================================
    public static void event(ServerPlayerEntity sp, Runnable backArg) {
        Runnable back = backArg != null ? backArg : () -> main(sp);
        MinecraftServer server = sp.getServer();
        ButtonMenu m = new ButtonMenu("Evento: repartir jugadores", 5);
        m.theme(Items.ORANGE_STAINED_GLASS_PANE);
        m.button(4, () -> {
            int h = EventManager.hosts(server).size();
            int p = EventManager.participants(server).size();
            String per = h == 0 ? "-" : (p / h) + (p % h == 0 ? "" : " a " + (p / h + 1));
            return Icons.of(Items.NETHER_STAR, "§6§lResumen del evento",
                    "§7Anfitriones: §f" + h, "§7Participantes: §f" + p, "§7Por anfitrión: §f" + per,
                    "§7Radio de dispersión: §f" + cfg().eventRadius + " bloques",
                    "§7Cuenta atrás: §f" + cfg().eventCountdown + " s",
                    EventManager.running() ? "§e¡Cuenta atrás en curso!" : "§8Listo");
        }, null);
        m.button(10, () -> Icons.of(Items.PLAYER_HEAD, "§a§lElegir anfitriones",
                        "§7Los admins que recibirán a los grupos", "§7(cada uno en su isla)", "", "§7Elegidos: §f" + cfg().eventHosts.size(), "", "§eClic para elegir"),
                c -> pickPlayers(c.player, "Elegir anfitriones", cfg().eventHosts, true, () -> event(c.player, back)));
        m.button(12, () -> Icons.of(Items.BARRIER, "§c§lExcluir jugadores",
                        "§7No serán movidos (staff, espectadores...)", "", "§7Excluidos: §f" + cfg().eventExcluded.size(), "", "§eClic para elegir"),
                c -> pickPlayers(c.player, "Excluir del evento", cfg().eventExcluded, false, () -> event(c.player, back)));
        m.button(14, () -> Icons.of(Items.COMPASS, "§e§lRadio de dispersión: §f" + cfg().eventRadius,
                        "§7Los jugadores caen repartidos al azar", "§7dentro de este radio del anfitrión", "§7Clic izq +1 / der -1 / shift ±5"),
                istep(() -> cfg().eventRadius, v -> cfg().eventRadius = v, 1, 0, 100, m::refresh));
        m.button(16, () -> Icons.of(Items.CLOCK, "§e§lCuenta atrás: §f" + cfg().eventCountdown + " s",
                        "§7Aviso antes de teletransportar", "§7Clic izq +1 / der -1 / shift ±5"),
                istep(() -> cfg().eventCountdown, v -> cfg().eventCountdown = v, 1, 0, 60, m::refresh));
        m.button(28, () -> Icons.of(Items.ENDER_EYE, "§b§lVista previa / barajar",
                        "§7Mira cómo quedarían los grupos", "§7y vuelve a barajar si quieres", "", "§eClic para ver"),
                c -> eventPreview(c.player, () -> event(c.player, back)));
        m.button(30, () -> {
            ItemStack st = Icons.of(Items.LIME_CONCRETE, "§a§l¡EJECUTAR EVENTO!", "§7Reparte y teletransporta a todos", "", "§eClic para continuar");
            Icons.glint(st);
            return st;
        }, c -> {
            if (EventManager.hosts(server).isEmpty()) { tell(c.player, "§cElige al menos un anfitrión conectado."); return; }
            if (EventManager.participants(server).isEmpty()) { tell(c.player, "§cNo hay participantes conectados."); return; }
            if (EventManager.plan().isEmpty()) EventManager.buildPlan(server);
            eventConfirm(c.player, () -> event(c.player, back));
        });
        m.button(32, () -> Icons.of(Items.ENDER_PEARL, "§d§lDevolver a todos",
                        "§7Regresa a cada jugador a donde estaba", "§7Pendientes: §f" + EventManager.returnableCount(), "", "§eClic para devolver"),
                c -> {
                    int n = EventManager.returnAll(server);
                    tell(c.player, "§aDevueltos " + n + " jugadores.");
                    m.refresh();
                });
        m.button(34, () -> Icons.of(Items.RED_CONCRETE, "§c§lCancelar cuenta atrás"),
                c -> { EventManager.cancel(server); m.refresh(); });
        m.back(back);
        m.open(sp);
    }

    private static void pickPlayers(ServerPlayerEntity sp, String title, Set<String> set, boolean offerAdmins, Runnable back) {
        ListMenu m = new ListMenu(title, back);
        m.theme(Items.ORANGE_STAINED_GLASS_PANE);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (ServerPlayerEntity p : sp.getServer().getPlayerManager().getPlayerList()) {
                boolean on = set.contains(p.getUuidAsString());
                ItemStack ic = Icons.head(p, (on ? "§a✔ " : "§7") + p.getName().getString(),
                        on ? "§aSeleccionado" : "§8No seleccionado", "", "§eClic para alternar");
                if (on) Icons.glint(ic);
                l.add(new ListMenu.Entry(ic, c -> {
                    String u = p.getUuidAsString();
                    if (!set.remove(u)) set.add(u);
                    cfg().names.put(u, p.getName().getString());
                    save();
                    m.refresh();
                }));
            }
            return l;
        });
        if (offerAdmins) {
            m.control(() -> Icons.of(Items.NETHER_STAR, "§6Marcar a todos los admins", "§7Todos los OP conectados"), c -> {
                for (ServerPlayerEntity p : sp.getServer().getPlayerManager().getPlayerList()) {
                    if (Perms.isAdmin(p)) set.add(p.getUuidAsString());
                }
                save();
                m.refresh();
            });
        }
        m.control(() -> Icons.of(Items.BUCKET, "§cLimpiar selección"), c -> { set.clear(); save(); m.refresh(); });
        m.open(sp);
    }

    private static void eventPreview(ServerPlayerEntity sp, Runnable back) {
        MinecraftServer server = sp.getServer();
        EventManager.buildPlan(server);
        ListMenu m = new ListMenu("Vista previa del reparto", back);
        m.theme(Items.ORANGE_STAINED_GLASS_PANE);
        m.entries(() -> {
            List<ListMenu.Entry> l = new ArrayList<>();
            for (Map.Entry<UUID, List<UUID>> e : EventManager.plan().entrySet()) {
                ServerPlayerEntity host = server.getPlayerManager().getPlayer(e.getKey());
                if (host == null) continue;
                List<String> lore = new ArrayList<>();
                lore.add("§7Jugadores asignados: §f" + e.getValue().size());
                lore.add("");
                int shown = 0;
                for (UUID id : e.getValue()) {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                    if (p == null) continue;
                    if (shown++ >= 20) break;
                    lore.add("§8• §f" + p.getName().getString());
                }
                if (e.getValue().size() > 20) lore.add("§8… y " + (e.getValue().size() - 20) + " más");
                ItemStack ic = Icons.head(host, "§a§l" + host.getName().getString() + " §7— §f" + e.getValue().size());
                Icons.lore(ic, lore);
                l.add(new ListMenu.Entry(ic, null));
            }
            return l;
        });
        m.control(() -> Icons.of(Items.ENDER_PEARL, "§b§lBarajar de nuevo", "§7Genera otro reparto aleatorio"), c -> {
            EventManager.buildPlan(server);
            m.refresh();
        });
        m.control(() -> Icons.of(Items.LIME_CONCRETE, "§a§l¡Ejecutar este reparto!"), c -> {
            if (EventManager.plan().isEmpty()) { tell(c.player, "§cNo hay reparto (¿anfitriones o participantes?)."); return; }
            eventConfirm(c.player, back);
        });
        m.open(sp);
    }

    private static void eventConfirm(ServerPlayerEntity sp, Runnable back) {
        MinecraftServer server = sp.getServer();
        ButtonMenu m = new ButtonMenu("¿Ejecutar el evento?", 3);
        m.theme(Items.RED_STAINED_GLASS_PANE);
        int total = 0;
        for (List<UUID> l : EventManager.plan().values()) total += l.size();
        final int count = total;
        m.button(4, () -> Icons.of(Items.PAPER, "§e¿Teletransportar a §f" + count + " §ejugadores?",
                "§7Grupos: §f" + EventManager.plan().size(), "§7Cuenta atrás: §f" + cfg().eventCountdown + " s"), null);
        m.button(11, () -> Icons.of(Items.LIME_CONCRETE, "§a§lSÍ, ejecutar"), c -> {
            String r = EventManager.start(server, c.player);
            tell(c.player, r);
            c.player.closeHandledScreen();
        });
        m.button(15, () -> Icons.of(Items.RED_CONCRETE, "§c§lNo, volver"), c -> back.run());
        m.open(sp);
    }

    // =====================================================================
    // VANISH
    // =====================================================================
    private static void vanishMenu(ServerPlayerEntity sp, Runnable back) {
        ButtonMenu m = new ButtonMenu("Vanish de admin", 3);
        m.theme(Items.LIME_STAINED_GLASS_PANE);
        m.button(11, () -> {
            boolean on = VanishManager.isVanished(sp);
            ItemStack st = Icons.of(on ? Items.ENDER_EYE : Items.ENDER_PEARL, on ? "§a§lVANISH: ACTIVADO" : "§7§lVANISH: desactivado",
                    "§7Los jugadores no te ven (cuerpo, nombre,", "§7ni en la lista de jugadores)", "", "§eClic para cambiar");
            if (on) Icons.glint(st);
            return st;
        }, c -> { VanishManager.toggle(c.player); m.refresh(); });
        m.toggle(13, "Otros admins te ven", "Los OP siguen viéndote", () -> cfg().vanishVisibleToOps, v -> cfg().vanishVisibleToOps = v);
        m.toggle(14, "Mobs te ignoran", "No te atacan estando en vanish", () -> cfg().vanishIgnoredByMobs, v -> cfg().vanishIgnoredByMobs = v);
        m.toggle(15, "No recoger items", "No recoges objetos del suelo en vanish", () -> cfg().vanishNoPickup, v -> cfg().vanishNoPickup = v);
        m.back(back);
        m.open(sp);
    }
}
