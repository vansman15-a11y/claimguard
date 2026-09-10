package net.robmc.rpgstats.magic;

/**
 * Every built-in spell. Each belongs to a {@link School} and a tier (1-5) within
 * it; the tier decides the school level you need before you can cast it (see
 * {@link School#unlockLevel(int)}).
 */
public enum Spell {

    // --- Weak Magic (tiers 1-4, all usable from the start) ---
    MANA_TO_STAMINA("Transfer: Mana → Stamina", School.WEAK, 1, Kind.TRANSFER, Pool.MANA, Pool.STAMINA, 0, 40, 120),
    STAMINA_TO_HEALTH("Transfer: Stamina → Health", School.WEAK, 2, Kind.TRANSFER, Pool.STAMINA, Pool.HEALTH, 0, 40, 120),
    HEALTH_TO_MANA("Transfer: Health → Mana", School.WEAK, 3, Kind.TRANSFER, Pool.HEALTH, Pool.MANA, 0, 40, 120),
    MAGIC_BOLT("Magic Bolt", School.WEAK, 4, Kind.MAGIC_BOLT, Pool.MANA, null, 20, 36, 30),

    // --- Adept Magic ---
    SUNDER("Sunder", School.ADEPT, 1, Kind.SUNDER, Pool.MANA, null, 16, 16, 70),
    HEAL_OTHER("Heal Other", School.ADEPT, 2, Kind.HEAL_OTHER, Pool.MANA, null, 34, 30, 80),
    AWAY("Away", School.ADEPT, 3, Kind.AWAY, Pool.MANA, null, 16, 14, 400),
    WARD("Ward", School.ADEPT, 4, Kind.WARD, Pool.MANA, null, 20, 2, 300),
    BRIGHT_LIGHT("Bright Light", School.ADEPT, 5, Kind.BRIGHT_LIGHT, Pool.MANA, null, 40, 24, 300),

    // --- Fire Magic (the first advanced school - end-game mage, higher cost & damage) ---
    EMBER_DART("Ember Dart", School.FIRE, 1, Kind.EMBER_DART, Pool.MANA, null, 9, 40, 40),
    SERPENTS_PLUME("Serpent's Plume", School.FIRE, 2, Kind.SERPENTS_PLUME, Pool.MANA, null, 12, 2, 200),
    SUNBURST("Sunburst", School.FIRE, 3, Kind.SUNBURST, Pool.MANA, null, 10, 38, 100),
    PYROCLASM("Pyroclasm", School.FIRE, 4, Kind.PYROCLASM, Pool.MANA, null, 9, 60, 200),
    CINDER_MAELSTROM("Cinder Maelstrom", School.FIRE, 5, Kind.CINDER_MAELSTROM, Pool.MANA, null, 16, 50, 900),

    // --- Chaos Magic (debuff school with one very strong heal) ---
    HEARTWELL("Heartwell", School.CHAOS, 1, Kind.HEARTWELL, Pool.MANA, null, 12, 20, 400),
    WITHER("Wither", School.CHAOS, 2, Kind.WITHER, Pool.MANA, null, 10, 26, 400),
    SLUMP("Slump", School.CHAOS, 3, Kind.SLUMP, Pool.MANA, null, 15, 26, 300),
    PESTILENCE("Pestilence", School.CHAOS, 4, Kind.PESTILENCE, Pool.MANA, null, 10, 2, 200),
    HEXDRAIN("Hexdrain", School.CHAOS, 5, Kind.HEXDRAIN, Pool.MANA, null, 40, 30, 1200),

    // --- Air Magic (lightning & wind - mobility, chains, displacement) ---
    LIGHTNING_STRIKE("Lightning Strike", School.AIR, 1, Kind.LIGHTNING_STRIKE, Pool.MANA, null, 8, 14, 50),
    SPEED_OF_WIND("Speed of Wind", School.AIR, 2, Kind.SPEED_OF_WIND, Pool.MANA, null, 10, 30, 60),
    CHAIN_SHOCK("Chain Shock", School.AIR, 3, Kind.CHAIN_SHOCK, Pool.MANA, null, 13, 2, 120),
    WIND_LURE("Wind Lure", School.AIR, 4, Kind.WIND_LURE, Pool.MANA, null, 9, 30, 160),
    HOWLING_IMPACT("Howling Impact", School.AIR, 5, Kind.HOWLING_IMPACT, Pool.MANA, null, 12, 50, 600),

