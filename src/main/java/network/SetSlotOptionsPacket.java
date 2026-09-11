package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.robmc.rpgstats.PlayerStats;
import net.robmc.rpgstats.RpgManager;

import java.util.function.Supplier;

/** Client -&gt; server: the J editor's slot-options popup - cycle mode, auto cast, and force-weapon-switch for one bar slot. */
public class SetSlotOptionsPacket {

    private final int slot;
    private final int cycleMode;
    private final boolean autoCast;
    private final boolean forceWeapon;

    public SetSlotOptionsPacket(int slot, int cycleMode, boolean autoCast, boolean forceWeapon) {
        this.slot = slot;
        this.cycleMode = cycleMode;
        this.autoCast = autoCast;
        this.forceWeapon = forceWeapon;
    }

    public static void encode(SetSlotOptionsPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.slot);
        buf.writeVarInt(p.cycleMode);
        buf.writeBoolean(p.autoCast);
        buf.writeBoolean(p.forceWeapon);
    }

    public static SetSlotOptionsPacket decode(FriendlyByteBuf buf) {
        return new SetSlotOptionsPacket(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(SetSlotOptionsPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            PlayerStats s = RpgManager.stats(player);
            PlayerStats.CycleMode[] modes = PlayerStats.CycleMode.values();
            if (packet.cycleMode >= 0 && packet.cycleMode < modes.length) {
                s.setSlotCycleMode(packet.slot, modes[packet.cycleMode]);
            }
            s.setSlotAutoCast(packet.slot, packet.autoCast);
            s.setSlotForceWeapon(packet.slot, packet.forceWeapon);
            net.robmc.rpgstats.RpgData.get(player.server).markDirty();
            RpgManager.syncSpellBar(player);
        });
        context.setPacketHandled(true);
    }
}
