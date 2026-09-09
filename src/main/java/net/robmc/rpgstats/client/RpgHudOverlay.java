package net.robmc.rpgstats.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
 * The three DAoC-style bars - red Health, yellow Stamina, blue Mana - drawn just
 * above the hotbar (movable in the J editor), each showing its number. Replaces
 * the vanilla health, hunger and air bars. Also shifts the hotbar group by its
 * saved offset.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RpgHudOverlay {

    static final int BAR_W = 120;
    static final int BAR_H = 9;
    static final int GAP = 2;
    static final int BARS_TOTAL_H = BAR_H * 3 + GAP * 2;

    private static final int COL_BG = 0xC0000000;
    private static final int COL_BORDER = 0xFF000000;
    static final int COL_HP = 0xFFC0392B;
    static final int COL_STAM = 0xFFC9A227;
    static final int COL_MANA = 0xFF2E6DB4;

    static float hp, maxHp = 300, stam, maxStam = 300, mana, maxMana = 300;
    static int str, vit, dex, qui, intel, wis;
    public static boolean resting;
    public static boolean exhausted;

    public static void update(SyncRpgStatsPacket p) {
        hp = p.health;
        maxHp = Math.max(1, p.maxHealth);
        stam = p.stamina;
        maxStam = Math.max(1, p.maxStamina);
        mana = p.mana;
        maxMana = Math.max(1, p.maxMana);
        str = p.str;
        vit = p.vit;
        dex = p.dex;
        qui = p.qui;
        intel = p.intel;
        wis = p.wis;
        resting = p.resting;
        exhausted = p.exhausted;
    }

    /** Default top-left of the bar stack, before the layout offset. */
    static int defaultLeft(int screenW) {
        return (screenW - BAR_W) / 2;
    }

    static int defaultTop(int screenH) {
        return screenH - 22 - 6 - BARS_TOTAL_H;
    }

    // --- status line (movable on its own) ---
    public static final int STATUS_W = 90;
    public static final int STATUS_H = 11;

    public static int statusDefaultLeft(int screenW) {
        return (screenW - STATUS_W) / 2;
    }

    public static int statusDefaultTop(int screenH) {
        return defaultTop(screenH) - STATUS_H - 3;
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "rpg_bars", BARS);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "rpg_status", STATUS);
    }

    private static final IGuiOverlay BARS = (gui, g, partialTick, screenW, screenH) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        int left = defaultLeft(screenW) + HudLayout.offX(HudLayout.STAT_BARS);
        int top = defaultTop(screenH) + HudLayout.offY(HudLayout.STAT_BARS);
        renderBars(g, mc.font, left, top);
    };

    private static final IGuiOverlay STATUS = (gui, g, partialTick, screenW, screenH) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !exhausted) {
            return;
        }
        int left = statusDefaultLeft(screenW) + HudLayout.offX(HudLayout.STATUS);
        int top = statusDefaultTop(screenH) + HudLayout.offY(HudLayout.STATUS);
        renderStatus(g, mc.font, left, top);
    };

    /** Active RPG statuses. Currently just Exhausted; drawn centred in a STATUS_W-wide strip. */
    public static void renderStatus(GuiGraphics g, Font font, int left, int top) {
        if (!exhausted) {
            return;
        }
        String text = "EXHAUSTED";
        int w = font.width(text);
        int x = left + (STATUS_W - w) / 2;
        g.fill(x - 3, top - 1, x + w + 3, top + STATUS_H - 1, 0xC0000000);
        g.drawString(font, text, x, top + 1, 0xFFFF5555, true);
    }

    static void renderBars(GuiGraphics g, Font font, int left, int top) {
        drawBar(g, font, left, top, hp, maxHp, COL_HP);
        drawBar(g, font, left, top + BAR_H + GAP, stam, maxStam, COL_STAM);
        drawBar(g, font, left, top + 2 * (BAR_H + GAP), mana, maxMana, COL_MANA);
    }

    private static void drawBar(GuiGraphics g, Font font, int x, int y, float value, float max, int colour) {
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, COL_BORDER);
        g.fill(x, y, x + BAR_W, y + BAR_H, COL_BG);
        int fill = (int) (BAR_W * Math.max(0f, Math.min(1f, value / max)));
        g.fill(x, y, x + fill, y + BAR_H, colour);
        String text = Math.round(value) + " / " + Math.round(max);
        g.drawString(font, text, x + (BAR_W - font.width(text)) / 2, y + 1, 0xFFFFFFFF, true);
    }

    /** Hides vanilla bars, and nudges the hotbar group by its saved layout offset. */
    @Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
    public static class VanillaHudTweaks {

        @SubscribeEvent
        public static void pre(RenderGuiOverlayEvent.Pre event) {
            var id = event.getOverlay().id();
            if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())
                    || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                    || id.equals(VanillaGuiOverlay.AIR_LEVEL.id())) {
                event.setCanceled(true);
                return;
            }
            if (isHotbarGroup(id)) {
                PoseStack pose = event.getGuiGraphics().pose();
                pose.pushPose();
                pose.translate(HudLayout.offX(HudLayout.HOTBAR), HudLayout.offY(HudLayout.HOTBAR), 0);
            }
        }

        @SubscribeEvent
        public static void post(RenderGuiOverlayEvent.Post event) {
            if (isHotbarGroup(event.getOverlay().id())) {
                event.getGuiGraphics().pose().popPose();
            }
        }

        private static boolean isHotbarGroup(net.minecraft.resources.ResourceLocation id) {
            return id.equals(VanillaGuiOverlay.HOTBAR.id())
                    || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                    || id.equals(VanillaGuiOverlay.ITEM_NAME.id())
                    || id.equals(VanillaGuiOverlay.JUMP_BAR.id())
                    || id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id());
        }
    }
}
