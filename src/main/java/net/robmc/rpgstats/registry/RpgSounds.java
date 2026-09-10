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
    public static final RegistryObject<SoundEvent> LIGHTNING_STRIKE = reg("spell.lightning_strike");
    public static final RegistryObject<SoundEvent> SPEED_OF_WIND = reg("spell.speed_of_wind");
    public static final RegistryObject<SoundEvent> CHAIN_SHOCK = reg("spell.chain_shock");
    public static final RegistryObject<SoundEvent> WIND_LURE = reg("spell.wind_lure");
    public static final RegistryObject<SoundEvent> HOWLING_IMPACT = reg("spell.howling_impact");
    public static final RegistryObject<SoundEvent> WATER_BREATHING = reg("spell.water_breathing");
    public static final RegistryObject<SoundEvent> ICE_WALL = reg("spell.ice_wall");
    public static final RegistryObject<SoundEvent> WATER_ORB = reg("spell.water_orb");
    public static final RegistryObject<SoundEvent> WATER_SPOUT = reg("spell.water_spout");
    public static final RegistryObject<SoundEvent> RIPTIDE = reg("spell.riptide");
    public static final RegistryObject<SoundEvent> EARTHEN_PATH = reg("spell.earthen_path");
    public static final RegistryObject<SoundEvent> SEISMIC_PILLAR = reg("spell.seismic_pillar");
    public static final RegistryObject<SoundEvent> QUAKE_STOMP = reg("spell.quake_stomp");
    public static final RegistryObject<SoundEvent> BARK_SKIN = reg("spell.bark_skin");
    public static final RegistryObject<SoundEvent> FISSURE = reg("spell.fissure");
    public static final RegistryObject<SoundEvent> BONE_SPEAR = reg("spell.bone_spear");
    public static final RegistryObject<SoundEvent> RAISE_MINION = reg("spell.raise_minion");
    public static final RegistryObject<SoundEvent> SOUL_DRAIN = reg("spell.soul_drain");
    public static final RegistryObject<SoundEvent> EYE_DECAY = reg("spell.eye_decay");
    public static final RegistryObject<SoundEvent> WRAITH_STEP = reg("spell.wraith_step");
    public static final RegistryObject<SoundEvent> CHANT_OF_GROWTH = reg("spell.chant_of_growth");
    public static final RegistryObject<SoundEvent> BATTLE_HYMN = reg("spell.battle_hymn");
    public static final RegistryObject<SoundEvent> WORD_OF_UNMAKING = reg("spell.word_of_unmaking");
    public static final RegistryObject<SoundEvent> FEARCRAFT = reg("spell.fearcraft");
    public static final RegistryObject<SoundEvent> SILENCING_WHISPER = reg("spell.silencing_whisper");

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
            case LIGHTNING_STRIKE -> LIGHTNING_STRIKE.get();
            case SPEED_OF_WIND -> SPEED_OF_WIND.get();
            case CHAIN_SHOCK -> CHAIN_SHOCK.get();
            case WIND_LURE -> WIND_LURE.get();
            case HOWLING_IMPACT -> HOWLING_IMPACT.get();
            case WATER_BREATHING -> WATER_BREATHING.get();
            case ICE_WALL -> ICE_WALL.get();
            case WATER_ORB -> WATER_ORB.get();
            case WATER_SPOUT -> WATER_SPOUT.get();
            case RIPTIDE -> RIPTIDE.get();
            case EARTHEN_PATH -> EARTHEN_PATH.get();
            case SEISMIC_PILLAR -> SEISMIC_PILLAR.get();
            case QUAKE_STOMP -> QUAKE_STOMP.get();
            case BARK_SKIN -> BARK_SKIN.get();
            case FISSURE -> FISSURE.get();
            case BONE_SPEAR -> BONE_SPEAR.get();
            case RAISE_MINION -> RAISE_MINION.get();
            case SOUL_DRAIN -> SOUL_DRAIN.get();
            case EYE_DECAY -> EYE_DECAY.get();
            case WRAITH_STEP -> WRAITH_STEP.get();
            case CHANT_OF_GROWTH -> CHANT_OF_GROWTH.get();
            case BATTLE_HYMN -> BATTLE_HYMN.get();
            case WORD_OF_UNMAKING -> WORD_OF_UNMAKING.get();
            case FEARCRAFT -> FEARCRAFT.get();
            case SILENCING_WHISPER -> SILENCING_WHISPER.get();
        };
    }

    public static void register(IEventBus modEventBus) {
        SOUNDS.register(modEventBus);
    }
}
