package net.robmc.rpgstats.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.magic.MagicBoltEntity;

public class RpgEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RpgStats.MOD_ID);

    public static final RegistryObject<EntityType<MagicBoltEntity>> MAGIC_BOLT = ENTITIES.register(
            "magic_bolt",
            () -> EntityType.Builder.<MagicBoltEntity>of(MagicBoltEntity::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f)
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .build("magic_bolt"));

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }
}
