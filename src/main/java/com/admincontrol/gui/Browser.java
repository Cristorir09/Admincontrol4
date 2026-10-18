package com.admincontrol.gui;

import com.admincontrol.Matchers;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Explorador de items / bloques / mobs / biomas (incluye los de todos los mods) con filtro por mod y búsqueda. */
public class Browser extends Menu {
    private final Kind kind;
    private final Consumer<String> onPick;
    private final Runnable back;
    private final Predicate<String> marked;
    private int page = 0;
    private String ns = null;
    private String search = "";
    private Predicate<Identifier> filter = null;
    private String markedLabel = "§c✔ Ya está en la lista";
    private String pickLabel = "§eClic: elegir";

    public Browser filter(Predicate<Identifier> f) { this.filter = f; return this; }
    public Browser markedLabel(String s) { this.markedLabel = s; return this; }
    public Browser pickLabel(String s) { this.pickLabel = s; return this; }
    public Browser search(String s) { this.search = s == null ? "" : s.toLowerCase(); return this; }

    public Browser(String title, Kind kind, Consumer<String> onPick, Runnable back, Predicate<String> marked) {
        super(title, 6);
        this.kind = kind;
        this.onPick = onPick;
        this.back = back;
        this.marked = marked;
    }

    private List<Identifier> allIds() {
        Iterable<Identifier> src = switch (kind) {
            case ITEM -> Registries.ITEM.getIds();
            case BLOCK -> Registries.BLOCK.getIds();
            case ENTITY -> Registries.ENTITY_TYPE.getIds();
            case BIOME -> viewer.getServer().getRegistryManager().get(RegistryKeys.BIOME).getIds();
        };
        List<Identifier> out = new ArrayList<>();
        for (Identifier id : src) {
            if ((kind == Kind.ITEM || kind == Kind.BLOCK) && id.getPath().equals("air")) continue;
            if (kind == Kind.ENTITY) {
                EntityType<?> t = Registries.ENTITY_TYPE.get(id);
                if (!t.isSummonable() || t.getSpawnGroup() == SpawnGroup.MISC) continue;
            }
            if (filter != null && !filter.test(id)) continue;
            out.add(id);
        }
        out.sort((a, b) -> a.toString().compareTo(b.toString()));
        return out;
    }

    @Override
    protected void build() {
        List<Identifier> all = allIds();
        TreeSet<String> nss = new TreeSet<>();
        for (Identifier i : all) nss.add(i.getNamespace());
        if (ns != null && !nss.contains(ns)) ns = null;

        List<Identifier> list = new ArrayList<>();
        for (Identifier i : all) {
            if (ns != null && !i.getNamespace().equals(ns)) continue;
            if (!search.isEmpty() && !i.toString().contains(search)) continue;
            list.add(i);
        }
        int pages = Math.max(1, (list.size() + 44) / 45);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx >= list.size()) break;
            Identifier id = list.get(idx);
            String key = id.toString();
            ItemStack icon = Icons.forId(id, kind);
            if (icon == null) {
                icon = new ItemStack(Items.PAPER);
                Icons.name(icon, "§f" + key);
            }
            boolean m = marked != null && marked.test(key);
            Icons.lore(icon, "§8" + key, m ? markedLabel : "§7", pickLabel);
            if (m) Icons.glint(icon);
            set(i, icon, c -> { onPick.accept(key); refresh(); });
        }

        fill(45, 54);
        final int pg = pages;
        final List<String> nsList = new ArrayList<>(nss);
        set(45, Icons.of(Items.ARROW, "§ePágina anterior"), c -> { if (page > 0) { page--; refresh(); } });
        set(53, Icons.of(Items.ARROW, "§ePágina siguiente"), c -> { if (page < pg - 1) { page++; refresh(); } });
        set(52, Icons.of(Items.PAPER, "§fPágina " + (page + 1) + "/" + pages, "§7Resultados: " + list.size()), null);

        set(46, Icons.of(Items.NAME_TAG, "§bMod: §f" + (ns == null ? "todos" : ns),
                "§7Clic izq: siguiente mod", "§7Clic der: anterior", "§7Mods cargados: " + nsList.size()), c -> {
            int cur = ns == null ? -1 : nsList.indexOf(ns);
            int next = cur + c.sign();
            if (next >= nsList.size()) next = -1;
            if (next < -1) next = nsList.size() - 1;
            ns = next < 0 ? null : nsList.get(next);
            page = 0;
            refresh();
        });
        set(47, Icons.of(Items.COMPASS, "§bBuscar: §f" + (search.isEmpty() ? "(nada)" : search),
                "§7Clic izq: escribir búsqueda en el chat", "§7Clic der: borrar búsqueda"), c -> {
            if (c.right()) { search = ""; page = 0; refresh(); }
            else ChatPrompt.ask(c.player, "Escribe el texto a buscar", t -> { search = t.toLowerCase(); page = 0; open(c.player); }, () -> open(c.player));
        });
        if (ns != null) {
            set(48, Icons.of(Items.CHEST, "§dElegir TODO el mod: §f" + ns + ":*", "§7Aplica a todo lo de ese mod", "§eClic para elegir"),
                    c -> { onPick.accept(ns + ":*"); refresh(); });
        }
        if (kind == Kind.ITEM || kind == Kind.BLOCK) {
            set(49, Icons.of(Items.HOPPER, "§7Atajo", "§7Haz clic en un objeto de", "§7TU inventario para elegirlo"), null);
            playerSlotAction = c -> {
                if (c.stack.isEmpty()) return;
                String key = null;
                if (kind == Kind.ITEM) key = Registries.ITEM.getId(c.stack.getItem()).toString();
                else if (c.stack.getItem() instanceof BlockItem bi) key = Registries.BLOCK.getId(bi.getBlock()).toString();
                if (key != null) { onPick.accept(key); refresh(); }
            };
        }
        if (back != null) set(51, Icons.of(Items.OAK_DOOR, "§6Volver"), c -> back.run());
    }
}
