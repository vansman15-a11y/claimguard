package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: "draw this box outline in the world for a few seconds."
 * Sent when a claim owner right-clicks their core bare-handed (see ClaimCoreBlock.use).
 *
 * handle() only ever runs on the client; DistExecutor keeps the client-only
 * ClaimBorderClient class off a dedicated server's classloader.
 */
public class ShowClaimBorderPacket {

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final int durationTicks;

    public ShowClaimBorderPacket(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int durationTicks) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.durationTicks = durationTicks;
    }

    public static void encode(ShowClaimBorderPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.minX);
        buf.writeInt(packet.minY);
        buf.writeInt(packet.minZ);
        buf.writeInt(packet.maxX);
        buf.writeInt(packet.maxY);
        buf.writeInt(packet.maxZ);
        buf.writeVarInt(packet.durationTicks);
    }

    public static ShowClaimBorderPacket decode(FriendlyByteBuf buf) {
        return new ShowClaimBorderPacket(
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readVarInt()
        );
    }

    public static void handle(ShowClaimBorderPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.claimguard.client.ClaimBorderClient.toggle(
                        packet.minX, packet.minY, packet.minZ,
                        packet.maxX, packet.maxY, packet.maxZ,
                        packet.durationTicks
                )
        ));
        context.setPacketHandled(true);
    }
}
