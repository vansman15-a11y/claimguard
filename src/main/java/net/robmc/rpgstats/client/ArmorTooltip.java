package net.robmc.rpgstats.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.item.ArmorTier;

/** Appends Encumbrance / Physical Protection / Magic Protection to any armour piece's tooltip. */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
public final class ArmorTooltip {

    private ArmorTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (!(event.getItemStack().getItem() instanceof ArmorItem armor)) {
            return;
        }
        ArmorTier tier = ArmorTier.of(event.getItemStack());
        if (tier == null) {
            return;
        }
        var slot = armor.getType().getSlot();
        double encumbrance = tier.encumbrance(slot);
        double physical = tier.physicalProtection(slot);
        double magic = tier.magicProtection(slot);

        event.getToolTip().add(Component.literal("Encumbrance: " + trim(encumbrance)).withStyle(ChatFormatting.GRAY));
        event.getToolTip().add(Component.literal("Physical Protection: " + trim(physical) + "%").withStyle(ChatFormatting.RED));
        event.getToolTip().add(Component.literal("Magic Protection: " + trim(magic) + "%").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
