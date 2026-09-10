package net.robmc.combat.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; client: the viewer's current melee combo streak and whether the next hit crits. */
public class ComboSyncPacket {

    public final int streak;
    public final boolean armed;

    public ComboSyncPacket(int streak, boolean armed) {
        this.streak = streak;
        this.armed = armed;
    }

    public static void encode(ComboSyncPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.streak);
        buf.writeBoolean(p.armed);
    }

    public static ComboSyncPacket decode(FriendlyByteBuf buf) {
        return new ComboSyncPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(ComboSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.robmc.combat.client.ClientCombo.accept(packet)));
        context.setPacketHandled(true);
    }
}
