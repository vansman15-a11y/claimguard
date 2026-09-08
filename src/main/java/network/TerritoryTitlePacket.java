package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TerritoryTitlePacket {

    private final String text;
    private final int durationTicks;

    public TerritoryTitlePacket(String text, int durationTicks) {
        this.text = text;
        this.durationTicks = durationTicks;
    }

    public static void encode(TerritoryTitlePacket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.text);
        buf.writeVarInt(packet.durationTicks);
    }

    public static TerritoryTitlePacket decode(FriendlyByteBuf buf) {
        return new TerritoryTitlePacket(buf.readUtf(), buf.readVarInt());
    }

    public static void handle(TerritoryTitlePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.claimguard.client.TerritoryOverlayClient.show(packet.text, packet.durationTicks)
        ));
        context.setPacketHandled(true);
    }
}