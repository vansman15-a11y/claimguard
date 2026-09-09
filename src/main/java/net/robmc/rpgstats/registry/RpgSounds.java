package net.robmc.rpgstats.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.magic.Spell;

/** Custom spell sounds. Files live in assets/rpgstats/sounds/spell/, wired up by sounds.json. */
public final class RpgSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, RpgStats.MOD_ID);

    public static final RegistryObject<SoundEvent> MANA_TO_STAMINA = reg("spell.mana_to_stamina");
    public static final RegistryObject<SoundEvent> STAMINA_TO_HEALTH = reg("spell.stamina_to_health");
    public static final RegistryObject<SoundEvent> HEALTH_TO_MANA = reg("spell.health_to_mana");
    public static final RegistryObject<SoundEvent> MAGIC_BOLT = reg("spell.magic_bolt");

    private RpgSounds() {
    }

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(RpgStats.MOD_ID, name)));
    }

    /** The effect sound that plays when the given spell resolves (null = let the caller pick a vanilla one). */
    public static SoundEvent forSpell(Spell spell) {
        return switch (spell) {
            case MANA_TO_STAMINA -> MANA_TO_STAMINA.get();
            case STAMINA_TO_HEALTH -> STAMINA_TO_HEALTH.get();
            case HEALTH_TO_MANA -> HEALTH_TO_MANA.get();
            case MAGIC_BOLT -> MAGIC_BOLT.get();
            default -> MAGIC_BOLT.get();
        };
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
