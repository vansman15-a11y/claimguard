package net.robmc.rpgstats.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.robmc.rpgstats.StatFormulas;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Arcane Bolt's constellation sigil. A marked target takes {@link StatFormulas#ARCANA_MARK_BONUS}
 * extra damage from the marker's next Arcana hit, which spends the mark.
 */
public final class ArcanaMark {

    private record Mark(UUID marker, long endTick) {
    }

    private static final Map<Integer, Mark> marks = new HashMap<>();

    private ArcanaMark() {
    }

    public static void mark(ServerPlayer caster, LivingEntity target) {
        marks.put(target.getId(), new Mark(caster.getUUID(),
                target.level().getGameTime() + StatFormulas.ARCANE_BOLT_MARK_TICKS));
        ((ServerLevel) target.level()).sendParticles(ParticleTypes.ENCHANT,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 16, 0.4, 0.5, 0.4, 0.6);
    }

    /**
     * Damage {@code base} should deal to {@code target} from {@code caster}'s Arcana spell -
     * boosted (and the sigil consumed + detonated) if the caster's own mark is on it.
     */
    public static float onArcanaHit(ServerPlayer caster, LivingEntity target, float base) {
        if (caster == null || target == null) {
            return base;
        }
        Mark m = marks.get(target.getId());
        if (m == null || !m.marker().equals(caster.getUUID())
                || target.level().getGameTime() >= m.endTick()) {
            return base;
        }
        marks.remove(target.getId());
        ServerLevel level = (ServerLevel) target.level();
        level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 24, 0.3, 0.4, 0.3, 0.15);
        return base * (float) StatFormulas.ARCANA_MARK_BONUS;
    }

    /** Called every server tick from RpgEvents - drop expired sigils. */
    public static void tick(MinecraftServer server) {
        if (marks.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        marks.values().removeIf(m -> now >= m.endTick());
    }
}
