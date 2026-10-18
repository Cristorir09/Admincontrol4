package com.admincontrol;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Coincidencia por id exacto, comodines (*) y tags (#). Funciona con items/mobs/bloques de cualquier mod. */
public final class Matchers {
    private Matchers() {}

    private static final Map<String, Pattern> CACHE = new ConcurrentHashMap<>();

    public static boolean glob(String pattern, String text) {
        if (pattern.equals("*")) return true;
        if (!pattern.contains("*")) return pattern.equals(text);
        Pattern p = CACHE.computeIfAbsent(pattern, k -> {
            String[] parts = k.split("\\*", -1);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(".*");
                sb.append(Pattern.quote(parts[i]));
            }
            return Pattern.compile(sb.toString());
        });
        return p.matcher(text).matches();
    }

    public static boolean item(ItemStack stack, String pattern) {
        if (pattern.startsWith("#")) {
            Identifier tid = Identifier.tryParse(pattern.substring(1));
            return tid != null && stack.isIn(TagKey.of(RegistryKeys.ITEM, tid));
        }
        return glob(pattern, Registries.ITEM.getId(stack.getItem()).toString());
    }

    public static boolean block(BlockState state, String pattern) {
        if (pattern.startsWith("#")) {
            Identifier tid = Identifier.tryParse(pattern.substring(1));
            return tid != null && state.isIn(TagKey.of(RegistryKeys.BLOCK, tid));
        }
        return glob(pattern, Registries.BLOCK.getId(state.getBlock()).toString());
    }

    public static boolean entity(EntityType<?> type, String pattern) {
        if (pattern.startsWith("#")) {
            Identifier tid = Identifier.tryParse(pattern.substring(1));
            return tid != null && type.isIn(TagKey.of(RegistryKeys.ENTITY_TYPE, tid));
        }
        return glob(pattern, Registries.ENTITY_TYPE.getId(type).toString());
    }

    public static boolean biome(RegistryEntry<Biome> biome, String pattern) {
        if (pattern.startsWith("#")) {
            Identifier tid = Identifier.tryParse(pattern.substring(1));
            return tid != null && biome.isIn(TagKey.of(RegistryKeys.BIOME, tid));
        }
        return biome.getKey().map(k -> glob(pattern, k.getValue().toString())).orElse(false);
    }

    /** Busca valor por entidad: primero id exacto, luego patrones. */
    public static Double lookupEntity(Map<String, Double> map, EntityType<?> type) {
        if (map.isEmpty()) return null;
        Double exact = map.get(Registries.ENTITY_TYPE.getId(type).toString());
        if (exact != null) return exact;
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (entity(type, e.getKey())) return e.getValue();
        }
        return null;
    }

    /** Normaliza lo que escribe el usuario: "zombie" -> "minecraft:zombie", "*_egg" -> "*:*_egg". */
    public static String norm(String s) {
        s = s.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.equals("*")) return s;
        if (s.startsWith("#")) return s.contains(":") ? s : "#minecraft:" + s.substring(1);
        if (!s.contains(":")) return s.contains("*") ? "*:" + s : "minecraft:" + s;
        return s;
    }
}
