package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: open the Create Clan screen. Sent when the owner right-clicks
 * a fully-signed charter. No payload - the server re-finds the charter when the
 * CreateClanPacket comes back.
 */
public class OpenCreateClanScreenPacket {

    public OpenCreateClanScreenPacket() {
    }

    public static void encode(OpenCreateClanScreenPacket packet, FriendlyByteBuf buf) {
    }

    public static OpenCreateClanScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenCreateClanScreenPacket();
    }

    public static void handle(OpenCreateClanScreenPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.claimguard.client.ClanClient.openCreateScreen()
        ));
        context.setPacketHandled(true);
    }
}
