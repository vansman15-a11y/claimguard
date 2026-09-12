package net.robmc.combat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Per-player "when I switch to this sword, pull this shield into my offhand" bindings - set by
 * dragging a shield onto a sword (or vice versa) in the survival/creative inventory. Stored
 * directly on the player's own persistent data (survives relog and death) rather than a
 * dedicated save file, since it's a handful of item-id strings per player.
 */
public final class ShieldBindings {

    private static final String ROOT = "CombatShieldBindings";

    private ShieldBindings() {
    }

    public static void bind(ServerPlayer player, String swordId, String shieldId) {
        CompoundTag root = player.getPersistentData();
        CompoundTag map = root.getCompound(ROOT);
        map.putString(swordId, shieldId);
        root.put(ROOT, map);
    }

    /** The shield item id bound to this sword item id, or null if none is set. */
    public static String shieldFor(ServerPlayer player, String swordId) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT)) {
            return null;
        }
        CompoundTag map = root.getCompound(ROOT);
        return map.contains(swordId) ? map.getString(swordId) : null;
    }
}
