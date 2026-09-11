package net.robmc.rpgstats.registry;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.item.DaggerItem;
import net.robmc.rpgstats.item.PolearmItem;
import net.robmc.rpgstats.item.RpgArmorMaterials;
import net.robmc.rpgstats.item.SkillItem;
import net.robmc.rpgstats.item.StaffItem;
import net.robmc.rpgstats.skill.Skill;

// RpgBlocks is in this same registry package, no import needed

/** RPG items - general-skill items plus the custom weapons. */
public final class RpgItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RpgStats.MOD_ID);

    public static final RegistryObject<Item> SKILL_REST = ITEMS.register(
            "skill_rest", () -> new SkillItem(Skill.REST, new Item.Properties()));

    public static final RegistryObject<Item> SKILL_RECALL = ITEMS.register(
            "skill_recall", () -> new SkillItem(Skill.RECALL, new Item.Properties()));

    public static final RegistryObject<Item> DAGGER = ITEMS.register(
            "dagger", () -> new DaggerItem(new Item.Properties().durability(560)));

    public static final RegistryObject<Item> POLEARM = ITEMS.register(
            "polearm", () -> new PolearmItem(new Item.Properties().durability(680)));

    public static final RegistryObject<Item> STAFF = ITEMS.register(
            "staff", () -> new StaffItem(new Item.Properties().durability(400)));

    public static final RegistryObject<Item> COBRA_STAFF = ITEMS.register(
            "cobra_staff", () -> new StaffItem(new Item.Properties().durability(440)));

    public static final RegistryObject<Item> BLACKBOLT_STAFF = ITEMS.register(
            "blackbolt_staff", () -> new StaffItem(new Item.Properties().durability(440)));

    // --- Dragon armour (Netherite-grade stats, fire resistant) ---
    public static final RegistryObject<Item> DRAGON_HELMET = armor("dragon_helmet", RpgArmorMaterials.DRAGON, ArmorItem.Type.HELMET, true);
    public static final RegistryObject<Item> DRAGON_CHESTPLATE = armor("dragon_chestplate", RpgArmorMaterials.DRAGON, ArmorItem.Type.CHESTPLATE, true);
    public static final RegistryObject<Item> DRAGON_LEGGINGS = armor("dragon_leggings", RpgArmorMaterials.DRAGON, ArmorItem.Type.LEGGINGS, true);
    public static final RegistryObject<Item> DRAGON_BOOTS = armor("dragon_boots", RpgArmorMaterials.DRAGON, ArmorItem.Type.BOOTS, true);

    // --- Infernal armour (fire resistant) ---
    public static final RegistryObject<Item> INFERNAL_HELMET = armor("infernal_helmet", RpgArmorMaterials.INFERNAL, ArmorItem.Type.HELMET, true);
    public static final RegistryObject<Item> INFERNAL_CHESTPLATE = armor("infernal_chestplate", RpgArmorMaterials.INFERNAL, ArmorItem.Type.CHESTPLATE, true);
    public static final RegistryObject<Item> INFERNAL_LEGGINGS = armor("infernal_leggings", RpgArmorMaterials.INFERNAL, ArmorItem.Type.LEGGINGS, true);
    public static final RegistryObject<Item> INFERNAL_BOOTS = armor("infernal_boots", RpgArmorMaterials.INFERNAL, ArmorItem.Type.BOOTS, true);

    // --- Full Plate armour - sits above Netherite, below Infernal ---
    public static final RegistryObject<Item> FULL_PLATE_HELMET = armor("full_plate_helmet", RpgArmorMaterials.FULL_PLATE, ArmorItem.Type.HELMET, false);
    public static final RegistryObject<Item> FULL_PLATE_CHESTPLATE = armor("full_plate_chestplate", RpgArmorMaterials.FULL_PLATE, ArmorItem.Type.CHESTPLATE, false);
    public static final RegistryObject<Item> FULL_PLATE_LEGGINGS = armor("full_plate_leggings", RpgArmorMaterials.FULL_PLATE, ArmorItem.Type.LEGGINGS, false);
    public static final RegistryObject<Item> FULL_PLATE_BOOTS = armor("full_plate_boots", RpgArmorMaterials.FULL_PLATE, ArmorItem.Type.BOOTS, false);

    // --- Rare ore materials (RareOres.zip) - not generated in the world yet ---
    public static final RegistryObject<Item> SELENTINE = ITEMS.register("selentine", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LEENSPAR = ITEMS.register("leenspar", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> NEITHAL = ITEMS.register("neithal", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> VEILRON = ITEMS.register("veilron", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> THEYRIL = ITEMS.register("theyril", () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SELENTINE_ORE = ITEMS.register("selentine_ore",
            () -> new BlockItem(RpgBlocks.SELENTINE_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> LEENSPAR_ORE = ITEMS.register("leenspar_ore",
            () -> new BlockItem(RpgBlocks.LEENSPAR_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> NEITHAL_ORE = ITEMS.register("neithal_ore",
            () -> new BlockItem(RpgBlocks.NEITHAL_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> VEILRON_ORE = ITEMS.register("veilron_ore",
            () -> new BlockItem(RpgBlocks.VEILRON_ORE.get(), new Item.Properties()));
    public static final RegistryObject<Item> THEYRIL_ORE = ITEMS.register("theyril_ore",
            () -> new BlockItem(RpgBlocks.THEYRIL_ORE.get(), new Item.Properties()));

    private static RegistryObject<Item> armor(String name, RpgArmorMaterials material, ArmorItem.Type type, boolean fireResistant) {
        return ITEMS.register(name, () -> {
            Item.Properties props = new Item.Properties();
            if (fireResistant) {
                props = props.fireResistant();
            }
            return new ArmorItem(material, type, props);
        });
    }

    private RpgItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
