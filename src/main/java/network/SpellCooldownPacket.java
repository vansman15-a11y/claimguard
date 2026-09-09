package net.robmc.claimguard.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -&gt; client: a spell just went on cooldown for {@code durationTicks}. */
public class SpellCooldownPacket {

    public final String spellName;
    public final int durationTicks;

    public SpellCooldownPacket(String spellName, int durationTicks) {
        this.spellName = spellName == null ? "" : spellName;
        this.durationTicks = durationTicks;
    }

    public static void encode(SpellCooldownPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.spellName, 48);
        buf.writeVarInt(p.durationTicks);
    }

    public static SpellCooldownPacket decode(FriendlyByteBuf buf) {
        return new SpellCooldownPacket(buf.readUtf(48), buf.readVarInt());
    }

    public static void handle(SpellCooldownPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> net.robmc.rpgstats.client.magic.ClientSpells.onSpellCooldown(packet)
        ));
        context.setPacketHandled(true);
    }
}
