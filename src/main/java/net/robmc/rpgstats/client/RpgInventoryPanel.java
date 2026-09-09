package net.robmc.rpgstats.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;

/**
 * A small stats panel pinned to the left of the inventory screen, by the
 * paperdoll / armour slots - your six stat levels and the three pools.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
public class RpgInventoryPanel {

    private static final int PANEL_W = 96;
    private static final int ROW_H = 11;

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = screen.getMinecraft().font;

        int guiLeft = ((AbstractContainerScreen<?>) screen).getGuiLeft();
        int guiTop = ((AbstractContainerScreen<?>) screen).getGuiTop();

        int panelH = ROW_H * 10 + 14;
        int x = guiLeft - PANEL_W - 4;
        int y = guiTop;
        if (x < 2) { // no room on the left (tiny window) - tuck it above instead
            x = guiLeft;
            y = guiTop - panelH - 4;
        }

        g.fill(x, y, x + PANEL_W, y + panelH, 0xE0161616);
        g.renderOutline(x, y, PANEL_W, panelH, 0xFF404040);

        int tx = x + 6;
        int ty = y + 6;
        g.drawString(font, "STATS", tx, ty, 0xFFD9A227, false);
        ty += ROW_H + 2;

        ty = statLine(g, font, tx, ty, "STR", RpgHudOverlay.str);
        ty = statLine(g, font, tx, ty, "VIT", RpgHudOverlay.vit);
        ty = statLine(g, font, tx, ty, "DEX", RpgHudOverlay.dex);
        ty = statLine(g, font, tx, ty, "QUI", RpgHudOverlay.qui);
        ty = statLine(g, font, tx, ty, "INT", RpgHudOverlay.intel);
        ty = statLine(g, font, tx, ty, "WIS", RpgHudOverlay.wis);

        ty += 4;
        g.drawString(font, "HP  " + Math.round(RpgHudOverlay.hp) + "/" + Math.round(RpgHudOverlay.maxHp), tx, ty, RpgHudOverlay.COL_HP | 0xFF000000, false);
        ty += ROW_H;
        g.drawString(font, "STA " + Math.round(RpgHudOverlay.stam) + "/" + Math.round(RpgHudOverlay.maxStam), tx, ty, 0xFFD9A227, false);
        ty += ROW_H;
        g.drawString(font, "MAN " + Math.round(RpgHudOverlay.mana) + "/" + Math.round(RpgHudOverlay.maxMana), tx, ty, 0xFF4E8FD4, false);
    }

    private static int statLine(GuiGraphics g, Font font, int x, int y, String label, int value) {
        g.drawString(font, label, x, y, 0xFFAAAAAA, false);
        String v = String.valueOf(value);
        g.drawString(font, v, x + PANEL_W - 12 - font.width(v), y, 0xFFFFFFFF, false);
        return y + ROW_H;
    }
}
