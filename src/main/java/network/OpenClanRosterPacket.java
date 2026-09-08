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
 * Server -> client: a snapshot of the viewer's clan for the roster screen.
 * Re-sent after any change (promote/kick/...) so the open screen refreshes.
 */
public class OpenClanRosterPacket {

    public record MemberRow(UUID id, String name, int rankOrdinal, boolean online, boolean canBuild) {
    }

    private final String clanName;
    private final String clanTag;
    private final String motd;
    private final int viewerRankOrdinal;
    private final List<MemberRow> members;

    public OpenClanRosterPacket(String clanName, String clanTag, String motd, int viewerRankOrdinal, List<MemberRow> members) {
        this.clanName = clanName;
        this.clanTag = clanTag;
        this.motd = motd;
        this.viewerRankOrdinal = viewerRankOrdinal;
        this.members = members;
    }

    public String clanName() {
        return clanName;
    }

    public String clanTag() {
        return clanTag;
    }

    public String motd() {
        return motd;
    }

    public int viewerRankOrdinal() {
        return viewerRankOrdinal;
    }

    public List<MemberRow> members() {
        return members;
    }

    public static void encode(OpenClanRosterPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.clanName, 64);
        buf.writeUtf(packet.clanTag, 16);
        buf.writeUtf(packet.motd, 512);
        buf.writeVarInt(packet.viewerRankOrdinal);
        buf.writeVarInt(packet.members.size());
        for (MemberRow row : packet.members) {
            buf.writeUUID(row.id());
            buf.writeUtf(row.name(), 32);
            buf.writeVarInt(row.rankOrdinal());
            buf.writeBoolean(row.online());
            buf.writeBoolean(row.canBuild());
        }
    }

    public static OpenClanRosterPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(64);
        String tag = buf.readUtf(16);
        String motd = buf.readUtf(512);
        int viewerRank = buf.readVarInt();
        int count = buf.readVarInt();
        List<MemberRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new MemberRow(buf.readUUID(), buf.readUtf(32), buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));
        }
        return new OpenClanRosterPacket(name, tag, motd, viewerRank, rows);
    }

    public static void handle(OpenClanRosterPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.claimguard.client.ClanClient.openRoster(packet)
        ));
        context.setPacketHandled(true);
    }
}
