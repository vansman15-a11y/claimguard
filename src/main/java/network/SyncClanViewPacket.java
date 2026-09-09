package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server -&gt; client: one row per online player - their name, their clan tag, and
 * how the receiving player's clan regards them (own clan / ally / everyone else).
 * Drives the target frame's name colour and the clan tag it shows.
 */
public class SyncClanViewPacket {

    /** relation codes */
    public static final byte SELF = 0;   // same clan as the viewer  -> green
    public static final byte ALLY = 1;   // a clan the viewer's clan allied -> dark green
    public static final byte ENEMY = 2;  // everyone else -> red

    public static final class Row {
        public final UUID id;
        public final String name;
        public final String tag;
        public final byte relation;

        public Row(UUID id, String name, String tag, byte relation) {
            this.id = id;
            this.name = name;
            this.tag = tag;
            this.relation = relation;
        }
    }

    public final List<Row> rows;

    public SyncClanViewPacket(List<Row> rows) {
        this.rows = rows;
    }

    public static void encode(SyncClanViewPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.rows.size());
        for (Row r : p.rows) {
            buf.writeUUID(r.id);
            buf.writeUtf(r.name, 48);
            buf.writeUtf(r.tag == null ? "" : r.tag, 16);
            buf.writeByte(r.relation);
        }
    }

    public static SyncClanViewPacket decode(FriendlyByteBuf buf) {
        int n = Math.max(0, Math.min(buf.readVarInt(), 512));
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rows.add(new Row(buf.readUUID(), buf.readUtf(48), buf.readUtf(16), buf.readByte()));
        }
        return new SyncClanViewPacket(rows);
    }

    public static void handle(SyncClanViewPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.ClientClanView.accept(packet)
        ));
        context.setPacketHandled(true);
    }
}
