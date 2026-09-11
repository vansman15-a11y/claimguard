package net.robmc.rpgstats.client;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.StatFormulas;

/** Appends Health/Stamina/Mana Gain to any food item's tooltip, and a Nourishment note. */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
public final class FoodTooltip {

    private FoodTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        var food = event.getItemStack().getFoodProperties(event.getEntity());
        if (food == null) {
            return;
        }
        double gain = StatFormulas.foodTotalGain(food);
        String amount = trim(gain);
        event.getToolTip().add(Component.literal("Health Gain: " + amount).withStyle(ChatFormatting.RED));
        event.getToolTip().add(Component.literal("Stamina Gain: " + amount).withStyle(ChatFormatting.YELLOW));
        event.getToolTip().add(Component.literal("Mana Gain: " + amount).withStyle(ChatFormatting.AQUA));
        event.getToolTip().add(Component.literal("Gained gradually over 10s, and refreshes Nourishment for 30 min.")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }
}
