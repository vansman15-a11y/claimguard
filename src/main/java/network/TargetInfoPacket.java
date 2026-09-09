package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -&gt; client, a few times a second: the harmful effects on whatever the
 * player is looking at, so the target frame can show a debuff row with live
 * countdowns. {@code entityId} lets the client match it to its own picked target.
 */
public class TargetInfoPacket {

    public static final byte KIND_VANILLA = 0; // id = BuiltInRegistries.MOB_EFFECT id
    public static final byte KIND_BURN = 1;    // stacks used
    public static final byte KIND_BLEED = 2;

    public static final class Debuff {
        public final byte kind;
        public final int id;
        public final int ticksLeft;
        public final byte stacks;

        public Debuff(byte kind, int id, int ticksLeft, byte stacks) {
            this.kind = kind;
            this.id = id;
            this.ticksLeft = ticksLeft;
            this.stacks = stacks;
        }
    }

    public final int entityId;
    public final List<Debuff> debuffs;

    public TargetInfoPacket(int entityId, List<Debuff> debuffs) {
        this.entityId = entityId;
        this.debuffs = debuffs;
    }

    public static void encode(TargetInfoPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entityId);
        buf.writeVarInt(p.debuffs.size());
        for (Debuff d : p.debuffs) {
            buf.writeByte(d.kind);
            buf.writeVarInt(d.id);
            buf.writeVarInt(d.ticksLeft);
            buf.writeByte(d.stacks);
        }
    }

    public static TargetInfoPacket decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int n = Math.max(0, Math.min(buf.readVarInt(), 32));
        List<Debuff> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Debuff(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readByte()));
        }
        return new TargetInfoPacket(entityId, list);
    }

    public static void handle(TargetInfoPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.ClientTargetInfo.accept(packet)
        ));
        context.setPacketHandled(true);
    }
}
