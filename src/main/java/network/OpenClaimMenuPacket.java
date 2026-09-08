package net.robmc.claimguard.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: "open the claim menu for this core." Sent when the owner
 * right-clicks their core, and again after an upgrade so the open screen refreshes.
 *
 * The client already knows the full tier table (ClaimTier is a shared enum), so
 * this only carries the core position, the current tier, and the display name.
 */
public class OpenClaimMenuPacket {

    private final BlockPos corePos;
    private final int tierOrdinal;
    private final String claimName;
    private final boolean boundHere;

    public OpenClaimMenuPacket(BlockPos corePos, int tierOrdinal, String claimName, boolean boundHere) {
        this.corePos = corePos;
        this.tierOrdinal = tierOrdinal;
        this.claimName = claimName;
        this.boundHere = boundHere;
    }

    public BlockPos corePos() {
        return corePos;
    }

    public int tierOrdinal() {
        return tierOrdinal;
    }

    public String claimName() {
        return claimName;
    }

    public boolean boundHere() {
        return boundHere;
    }

    public static void encode(OpenClaimMenuPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.corePos);
        buf.writeVarInt(packet.tierOrdinal);
        buf.writeUtf(packet.claimName);
        buf.writeBoolean(packet.boundHere);
    }

    public static OpenClaimMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenClaimMenuPacket(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(OpenClaimMenuPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        // The double-supplier keeps the client-only class off the classloader on a
        // dedicated server, where net.minecraft.client doesn't exist.
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.claimguard.client.ClaimMenuClient.open(packet)
        ));
        context.setPacketHandled(true);
    }
}
