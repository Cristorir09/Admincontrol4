package com.admincontrol.gui;

import com.admincontrol.AdminControl;
import com.admincontrol.Matchers;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Set;

/** Clasifica items en categorías (como el creativo) usando clases de item y nombres, así funciona con mods. */
public final class Categories {
    private Categories() {}

    private static final Set<String> REDSTONE = Set.of("redstone", "repeater", "comparator", "piston", "sticky_piston",
            "observer", "dropper", "dispenser", "hopper", "lever", "tnt", "redstone_torch", "redstone_lamp",
            "redstone_block", "target", "note_block", "daylight_detector", "tripwire_hook", "slime_block",
            "honey_block", "crafter", "lightning_rod", "sculk_sensor", "calibrated_sculk_sensor", "trapped_chest",
            "tnt_minecart", "hopper_minecart", "string", "bell");

    private static final String[] DECOR = {"flower", "poppy", "dandelion", "orchid", "allium", "azure_bluet", "tulip",
            "oxeye", "cornflower", "lily", "rose_bush", "peony", "lilac", "sunflower", "torchflower", "pitcher",
            "carpet", "banner", "_bed", "candle", "sign", "skull", "_head", "pot", "painting", "frame", "lantern",
            "torch", "coral", "vine", "fern", "bush", "leaves", "sapling", "propagule", "mushroom", "azalea", "moss",
            "sea_pickle", "cobweb", "short_grass", "tall_grass", "kelp", "seagrass", "dripleaf", "glow_lichen",
            "hanging_roots", "spore_blossom", "amethyst_cluster", "campfire", "chain", "end_rod", "sculk_vein"};

    private static final Set<String> TOOL_NAMES = Set.of("compass", "clock", "spyglass", "brush", "lead", "name_tag",
            "carrot_on_a_stick", "warped_fungus_on_a_stick", "recovery_compass");

    private static boolean redstone(String path) {
        return REDSTONE.contains(path) || path.endsWith("_button") || path.endsWith("_pressure_plate")
                || path.endsWith("rail") || path.contains("redstone");
    }

    private static boolean decor(String path) {
        for (String k : DECOR) if (path.contains(k)) return true;
        return false;
    }

    private static boolean combat(Item i) {
        return i instanceof SwordItem || i instanceof RangedWeaponItem || i instanceof TridentItem
                || i instanceof ShieldItem || i instanceof MaceItem || i instanceof ArrowItem
                || i == Items.TOTEM_OF_UNDYING;
    }

    private static boolean tool(Item i, String path) {
        return i instanceof MiningToolItem || i instanceof ShearsItem || i instanceof FishingRodItem
                || i instanceof FlintAndSteelItem || i instanceof BucketItem || TOOL_NAMES.contains(path);
    }

    public static boolean blockedMatch(Item item) {
        return blockedMatch(item, AdminControl.CFG);
    }

    public static boolean blockedMatch(Item item, com.admincontrol.Config cfg) {
        ItemStack s = new ItemStack(item);
        for (String pat : cfg.blockedItems) if (Matchers.item(s, pat)) return true;
        return false;
    }

    public static boolean has(Identifier id, String cat) {
        return has(id, cat, AdminControl.CFG);
    }

    public static boolean has(Identifier id, String cat, com.admincontrol.Config cfg) {
        if (cat.equals("all")) return true;
        Item item = Registries.ITEM.get(id);
        if (item == Items.AIR) return false;
        String path = id.getPath();
        boolean block = item instanceof BlockItem;
        boolean egg = item instanceof SpawnEggItem;
        boolean armor = item instanceof ArmorItem;
        boolean food = item.getComponents().contains(DataComponentTypes.FOOD);
        boolean combat = combat(item);
        boolean tool = tool(item, path) && !combat;
        return switch (cat) {
            case "blocked" -> blockedMatch(item, cfg);
            case "eggs" -> egg;
            case "armor" -> armor;
            case "food" -> food && !block;
            case "combat" -> combat;
            case "tools" -> tool;
            case "redstone" -> redstone(path);
            case "decor" -> block && decor(path) && !redstone(path);
            case "building" -> block && !decor(path) && !redstone(path);
            case "materials" -> !block && !egg && !armor && !food && !combat && !tool;
            default -> false;
        };
    }
}
