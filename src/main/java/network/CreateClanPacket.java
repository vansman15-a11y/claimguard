package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.claimguard.clan.ClanActions;

import java.util.function.Supplier;

/**
 * Client -> server: the Create Clan screen was submitted. The server re-validates
 * (charter present and signed, name/tag rules, not already in a clan).
 */
public class CreateClanPacket {

    private final String name;
    private final String tag;
    private final String motd;

    public CreateClanPacket(String name, String tag, String motd) {
        this.name = name;
        this.tag = tag;
        this.motd = motd;
    }

    public static void encode(CreateClanPacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.name, 64);
        buf.writeUtf(packet.tag, 16);
        buf.writeUtf(packet.motd, 512);
    }

    public static CreateClanPacket decode(FriendlyByteBuf buf) {
        return new CreateClanPacket(buf.readUtf(64), buf.readUtf(16), buf.readUtf(512));
    }

    public static void handle(CreateClanPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ClanActions.createClan(player, packet.name, packet.tag, packet.motd);
            }
        });
        context.setPacketHandled(true);
    }
}
