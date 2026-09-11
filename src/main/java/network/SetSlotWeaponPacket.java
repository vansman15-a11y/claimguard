package net.robmc.claimguard.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.item.Weapons;

import java.util.function.Supplier;

/**
 * Client -&gt; server: the J editor's "set from held item" button - bind whatever weapon
 * the player is currently holding as this bar slot's forced weapon (empty hand clears it).
 */
public class SetSlotWeaponPacket {

    private final int slot;

    public SetSlotWeaponPacket(int slot) {
        this.slot = slot;
    }

    public static void encode(SetSlotWeaponPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.slot);
    }

    public static SetSlotWeaponPacket decode(FriendlyByteBuf buf) {
        return new SetSlotWeaponPacket(buf.readVarInt());
    }

    public static void handle(SetSlotWeaponPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            ItemStack held = player.getMainHandItem();
            if (held.isEmpty()) {
                RpgManager.stats(player).setSlotWeaponId(packet.slot, "");
                player.displayClientMessage(Component.literal("Slot weapon cleared.").withStyle(ChatFormatting.GRAY), true);
            } else if (!Weapons.isBindableWeapon(held)) {
                player.displayClientMessage(Component.literal("That's not a weapon you can bind.").withStyle(ChatFormatting.GRAY), true);
                return;
            } else {
                var id = ForgeRegistries.ITEMS.getKey(held.getItem());
                if (id != null) {
                    RpgManager.stats(player).setSlotWeaponId(packet.slot, id.toString());
                    player.displayClientMessage(Component.literal("Slot weapon set to " + held.getHoverName().getString() + ".")
                            .withStyle(ChatFormatting.GRAY), true);
                }
            }
            net.robmc.rpgstats.RpgData.get(player.server).markDirty();
            RpgManager.syncSpellBar(player);
        });
        context.setPacketHandled(true);
    }
}
