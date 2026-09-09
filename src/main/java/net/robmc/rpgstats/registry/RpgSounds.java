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
    public static final RegistryObject<SoundEvent> SUNDER = reg("spell.sunder");
    public static final RegistryObject<SoundEvent> HEAL_OTHER = reg("spell.heal_other");
    public static final RegistryObject<SoundEvent> AWAY = reg("spell.away");
    public static final RegistryObject<SoundEvent> WARD = reg("spell.ward");
    public static final RegistryObject<SoundEvent> BRIGHT_LIGHT = reg("spell.bright_light");
    public static final RegistryObject<SoundEvent> EMBER_DART = reg("spell.ember_dart");
    public static final RegistryObject<SoundEvent> SERPENTS_PLUME = reg("spell.serpents_plume");
    public static final RegistryObject<SoundEvent> SUNBURST = reg("spell.sunburst");
    public static final RegistryObject<SoundEvent> PYROCLASM = reg("spell.pyroclasm");
    // Cinder Maelstrom has no custom sfx - it uses a vanilla cast boom + a looping fire crackle from FireFieldManager.
    public static final RegistryObject<SoundEvent> HEARTWELL = reg("spell.heartwell");
    public static final RegistryObject<SoundEvent> WITHER = reg("spell.wither");
    public static final RegistryObject<SoundEvent> SLUMP = reg("spell.slump");
    public static final RegistryObject<SoundEvent> PESTILENCE = reg("spell.pestilence");
    public static final RegistryObject<SoundEvent> HEXDRAIN = reg("spell.hexdrain");

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
            case SUNDER -> SUNDER.get();
            case HEAL_OTHER -> HEAL_OTHER.get();
            case AWAY -> AWAY.get();
            case WARD -> WARD.get();
            case BRIGHT_LIGHT -> BRIGHT_LIGHT.get();
            case EMBER_DART -> EMBER_DART.get();
            case SERPENTS_PLUME -> SERPENTS_PLUME.get();
            case SUNBURST -> SUNBURST.get();
            case PYROCLASM -> PYROCLASM.get();
            case CINDER_MAELSTROM -> net.minecraft.sounds.SoundEvents.FIRECHARGE_USE; // vanilla cast boom
            case HEARTWELL -> HEARTWELL.get();
            case WITHER -> WITHER.get();
            case SLUMP -> SLUMP.get();
            case PESTILENCE -> PESTILENCE.get();
            case HEXDRAIN -> HEXDRAIN.get();
        };
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
