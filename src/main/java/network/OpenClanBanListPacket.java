package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server -> client: the clan's ban list for ClanBanListScreen. */
public class OpenClanBanListPacket {

    public record BanRow(UUID id, String name) {
    }

    private final String clanName;
    private final boolean canUnban;
    private final List<BanRow> banned;

    public OpenClanBanListPacket(String clanName, boolean canUnban, List<BanRow> banned) {
        this.clanName = clanName;
        this.canUnban = canUnban;
        this.banned = banned;
    }

    public String clanName() {
        return clanName;
    }

    public boolean canUnban() {
        return canUnban;
    }

    public List<BanRow> banned() {
        return banned;
    }

    public static void encode(OpenClanBanListPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.clanName, 64);
        buf.writeBoolean(packet.canUnban);
        buf.writeVarInt(packet.banned.size());
        for (BanRow row : packet.banned) {
            buf.writeUUID(row.id());
            buf.writeUtf(row.name(), 32);
        }
    }

    public static OpenClanBanListPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(64);
        boolean canUnban = buf.readBoolean();
        int count = buf.readVarInt();
        List<BanRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new BanRow(buf.readUUID(), buf.readUtf(32)));
        }
        return new OpenClanBanListPacket(name, canUnban, rows);
    }

    public static void handle(OpenClanBanListPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.claimguard.client.ClanClient.openBanList(packet)
        ));
        context.setPacketHandled(true);
    }
}
