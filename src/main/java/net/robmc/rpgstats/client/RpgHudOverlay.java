package net.robmc.rpgstats.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.network.SyncRpgStatsPacket;
import net.robmc.rpgstats.RpgStats;

/**
 * The three DAoC-style bars - red Health, yellow Stamina, blue Mana - stacked
 * just above the hotbar, each showing its number. Replaces the vanilla health,
 * hunger and air bars.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RpgHudOverlay {

    private static final int BAR_W = 120;
    private static final int BAR_H = 9;
    private static final int GAP = 2;

    private static final int COL_BG = 0xC0000000;
    private static final int COL_BORDER = 0xFF000000;
    private static final int COL_HP = 0xFFC0392B;
    private static final int COL_STAM = 0xFFC9A227;
    private static final int COL_MANA = 0xFF2E6DB4;

    private static float hp, maxHp = 300, stam, maxStam = 300, mana, maxMana = 300;

    public static void update(SyncRpgStatsPacket p) {
        hp = p.health;
        maxHp = Math.max(1, p.maxHealth);
        stam = p.stamina;
        maxStam = Math.max(1, p.maxStamina);
        mana = p.mana;
        maxMana = Math.max(1, p.maxMana);
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "rpg_bars", BARS);
    }

    private static final IGuiOverlay BARS = (gui, g, partialTick, screenW, screenH) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int totalH = BAR_H * 3 + GAP * 2;
        int left = (screenW - BAR_W) / 2;
        int top = screenH - 22 - 6 - totalH; // above the hotbar

        drawBar(g, mc.font, left, top, hp, maxHp, COL_HP);
        drawBar(g, mc.font, left, top + BAR_H + GAP, stam, maxStam, COL_STAM);
        drawBar(g, mc.font, left, top + 2 * (BAR_H + GAP), mana, maxMana, COL_MANA);
    };

    private static void drawBar(GuiGraphics g, Font font, int x, int y, float value, float max, int colour) {
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, COL_BORDER);
        g.fill(x, y, x + BAR_W, y + BAR_H, COL_BG);
        int fill = (int) (BAR_W * Math.max(0f, Math.min(1f, value / max)));
        g.fill(x, y, x + fill, y + BAR_H, colour);

        String text = Math.round(value) + " / " + Math.round(max);
        g.drawString(font, text, x + (BAR_W - font.width(text)) / 2, y + 1, 0xFFFFFFFF, true);
    }

    /** Hide the vanilla bars this replaces. */
    @Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
    public static class Hider {
        @SubscribeEvent
        public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
            var id = event.getOverlay().id();
            if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                    || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())) {
                event.setCanceled(true);
            }
        }
    }
}
