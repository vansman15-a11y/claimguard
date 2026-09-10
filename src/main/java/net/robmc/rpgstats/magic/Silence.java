package net.robmc.rpgstats.magic;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Silencing Whisper's lockout: for a couple of seconds a player can't cast spells
 * from a given school (or every school, if they weren't mid-cast when hit).
 */
public final class Silence {

    private static final Map<UUID, EnumMap<School, Long>> locks = new HashMap<>();

    private Silence() {
    }

    public static void apply(ServerPlayer target, School school, int durationTicks) {
        long end = target.serverLevel().getGameTime() + durationTicks;
        locks.computeIfAbsent(target.getUUID(), k -> new EnumMap<>(School.class)).merge(school, end, Math::max);
    }

    public static void applyAll(ServerPlayer target, int durationTicks) {
        for (School school : School.values()) {
            apply(target, school, durationTicks);
        }
    }

    /** True if this caster is currently silenced for the given school. */
    public static boolean blocks(ServerPlayer caster, School school) {
        EnumMap<School, Long> m = locks.get(caster.getUUID());
        if (m == null) {
            return false;
        }
        Long end = m.get(school);
        return end != null && caster.serverLevel().getGameTime() < end;
    }

    public static void clear(UUID id) {
        locks.remove(id);
    }

    /** Called every server tick from RpgEvents. */
    public static void tick(MinecraftServer server) {
        if (locks.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        locks.entrySet().removeIf(entry -> {
            entry.getValue().entrySet().removeIf(e -> now >= e.getValue());
            return entry.getValue().isEmpty();
        });
    }
}
