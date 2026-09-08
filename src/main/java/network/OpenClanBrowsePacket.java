package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server -> client: the clan directory for ClanBrowseScreen. */
public class OpenClanBrowsePacket {

    /** relationOrdinal: -1 neutral, else ClanRelation.ordinal(). */
    public record ClanRow(UUID clanId, String name, String tag, int memberCount,
                          String raidWindow, boolean windowOpen, int relationOrdinal, boolean underSiege) {
    }

    private final int viewerRankOrdinal;
    private final List<ClanRow> clans;

    public OpenClanBrowsePacket(int viewerRankOrdinal, List<ClanRow> clans) {
        this.viewerRankOrdinal = viewerRankOrdinal;
        this.clans = clans;
    }

    public int viewerRankOrdinal() {
        return viewerRankOrdinal;
    }

    public List<ClanRow> clans() {
        return clans;
    }

    public static void encode(OpenClanBrowsePacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.viewerRankOrdinal);
        buf.writeVarInt(packet.clans.size());
        for (ClanRow row : packet.clans) {
            buf.writeUUID(row.clanId());
            buf.writeUtf(row.name(), 64);
            buf.writeUtf(row.tag(), 16);
            buf.writeVarInt(row.memberCount());
            buf.writeUtf(row.raidWindow(), 32);
            buf.writeBoolean(row.windowOpen());
            buf.writeVarInt(row.relationOrdinal() + 1); // shift so -1 -> 0 fits a varint
            buf.writeBoolean(row.underSiege());
        }
    }

    public static OpenClanBrowsePacket decode(FriendlyByteBuf buf) {
        int rank = buf.readVarInt();
        int count = buf.readVarInt();
        List<ClanRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new ClanRow(
                    buf.readUUID(), buf.readUtf(64), buf.readUtf(16), buf.readVarInt(),
                    buf.readUtf(32), buf.readBoolean(), buf.readVarInt() - 1, buf.readBoolean()));
        }
        return new OpenClanBrowsePacket(rank, rows);
    }

    public static void handle(OpenClanBrowsePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.claimguard.client.ClanClient.openBrowse(packet)
        ));
        context.setPacketHandled(true);
    }
}
