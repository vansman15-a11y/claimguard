package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: a cast started ({@code durationTicks} > 0) or was cancelled ("" / 0). */
public class CastStatePacket {

    public final String spellName;
    public final int durationTicks;

    public CastStatePacket(String spellName, int durationTicks) {
        this.spellName = spellName == null ? "" : spellName;
        this.durationTicks = durationTicks;
    }

    public static void encode(CastStatePacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.spellName, 48);
        buf.writeVarInt(p.durationTicks);
    }

    public static CastStatePacket decode(FriendlyByteBuf buf) {
        return new CastStatePacket(buf.readUtf(48), buf.readVarInt());
    }

    public static void handle(CastStatePacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.magic.ClientSpells.onCastState(packet)
        ));
        context.setPacketHandled(true);
    }
}
