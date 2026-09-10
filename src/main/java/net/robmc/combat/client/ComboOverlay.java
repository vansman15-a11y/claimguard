package net.robmc.combat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.combat.CombatConfig;
import net.robmc.combat.CombatEvents;
import net.robmc.combat.CombatMod;

/**
 * Four pips ringed around the crosshair. They light amber as the combo builds;
 * when the finisher is armed the whole ring pulses red.
 */
@Mod.EventBusSubscriber(modid = CombatMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ComboOverlay {

    private static final int PIPS = CombatConfig.COMBO_HITS_FOR_CRIT - 1; // 4
    private static final int RADIUS = 10;
    private static final int DIM = 0x40FFFFFF;
    private static final int AMBER = 0xFFFFC24B;

    private ComboOverlay() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.CROSSHAIR.id(), "combat_combo", COMBO);
    }

    private static final IGuiOverlay COMBO = (gui, g, partialTick, screenW, screenH) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !CombatEvents.isMeleeWeapon(mc.player.getMainHandItem())) {
            return;
        }
        int streak = ClientCombo.streak();
        boolean armed = ClientCombo.armed();
        if (streak <= 0 && !armed) {
            return;
        }

        int cx = screenW / 2;
        int cy = screenH / 2;
        boolean flash = armed && (ClientCombo.sinceChange() % 12) < 6;
        int lit = armed ? (flash ? 0xFFFF4040 : 0xFFB03030) : AMBER;

        for (int i = 0; i < PIPS; i++) {
            double a = -Math.PI / 2 + i * (Math.PI * 2 / PIPS);
            int px = cx + (int) Math.round(Math.cos(a) * RADIUS) - 1;
            int py = cy + (int) Math.round(Math.sin(a) * RADIUS) - 1;
            int col = (i < streak || armed) ? lit : DIM;
            g.fill(px - 1, py - 1, px + 2, py + 2, 0xC0000000);
            g.fill(px, py, px + 2, py + 2, col);
        }
    };
}
