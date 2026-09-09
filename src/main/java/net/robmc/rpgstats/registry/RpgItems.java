package net.robmc.rpgstats.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.item.SkillItem;
import net.robmc.rpgstats.skill.Skill;

/** General-skill items - held in the hotbar, right-click to use. */
public final class RpgItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RpgStats.MOD_ID);

    public static final RegistryObject<Item> SKILL_REST = ITEMS.register(
            "skill_rest", () -> new SkillItem(Skill.REST, new Item.Properties()));

    public static final RegistryObject<Item> SKILL_RECALL = ITEMS.register(
            "skill_recall", () -> new SkillItem(Skill.RECALL, new Item.Properties()));

    private RpgItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