    // --- Water Magic (breathing, ice, sustained beams, big waves) ---
    WATER_BREATHING("Water Breathing", School.WATER, 1, Kind.WATER_BREATHING, Pool.MANA, null, 8, 14, 200),
    ICE_WALL("Ice Wall", School.WATER, 2, Kind.ICE_WALL, Pool.MANA, null, 16, 50, 1800),
    WATER_ORB("Water Orb", School.WATER, 3, Kind.WATER_ORB, Pool.MANA, null, 9, 60, 100),
    WATER_SPOUT("Water Spout", School.WATER, 4, Kind.WATER_SPOUT, Pool.MANA, null, 10, 2, 40),
    RIPTIDE("Riptide", School.WATER, 5, Kind.RIPTIDE, Pool.MANA, null, 25, 80, 400),

    // --- Earth Magic (stone, terrain, displacement) ---
    EARTHEN_PATH("Earthen Path", School.EARTH, 1, Kind.EARTHEN_PATH, Pool.MANA, null, 8, 40, 80),
    SEISMIC_PILLAR("Seismic Pillar", School.EARTH, 2, Kind.SEISMIC_PILLAR, Pool.MANA, null, 15, 50, 1200),
    QUAKE_STOMP("Quake Stomp", School.EARTH, 3, Kind.QUAKE_STOMP, Pool.MANA, null, 20, 80, 1800),
    BARK_SKIN("Bark Skin", School.EARTH, 4, Kind.BARK_SKIN, Pool.MANA, null, 14, 40, 1200),
    FISSURE("Fissure", School.EARTH, 5, Kind.FISSURE, Pool.MANA, null, 20, 80, 2400),

    // --- Arcana (starlight - burst damage, light, teleports, control) ---
    STARLANCE("Starlance", School.ARCANA, 1, Kind.STARLANCE, Pool.MANA, null, 10, 40, 100),
    ILLUMINATE_VISION("Illuminate Vision", School.ARCANA, 2, Kind.ILLUMINATE_VISION, Pool.MANA, null, 20, 80, 200),
    LUMINOUS_PHASE("Luminous Phase", School.ARCANA, 3, Kind.LUMINOUS_PHASE, Pool.MANA, null, 12, 20, 800),
    AEGIS_OF_STARS("Aegis of Stars", School.ARCANA, 4, Kind.AEGIS_OF_STARS, Pool.MANA, null, 14, 70, 1800),
    ASTRAL_NOVA("Astral Nova", School.ARCANA, 5, Kind.ASTRAL_NOVA, Pool.MANA, null, 20, 80, 2100),

    // --- Gravemancy (bone, undead, life drain) ---
    BONE_SPEAR("Bone Spear", School.GRAVEMANCY, 1, Kind.BONE_SPEAR, Pool.MANA, null, 7, 40, 60),
    RAISE_MINION("Raise Minion", School.GRAVEMANCY, 2, Kind.RAISE_MINION, Pool.MANA, null, 18, 80, 0),
    SOUL_DRAIN("Soul Drain", School.GRAVEMANCY, 3, Kind.SOUL_DRAIN, Pool.MANA, null, 12, 40, 1800),
    EYE_DECAY("Eye Decay", School.GRAVEMANCY, 4, Kind.EYE_DECAY, Pool.MANA, null, 10, 40, 600),
    WRAITH_STEP("Wraith Step", School.GRAVEMANCY, 5, Kind.WRAITH_STEP, Pool.MANA, null, 11, 20, 1200),

    // --- Incantation Magic (utility chants) ---
    CHANT_OF_GROWTH("Chant of Growth", School.INCANTATION, 1, Kind.CHANT_OF_GROWTH, Pool.MANA, null, 5, 24, 200),
    BATTLE_HYMN("Battle Hymn", School.INCANTATION, 2, Kind.BATTLE_HYMN, Pool.MANA, null, 15, 60, 600),
    WORD_OF_UNMAKING("Word of Unmaking", School.INCANTATION, 3, Kind.WORD_OF_UNMAKING, Pool.MANA, null, 8, 6, 300),
    FEARCRAFT("Fearcraft", School.INCANTATION, 4, Kind.FEARCRAFT, Pool.MANA, null, 8, 60, 1600),
    SILENCING_WHISPER("Silencing Whisper", School.INCANTATION, 5, Kind.SILENCING_WHISPER, Pool.MANA, null, 9, 2, 900),

