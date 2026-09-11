package net.robmc.combat.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; client: a melee swing was just allowed through the hard global cooldown - starts it client-side. */
public class SwingCooldownPacket {

    public final String weaponId; // registry id of the weapon swung, "" = unknown
    public final int durationTicks;

    public SwingCooldownPacket(String weaponId, int durationTicks) {
        this.weaponId = weaponId;
        this.durationTicks = durationTicks;
    }

    public static void encode(SwingCooldownPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.weaponId, 64);
        buf.writeVarInt(p.durationTicks);
    }

    public static SwingCooldownPacket decode(FriendlyByteBuf buf) {
        return new SwingCooldownPacket(buf.readUtf(64), buf.readVarInt());
    }

    public static void handle(SwingCooldownPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.robmc.combat.client.ClientSwingCooldown.accept(packet)));
        context.setPacketHandled(true);
    }
}
