package net.robmc.rpgstats.magic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.robmc.rpgstats.StatFormulas;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chaos Magic's lingering stat debuffs (as opposed to the damage-over-time ones,
 * which live in {@link BleedManager} / {@link DiseaseManager}). Keyed by entity
 * id so it works on players and mobs alike.
 *
 * <ul>
 *   <li>{@link Kind#WITHER} - your casts are slower and your spells hit softer.</li>
 *   <li>{@link Kind#SLUMP} - a transient -HP-max modifier (vanilla clamps current
 *       HP down); the stamina half is a one-shot drain applied where it's cast.</li>
 * </ul>
 */
public final class Afflictions {

    public enum Kind { WITHER, SLUMP }

    /** Stable UUID for the Slump max-health attribute modifier. */
    private static final UUID SLUMP_HP_MOD = UUID.fromString("c4a05e10-0001-4a00-8000-00000000cafe");

    private static final Map<Integer, EnumMap<Kind, Long>> active = new HashMap<>();

    private Afflictions() {
    }

    public static void apply(LivingEntity target, Kind kind, int durationTicks) {
        long end = target.level().getGameTime() + durationTicks;
        active.computeIfAbsent(target.getId(), k -> new EnumMap<>(Kind.class)).merge(kind, end, Math::max);
        if (kind == Kind.SLUMP) {
            AttributeInstance hp = target.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null && hp.getModifier(SLUMP_HP_MOD) == null) {
                hp.addTransientModifier(new AttributeModifier(SLUMP_HP_MOD, "chaos_slump",
                        -StatFormulas.SLUMP_MAX_HP_FRACTION, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    public static boolean has(LivingEntity entity, Kind kind) {
        return remainingTicks(entity.level().getGameTime(), entity.getId(), kind) > 0;
    }

    public static int remainingTicks(long now, int entityId, Kind kind) {
        EnumMap<Kind, Long> m = active.get(entityId);
        if (m == null) {
            return 0;
        }
        Long end = m.get(kind);
        return end == null ? 0 : (int) Math.max(0, end - now);
    }

    /** Cast-time multiplier for a caster (>1 = slower). */
    public static double castTimeMult(LivingEntity caster) {
        return has(caster, Kind.WITHER) ? StatFormulas.WITHER_CAST_TIME_MULT : 1.0;
    }

    /** Outgoing spell-damage multiplier for a caster (<1 = weaker). */
    public static double spellDamageMult(LivingEntity caster) {
        return has(caster, Kind.WITHER) ? StatFormulas.WITHER_SPELL_DAMAGE_MULT : 1.0;
    }

    /** Called every server tick from RpgEvents: expire entries and lift the Slump modifier when it ends. */
    public static void tick(MinecraftServer server) {
        if (active.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        active.entrySet().removeIf(entry -> {
            EnumMap<Kind, Long> m = entry.getValue();
            boolean hadSlump = m.containsKey(Kind.SLUMP);
            m.entrySet().removeIf(e -> now >= e.getValue());
            if (hadSlump && !m.containsKey(Kind.SLUMP)) {
                LivingEntity le = find(server, entry.getKey());
                if (le != null) {
                    AttributeInstance hp = le.getAttribute(Attributes.MAX_HEALTH);
                    if (hp != null) {
                        hp.removeModifier(SLUMP_HP_MOD);
                    }
                }
            }
            return m.isEmpty();
        });
    }

    private static LivingEntity find(MinecraftServer server, int entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(entityId);
            if (e instanceof LivingEntity le) {
                return le;
            }
        }
        return null;
    }
}