    // --- Druid (nature buffs, shapeshifting, wildlife) ---
    THORNS("Thorns", School.DRUID, 1, Kind.THORNS, Pool.MANA, null, 7, 30, 60),
    WOLF_FORM("Wolf Form", School.DRUID, 2, Kind.WOLF_FORM, Pool.MANA, null, 17, 60, 1200),
    DOLPHIN_FORM("Dolphin Form", School.DRUID, 3, Kind.DOLPHIN_FORM, Pool.MANA, null, 15, 60, 400),
    BLOOM_OF_RENEWAL("Bloom of Renewal", School.DRUID, 4, Kind.BLOOM_OF_RENEWAL, Pool.MANA, null, 11, 40, 900),
    SWARM_OF_THE_WILD("Swarm of the Wild", School.DRUID, 5, Kind.SWARM_OF_THE_WILD, Pool.MANA, null, 19, 80, 2400);

    public enum Pool { HEALTH, STAMINA, MANA }

    /** How SpellCasting resolves the spell when the cast finishes. */
    public enum Kind {
        TRANSFER, MAGIC_BOLT, SUNDER, HEAL_OTHER, AWAY, WARD, BRIGHT_LIGHT,
        EMBER_DART, SERPENTS_PLUME, SUNBURST, PYROCLASM, CINDER_MAELSTROM,
        HEARTWELL, WITHER, SLUMP, PESTILENCE, HEXDRAIN,
        LIGHTNING_STRIKE, SPEED_OF_WIND, CHAIN_SHOCK, WIND_LURE, HOWLING_IMPACT,
        WATER_BREATHING, ICE_WALL, WATER_ORB, WATER_SPOUT, RIPTIDE,
        EARTHEN_PATH, SEISMIC_PILLAR, QUAKE_STOMP, BARK_SKIN, FISSURE,
        BONE_SPEAR, RAISE_MINION, SOUL_DRAIN, EYE_DECAY, WRAITH_STEP,
        STARLANCE, ILLUMINATE_VISION, LUMINOUS_PHASE, AEGIS_OF_STARS, ASTRAL_NOVA,
        CHANT_OF_GROWTH, BATTLE_HYMN, WORD_OF_UNMAKING, FEARCRAFT, SILENCING_WHISPER,
        THORNS, WOLF_FORM, DOLPHIN_FORM, BLOOM_OF_RENEWAL, SWARM_OF_THE_WILD
    }

    private final String displayName;
    private final School school;
    private final int tier;
    private final Kind kind;
    private final Pool costPool;
    private final Pool gainPool;    // transfers only
    private final double flatCost;  // fixed pool cost for non-transfers
    private final int castTicks;
    private final int cooldownTicks;

    Spell(String displayName, School school, int tier, Kind kind,
          Pool costPool, Pool gainPool, double flatCost, int castTicks, int cooldownTicks) {
        this.displayName = displayName;
        this.school = school;
        this.tier = tier;
        this.kind = kind;
        this.costPool = costPool;
        this.gainPool = gainPool;
        this.flatCost = flatCost;
        this.castTicks = castTicks;
        this.cooldownTicks = cooldownTicks;
    }

    public String displayName() {
        return displayName;
    }

    public School school() {
        return school;
    }

    public int tier() {
        return tier;
    }

    public Kind kind() {
        return kind;
    }

    public Pool costPool() {
        return costPool;
    }

    public Pool gainPool() {
        return gainPool;
    }

    public double flatCost() {
        return flatCost;
    }

    public int castTicks() {
        return castTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public boolean isTransfer() {
        return kind == Kind.TRANSFER;
    }

    /** School level required to cast this spell. */
    public int unlockLevel() {
        return school.unlockLevel(tier);
    }

    /** The spell of a school at a given tier, or null. */
    public static Spell of(School school, int tier) {
        for (Spell s : values()) {
            if (s.school == school && s.tier == tier) {
                return s;
            }
        }
        return null;
    }

    public static Spell byName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
