package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: you died while bound AND with a bed set - show the
 * bind-vs-bed respawn chooser (replaces the vanilla death screen).
 */
public class OpenRespawnChoicePacket {

    public OpenRespawnChoicePacket() {
    }

    public static void encode(OpenRespawnChoicePacket packet, FriendlyByteBuf buf) {
    }

    public static OpenRespawnChoicePacket decode(FriendlyByteBuf buf) {
        return new OpenRespawnChoicePacket();
    }

    public static void handle(OpenRespawnChoicePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.claimguard.client.BindClient.openRespawnChoice()
        ));
        context.setPacketHandled(true);
    }
}
