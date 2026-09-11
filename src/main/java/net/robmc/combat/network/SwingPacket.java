package net.robmc.combat.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.combat.CombatEvents;
import net.robmc.combat.MeleeArc;

import java.util.function.Supplier;

/**
 * Client -&gt; server: the player swung a melee weapon at nothing in particular.
 * The server sweeps the cleave arc from where they're looking so a swing at air
 * still catches anyone in the fan.
 */
public class SwingPacket {

    public SwingPacket() {
    }

    public static void encode(SwingPacket p, FriendlyByteBuf buf) {
    }

    public static SwingPacket decode(FriendlyByteBuf buf) {
        return new SwingPacket();
    }

    public static void handle(SwingPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && CombatEvents.isMeleeWeapon(player.getMainHandItem()) && CombatEvents.tryStartSwing(player)) {
                MeleeArc.sweep(player, null, false);
            }
        });
        context.setPacketHandled(true);
    }
}
