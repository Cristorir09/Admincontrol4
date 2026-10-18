package com.admincontrol.gui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class Icons {
    private Icons() {}

    public static ItemStack of(ItemConvertible item, String name, String... lore) {
        ItemStack s = new ItemStack(item);
        name(s, name);
        lore(s, lore);
        return s;
    }

    public static void name(ItemStack s, String name) {
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled(st -> st.withItalic(false)));
    }

    public static void lore(ItemStack s, String... lines) {
        lore(s, Arrays.asList(lines));
    }

    public static void lore(ItemStack s, List<String> lines) {
        List<Text> t = new ArrayList<>();
        for (String l : lines) {
            t.add(Text.literal(l).styled(st -> st.withItalic(false).withColor(Formatting.GRAY)));
        }
        s.set(DataComponentTypes.LORE, new LoreComponent(t));
    }

    public static void glint(ItemStack s) {
        s.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
    }

    public static ItemStack pane() {
        return of(Items.BLACK_STAINED_GLASS_PANE, " ");
    }

    public static ItemStack pane(Item item) {
        return of(item, " ");
    }

    public static ItemStack head(ServerPlayerEntity p, String name, String... lore) {
        ItemStack s = new ItemStack(Items.PLAYER_HEAD);
        s.set(DataComponentTypes.PROFILE, new ProfileComponent(p.getGameProfile()));
        name(s, name);
        lore(s, lore);
        return s;
    }

    /** Icono para un id concreto (null si no existe). */
    public static ItemStack forId(Identifier id, Kind kind) {
        switch (kind) {
            case ITEM: {
                Optional<Item> o = Registries.ITEM.getOrEmpty(id);
                if (o.isPresent() && o.get() != Items.AIR) return new ItemStack(o.get());
                return null;
            }
            case BLOCK: {
                var o = Registries.BLOCK.getOrEmpty(id);
                if (o.isPresent()) {
                    Item it = o.get().asItem();
                    if (it != Items.AIR) return new ItemStack(it);
                }
                return null;
            }
            case ENTITY: {
                var o = Registries.ENTITY_TYPE.getOrEmpty(id);
                if (o.isPresent()) {
                    SpawnEggItem egg = SpawnEggItem.forEntity(o.get());
                    if (egg != null) return new ItemStack(egg);
                }
                return null;
            }
            default:
                return new ItemStack(Items.GRASS_BLOCK);
        }
    }

    /** Icono para un patrón (id, mod:*, #tag...). */
    public static ItemStack forPattern(String pat, Kind kind) {
        if (pat.startsWith("#")) return new ItemStack(Items.NAME_TAG);
        if (!pat.contains("*")) {
            Identifier id = Identifier.tryParse(pat);
            if (id != null) {
                ItemStack s = forId(id, kind);
                if (s != null) return s;
            }
        }
        return new ItemStack(Items.PAPER);
    }
}
