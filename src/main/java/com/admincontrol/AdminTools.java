package com.admincontrol;

import com.admincontrol.gui.Icons;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Objetos especiales (sobre items vanilla, así los clientes no necesitan el mod). */
public final class AdminTools {
    private AdminTools() {}

    private static final String KEY = "admincontrol_tool";

    public static ItemStack respawnStick() {
        ItemStack s = new ItemStack(Items.STICK);
        Icons.name(s, "§dBastón de Retorno");
        Icons.lore(s, "§7Golpea a un jugador para enviarlo", "§7a su último punto de reaparición.");
        Icons.glint(s);
        mark(s, "respawn_stick");
        return s;
    }

    public static ItemStack menuWand() {
        ItemStack s = new ItemStack(Items.BLAZE_ROD);
        Icons.name(s, "§6Varita de Admin");
        Icons.lore(s, "§7Clic derecho: abre el menú de Admin Control.");
        Icons.glint(s);
        mark(s, "menu_wand");
        return s;
    }

    private static void mark(ItemStack s, String type) {
        NbtCompound n = new NbtCompound();
        n.putString(KEY, type);
        s.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(n));
    }

    public static String type(ItemStack s) {
        if (s.isEmpty()) return "";
        NbtComponent c = s.get(DataComponentTypes.CUSTOM_DATA);
        return c == null ? "" : c.copyNbt().getString(KEY);
    }

    public static void give(ServerPlayerEntity p, ItemStack s) {
        p.getInventory().offerOrDrop(s);
    }

    /** Envía al jugador a su cama/ancla de reaparición (o al spawn del mundo), con sonido y partículas. */
    public static void sendToSpawn(ServerPlayerEntity admin, ServerPlayerEntity t) {
        MinecraftServer server = t.getServer();
        if (server == null) return;
        ServerWorld world;
        BlockPos pos;
        BlockPos sp = t.getSpawnPointPosition();
        if (sp != null) {
            world = server.getWorld(t.getSpawnPointDimension());
            if (world == null) world = server.getOverworld();
            pos = sp;
        } else {
            world = server.getOverworld();
            pos = world.getSpawnPos();
        }
        Vec3d dest = safeSpot(world, pos);

        // sonido + partículas en el origen
        ServerWorld from = (ServerWorld) t.getWorld();
        from.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
        from.spawnParticles(ParticleTypes.PORTAL, t.getX(), t.getY() + 1, t.getZ(), 30, 0.3, 0.5, 0.3, 0.1);

        t.teleport(world, dest.x, dest.y, dest.z, t.getYaw(), 0f);
        t.fallDistance = 0;

        // sonido en el destino
        world.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.2f);
        world.spawnParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1, dest.z, 30, 0.3, 0.5, 0.3, 0.1);

        t.sendMessage(Text.literal("§d¡Fuiste enviado a tu punto de reaparición!"), true);
        admin.sendMessage(Text.literal("§aEnviaste a " + t.getName().getString() + " a su punto de reaparición."), true);
        admin.getItemCooldownManager().set(Items.STICK, 10);
    }

    private static boolean standable(ServerWorld w, BlockPos p) {
        BlockPos above = p.up();
        BlockPos below = p.down();
        return w.getBlockState(p).getCollisionShape(w, p).isEmpty()
                && w.getBlockState(above).getCollisionShape(w, above).isEmpty()
                && w.getFluidState(p).isEmpty()
                && !w.getBlockState(below).getCollisionShape(w, below).isEmpty();
    }

    private static Vec3d safeSpot(ServerWorld w, BlockPos base) {
        for (int dy = 0; dy < 16; dy++) {
            BlockPos p = base.up(dy);
            if (standable(w, p)) return new Vec3d(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        }
        return new Vec3d(base.getX() + 0.5, base.getY() + 1, base.getZ() + 0.5);
    }

    /** Punto aleatorio y seguro dentro de un círculo alrededor de center (reparto uniforme). */
    public static Vec3d scatterAround(ServerWorld w, BlockPos center, int radius, java.util.Random rng) {
        for (int i = 0; i < 24; i++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double dist = radius <= 0 ? 0 : Math.sqrt(rng.nextDouble()) * radius;
            int x = center.getX() + (int) Math.round(Math.cos(ang) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(ang) * dist);
            for (int dy = 3; dy >= -6; dy--) {
                BlockPos p = new BlockPos(x, center.getY() + dy, z);
                if (standable(w, p)) return new Vec3d(x + 0.5, p.getY(), z + 0.5);
            }
        }
        return new Vec3d(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
    }
}
