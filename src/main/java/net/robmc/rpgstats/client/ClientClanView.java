package net.robmc.rpgstats.client;

import net.robmc.claimguard.network.SyncClanViewPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client mirror of {@link SyncClanViewPacket}: for every online player, their
 * name, clan tag and how our clan regards them. Read by the target frame (and
 * anything else that wants clan-coloured names).
 */
public final class ClientClanView {

    private static final class Entry {
        final String name;
        final String tag;
        final byte relation;

        Entry(String name, String tag, byte relation) {
            this.name = name;
            this.tag = tag;
            this.relation = relation;
        }
    }

    private static final Map<UUID, Entry> ROWS = new HashMap<>();

    private ClientClanView() {
    }

    public static void accept(SyncClanViewPacket p) {
        ROWS.clear();
        for (SyncClanViewPacket.Row r : p.rows) {
            ROWS.put(r.id, new Entry(r.name, r.tag, r.relation));
        }
    }

    /** {@link SyncClanViewPacket#SELF}/{@code ALLY}/{@code ENEMY}, or {@code ENEMY} if we don't know them. */
    public static byte relation(UUID id) {
        Entry e = ROWS.get(id);
        return e == null ? SyncClanViewPacket.ENEMY : e.relation;
    }

    /** Clan tag, or "" if none / unknown. */
    public static String tag(UUID id) {
        Entry e = ROWS.get(id);
        return e == null || e.tag == null ? "" : e.tag;
    }

    /** Known name, or null. */
    public static String name(UUID id) {
        Entry e = ROWS.get(id);
        return e == null ? null : e.name;
    }
}
