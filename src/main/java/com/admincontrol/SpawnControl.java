package com.admincontrol;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SpawnControl {
    private SpawnControl() {}

    // ------------------------------------------------------------------
    // Spawn natural vanilla (llamado desde el mixin de SpawnHelper)
    // ------------------------------------------------------------------
    public static boolean shouldBlock(ServerWorld world, BlockPos pos, EntityType<?> type) {
        Config c = AdminControl.CFG;
        if (!c.spawnEnabled) return false;

        for (String p : c.blockedSpawns) {
            if (Matchers.entity(type, p)) return true;
        }
        if (!c.blockedSpawnBiomes.isEmpty()) {
            var biome = world.getBiome(pos);
            for (Map.Entry<String, Set<String>> e : c.blockedSpawnBiomes.entrySet()) {
                if (!Matchers.entity(type, e.getKey())) continue;
                for (String bp : e.getValue()) {
                    if (Matchers.biome(biome, bp)) return true;
                }
            }
        }
        Double m = Matchers.lookupEntity(c.spawnMultipliers, type);
        if (m != null && m < 1.0) {
            return world.getRandom().nextDouble() >= m;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Spawner extra: aparición en biomas nuevos y aumento de spawn
    // ------------------------------------------------------------------
    public static void tick(MinecraftServer server) {
        Config c = AdminControl.CFG;
        if (!c.spawnEnabled) return;
        int interval = Math.max(5, c.spawnerIntervalTicks);
        if (server.getTicks() % interval != 0) return;

        List<Config.SpawnRule> rules = new ArrayList<>();
        for (Config.SpawnRule r : c.extraSpawns) {
            if (r.enabled && r.entity != null) rules.add(r);
        }
        for (Map.Entry<String, Double> e : c.spawnMultipliers.entrySet()) {
            double m = e.getValue();
            String k = e.getKey();
            if (m > 1.0 && !k.contains("*") && !k.startsWith("#")) {
                Config.SpawnRule r = new Config.SpawnRule();
                r.entity = k;
                r.biomes = List.of("*");
                r.chance = Math.min(1.0, (m - 1.0) * 0.3);
                r.maxNearby = (int) Math.round(6 * m);
                r.minGroup = 1;
                r.maxGroup = 2;
                rules.add(r);
            }
        }
        if (rules.isEmpty()) return;

        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.isSpectator()) continue;
                for (Config.SpawnRule r : rules) {
                    try {
                        attempt(world, p, r);
                    } catch (Exception ex) {
                        AdminControl.LOGGER.warn("Error en regla de spawn {}: {}", r.entity, ex.toString());
                    }
                }
            }
        }
    }

    private static void attempt(ServerWorld world, ServerPlayerEntity p, Config.SpawnRule r) {
        Identifier id = Identifier.tryParse(r.entity);
        if (id == null) return;
        Optional<EntityType<?>> opt = Registries.ENTITY_TYPE.getOrEmpty(id);
        if (opt.isEmpty()) return;
        EntityType<?> type = opt.get();
        if (!type.isSummonable()) return;
        if (world.getRandom().nextDouble() >= r.chance) return;

        Box box = p.getBoundingBox().expand(96);
        if (count(world, type, box) >= r.maxNearby) return;

        int span = Math.max(1, r.maxGroup - r.minGroup + 1);
        int group = Math.max(1, r.minGroup) + world.getRandom().nextInt(span);

        for (int tries = 0; tries < 10; tries++) {
            BlockPos base = randomPos(world, p);
            if (base == null || !biomeOk(world, base, r)) continue;
            if (SpawnControl.shouldBlock(world, base, type)) return;
            int spawned = 0;
            for (int i = 0; i < group; i++) {
                BlockPos pos = base.add(world.getRandom().nextInt(7) - 3, 0, world.getRandom().nextInt(7) - 3);
                if (canPlace(world, type, pos, r)) {
                    Entity e = type.spawn(world, pos, SpawnReason.NATURAL);
                    if (e != null) spawned++;
                }
            }
            if (spawned > 0) return;
        }
    }

    private static <T extends Entity> int count(ServerWorld world, EntityType<T> type, Box box) {
        return world.getEntitiesByType(type, box, e -> true).size();
    }

    private static BlockPos randomPos(ServerWorld world, ServerPlayerEntity p) {
        double ang = world.getRandom().nextDouble() * Math.PI * 2;
        double dist = 24 + world.getRandom().nextDouble() * 40;
        int x = MathHelper.floor(p.getX() + Math.cos(ang) * dist);
        int z = MathHelper.floor(p.getZ() + Math.sin(ang) * dist);
        if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return null;
        int y;
        if (world.getRandom().nextBoolean()) {
            y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        } else {
            y = p.getBlockY() + world.getRandom().nextInt(25) - 12;
        }
        y = MathHelper.clamp(y, world.getBottomY() + 1, world.getTopY() - 3);
        return new BlockPos(x, y, z);
    }

    private static boolean biomeOk(ServerWorld world, BlockPos pos, Config.SpawnRule r) {
        if (r.biomes == null || r.biomes.isEmpty()) return true;
        var biome = world.getBiome(pos);
        for (String b : r.biomes) {
            if (b.equals("*") || Matchers.biome(biome, b)) return true;
        }
        return false;
    }

    private static boolean canPlace(ServerWorld world, EntityType<?> type, BlockPos pos, Config.SpawnRule r) {
        if (pos.getY() <= world.getBottomY() || pos.getY() >= world.getTopY() - 2) return false;
        if (!world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;
        if (world.getClosestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 16, false) != null) return false;
        if (!world.isSpaceEmpty(type.getDimensions().getBoxAt(Vec3d.ofBottomCenter(pos)))) return false;
        if (r.requireDark && world.getLightLevel(pos) > 7) return false;
        if (r.forcePlacement) {
            BlockPos below = pos.down();
            return world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)
                    && world.getFluidState(pos).isEmpty();
        }
        return SpawnRestriction.isSpawnPosAllowed(type, world, pos)
                && SpawnRestriction.canSpawn(type, world, SpawnReason.NATURAL, pos, world.getRandom());
    }
}
