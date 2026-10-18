package com.admincontrol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Toda la configuración. Se guarda en config/admincontrol.json y también se edita con /ac ... */
public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("admincontrol.json");
    }

    // ---------------- ITEMS ----------------
    public boolean itemsEnabled = true;
    /** Los OP (nivel admin) ignoran los bloqueos. */
    public boolean opsBypass = false;
    public boolean blockUse = true;        // usar / colocar / atacar con el item
    public boolean blockPickup = true;     // recoger del suelo
    public boolean blockInventory = true;  // tenerlo en inventario (creativo, craftear, cofres...)
    /** Patrones: minecraft:diamond | mimod:* | *:*_spawn_egg | #minecraft:swords */
    public Set<String> blockedItems = new LinkedHashSet<>();
    /** uuid -> patrones permitidos solo para ese jugador */
    public Map<String, Set<String>> playerExceptions = new LinkedHashMap<>();
    /** uuid de jugadores que ignoran TODOS los bloqueos de items */
    public Set<String> fullBypass = new LinkedHashSet<>();
    public Map<String, String> names = new LinkedHashMap<>();

    // ---------------- SPAWN ----------------
    public boolean spawnEnabled = true;
    public int spawnerIntervalTicks = 40;
    public Set<String> blockedSpawns = new LinkedHashSet<>();
    /** patrón de entidad -> patrones de bioma donde NO puede aparecer */
    public Map<String, Set<String>> blockedSpawnBiomes = new LinkedHashMap<>();
    /** patrón de entidad -> multiplicador (<1 reduce, >1 aumenta; >1 solo con id exacto) */
    public Map<String, Double> spawnMultipliers = new LinkedHashMap<>();
    public List<SpawnRule> extraSpawns = new ArrayList<>();

    public static class SpawnRule {
        public String entity;
        public List<String> biomes = new ArrayList<>(List.of("*"));
        public double chance = 0.25;       // probabilidad por intervalo y por jugador
        public int maxNearby = 6;          // máximo cerca del jugador (96 bloques)
        public int minGroup = 1;
        public int maxGroup = 3;
        public boolean forcePlacement = false; // ignora reglas vanilla (ej. ahogado en tierra)
        public boolean requireDark = false;
        public boolean enabled = true;
    }

    // ---------------- DAÑO DE MOBS ----------------
    public boolean damageEnabled = true;
    public double globalMobDamageMultiplier = 1.0;
    public Map<String, Double> damageMultipliers = new LinkedHashMap<>();

    // ---------------- REGLAS EXTREMAS ----------------
    /** ids de reglas extra activadas (ver RuleDefs) */
    public Set<String> rules = new LinkedHashSet<>();
    public boolean noSaplingDrops = false;
    public boolean noSaplingPlanting = false;
    public boolean extremeDamageEnabled = false;
    public boolean extremeAffectCreative = false;
    public Map<String, Double> useDamage = new LinkedHashMap<>();   // bloques: click derecho
    public Map<String, Double> standDamage = new LinkedHashMap<>(); // bloques: pisar
    public Map<String, Double> breakDamage = new LinkedHashMap<>(); // bloques: romper
    public Map<String, Double> placeDamage = new LinkedHashMap<>(); // bloques: colocar
    public Map<String, Double> itemDamage = new LinkedHashMap<>();  // items: usar

    // ---------------- RECETAS / BLOQUES PROTEGIDOS ----------------
    /** Items que NO se pueden craftear en mesa de crafteo (siguen existiendo por otros medios) */
    public Set<String> blockedRecipes = new LinkedHashSet<>();
    /** Bloques que nadie (sin bypass) puede romper */
    public Set<String> protectedBlocks = new LinkedHashSet<>();

    // ---------------- EVENTO (reparto de jugadores) ----------------
    public Set<String> eventHosts = new LinkedHashSet<>();
    public Set<String> eventExcluded = new LinkedHashSet<>();
    public int eventRadius = 8;
    public int eventCountdown = 5;

    // ---------------- VANISH ----------------
    public boolean vanishVisibleToOps = true;
    public Set<String> vanished = new LinkedHashSet<>();
    public boolean vanishIgnoredByMobs = true;
    public boolean vanishNoPickup = true;

    // ---------------- IO ----------------
    public static Config load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                Config c = GSON.fromJson(r, Config.class);
                if (c != null) { c.fix(); return c; }
            } catch (Exception e) {
                AdminControl.LOGGER.error("No se pudo leer admincontrol.json, usando valores por defecto", e);
            }
        }
        Config c = new Config();
        c.save();
        return c;
    }

    /** Se incrementa en cada guardado: sirve para invalidar cachés. */
    public static volatile int REV = 0;

    public synchronized void save() {
        REV++;
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            AdminControl.LOGGER.error("No se pudo guardar admincontrol.json", e);
        }
    }

    public void normalize() { fix(); }

    private void fix() {
        if (blockedItems == null) blockedItems = new LinkedHashSet<>();
        if (playerExceptions == null) playerExceptions = new LinkedHashMap<>();
        if (fullBypass == null) fullBypass = new LinkedHashSet<>();
        if (names == null) names = new LinkedHashMap<>();
        if (blockedSpawns == null) blockedSpawns = new LinkedHashSet<>();
        if (blockedSpawnBiomes == null) blockedSpawnBiomes = new LinkedHashMap<>();
        if (spawnMultipliers == null) spawnMultipliers = new LinkedHashMap<>();
        if (extraSpawns == null) extraSpawns = new ArrayList<>();
        if (damageMultipliers == null) damageMultipliers = new LinkedHashMap<>();
        if (useDamage == null) useDamage = new LinkedHashMap<>();
        if (standDamage == null) standDamage = new LinkedHashMap<>();
        if (breakDamage == null) breakDamage = new LinkedHashMap<>();
        if (placeDamage == null) placeDamage = new LinkedHashMap<>();
        if (itemDamage == null) itemDamage = new LinkedHashMap<>();
        if (vanished == null) vanished = new LinkedHashSet<>();
        if (rules == null) rules = new LinkedHashSet<>();
        if (blockedRecipes == null) blockedRecipes = new LinkedHashSet<>();
        if (protectedBlocks == null) protectedBlocks = new LinkedHashSet<>();
        if (eventHosts == null) eventHosts = new LinkedHashSet<>();
        if (eventExcluded == null) eventExcluded = new LinkedHashSet<>();
    }

    public String nameOf(String uuid) {
        return names.getOrDefault(uuid, uuid);
    }
}
