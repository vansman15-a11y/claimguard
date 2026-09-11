package net.robmc.rpgstats.client.magic;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.client.HudLayout;
import net.robmc.rpgstats.magic.Spell;

/**
 * Two 9-slot casting bars, vertical by default (left edge of the screen), each with two
 * extra tiles attached past its 9th slot: a "currently selected" readout (whatever the
 * last-pressed slot on that bar would fire) and, right after it, the weapon that's set to
 * auto-equip for it. Movable, and - via the J editor's right-click bar menu - each can be
 * flipped horizontal and given its own background opacity (which fades everything in the
 * bar, icons included, not just the slot backgrounds).
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SpellBarOverlay {

    public static final int SLOT = 20;
    public static final int SLOTS = StatFormulas.BAR_SLOTS;   // per bar
    public static final int BAR_H = SLOT * SLOTS;             // the 9 cast slots' span along their own axis
    private static final int SELECTED_GAP = 4;                // small gap between slot 9 and the "selected" readout

    /** Anchor for a bar: bar 0 sits at the edge, bar 1 just to its right. */
    public static int defaultLeft(int bar) {
        return 8 + bar * (SLOT + 3);
    }

    public static String layoutKey(int bar) {
        return bar == 0 ? HudLayout.SPELL_BAR : HudLayout.SPELL_BAR_2;
    }

    public static int defaultTop(int screenH) {
        return (screenH - BAR_H) / 2;
    }

    /** First global slot index of a bar. */
    public static int firstSlot(int bar) {
        return bar * SLOTS;
    }

    /**
     * Which global slot (0..17) sits at this screen position right now, accounting for each
     * bar's saved position/orientation, or -1. Shared by the J editor's own hit-testing and by
     * anything reaching in from outside it (e.g. dragging a weapon off the survival inventory).
     */
    public static int slotAt(double mx, double my, int screenH) {
        for (int bar = 0; bar < StatFormulas.BAR_COUNT; bar++) {
            String key = layoutKey(bar);
            boolean horiz = HudLayout.isHorizontal(key);
            int sx = defaultLeft(bar) + HudLayout.offX(key);
            int sy = defaultTop(screenH) + HudLayout.offY(key);
            int i;
            if (horiz) {
                if (my < sy || my > sy + SLOT) {
                    continue;
                }
                i = (int) ((mx - sx) / SLOT);
            } else {
                if (mx < sx || mx > sx + SLOT) {
                    continue;
                }
                i = (int) ((my - sy) / SLOT);
            }
            if (i >= 0 && i < SLOTS) {
                return bar * SLOTS + i;
            }
        }
        return -1;
    }

    /** Only Bar 1 gets the trailing "currently selected" + weapon readout tiles. */
    private static boolean hasReadout(String layoutKey) {
        return layoutKey.equals(HudLayout.SPELL_BAR);
    }

    /** The bar's on-screen width, accounting for its saved orientation - includes the readout tiles on Bar 1. */
    public static int width(String layoutKey) {
        int extra = hasReadout(layoutKey) ? SELECTED_GAP + SLOT * 2 : 0;
        return HudLayout.isHorizontal(layoutKey) ? BAR_H + extra : SLOT;
    }

    /** The bar's on-screen height, accounting for its saved orientation - includes the readout tiles on Bar 1. */
    public static int height(String layoutKey) {
        int extra = hasReadout(layoutKey) ? SELECTED_GAP + SLOT * 2 : 0;
        return HudLayout.isHorizontal(layoutKey) ? SLOT : BAR_H + extra;
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "rpg_spell_bar", BAR);
    }

    private static final IGuiOverlay BAR = (gui, g, partialTick, screenW, screenH) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        for (int bar = 0; bar < StatFormulas.BAR_COUNT; bar++) {
            String key = layoutKey(bar);
            int x = defaultLeft(bar) + HudLayout.offX(key);
            int y = defaultTop(screenH) + HudLayout.offY(key);
            render(g, mc.font, x, y, firstSlot(bar), key);
        }

        if (ClientRecall.isRecalling()) {
            g.drawCenteredString(mc.font, "Recalling  " + ClientRecall.secondsLeft() + "s",
                    screenW / 2, screenH / 2 + 24, 0xFF66D9FF);
        }
    };

    /**
     * Render one bar's SLOTS slots starting from global slot {@code firstSlot}, laid out per
     * its saved orientation/opacity, followed by its "currently selected" readout tile and
     * that ability's weapon tile.
     */
    public static void render(GuiGraphics g, Font font, int x, int y, int firstSlot, String layoutKey) {
        boolean horiz = HudLayout.isHorizontal(layoutKey);
        float opacity = HudLayout.opacity(layoutKey);
        for (int i = 0; i < SLOTS; i++) {
            int sx = horiz ? x + i * SLOT : x;
            int sy = horiz ? y : y + i * SLOT;
            drawSlot(g, font, sx, sy, firstSlot + i, opacity);
        }

        if (!hasReadout(layoutKey)) {
            return; // only Bar 1 shows the "currently selected" + weapon readout
        }
        int selX = horiz ? x + SLOTS * SLOT + SELECTED_GAP : x;
        int selY = horiz ? y : y + SLOTS * SLOT + SELECTED_GAP;
        drawTile(g, font, selX, selY, ClientSpells.selectedSpell(), opacity, 0x00C9A227);

        int wepX = horiz ? selX + SLOT : selX;
        int wepY = horiz ? selY : selY + SLOT;
        drawWeaponTile(g, wepX, wepY, ClientSpells.selectedWeaponId(), opacity);
    }

    static void drawSlot(GuiGraphics g, Font font, int x, int y, int index, float opacity) {
        Spell spell = ClientSpells.activeSlot(index);
        drawTile(g, font, x, y, spell, opacity, 0x003A3A3A);
        if (spell == null) {
            g.drawString(font, String.valueOf(index % SLOTS + 1), x + SLOT - 6, y + SLOT - 8, fade(0xFF808080, opacity), false);
            return;
        }

        // stacked "ray bar" slot - a small count badge, bottom-left
        int count = ClientSpells.slotBindCount(index);
        if (count > 1) {
            String tag = "x" + count;
            g.pose().pushPose();
            g.pose().translate(x + 1.0f, y + SLOT - 9.0f, 0.0f);
            g.pose().scale(0.7f, 0.7f, 1.0f);
            g.drawString(font, tag, 0, 0, fade(0xFF9FC0FF, opacity), true);
            g.pose().popPose();
        }

        // this slot force-equips a weapon before firing - a tiny icon, top-right
        String weaponId = ClientSpells.slotForceWeapon(index) ? ClientSpells.slotWeaponId(index) : null;
        if (weaponId != null) {
            drawItem(g, weaponId, x + SLOT - 9, y + 1, 0.55f, opacity);
        }

        g.drawString(font, String.valueOf(index % SLOTS + 1), x + SLOT - 6, y + SLOT - 8, fade(0xFF808080, opacity), false);
    }

    /** The shared tile look for a bindable cast slot and the "currently selected" readout - background, icon, and every state overlay. */
    private static void drawTile(GuiGraphics g, Font font, int x, int y, Spell spell, float opacity, int borderRGB) {
        int bgAlpha = (int) (0xC0 * opacity);
        int borderAlpha = (int) (0xFF * opacity);
        g.fill(x, y, x + SLOT, y + SLOT, (bgAlpha << 24) | 0x00101010);
        g.renderOutline(x, y, SLOT, SLOT, (borderAlpha << 24) | borderRGB);

        if (spell == null) {
            return;
        }
        SpellIcons.draw(g, spell, x + 2, y + 2, SLOT - 4, opacity);

        if (!ClientSpells.unlocked(spell)) {
            g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, fade(0xB0101014, opacity)); // locked - dimmed
        }

        // On cooldown: the slot sits "lit" just after the cast and fades back to normal as it recharges.
        float cdp = ClientSpells.cooldownProgress(spell);
        if (cdp < 1.0f) {
            int a = (int) (0x8C * (1.0f - cdp) * opacity);
            g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, (a << 24) | 0x00FFE2A6);
        }

        // Off cooldown: a very short, subtle flash to say "ready".
        float flash = ClientSpells.readyFlash(spell);
        if (flash > 0.0f) {
            g.fill(x, y, x + SLOT, y + SLOT, ((int) (0x3C * flash * opacity) << 24) | 0x00FFFFFF);
            g.renderOutline(x, y, SLOT, SLOT, ((int) (0xC0 * flash * opacity) << 24) | 0x00FFFFFF);
        }

        // Casting now: a fill that rises from the bottom.
        if (ClientSpells.isCasting() && ClientSpells.castingSpell() == spell) {
            int h = (int) (SLOT * ClientSpells.castProgress());
            g.fill(x, y + SLOT - h, x + SLOT, y + SLOT, fade(0x8055C9FF, opacity));
        }

        cornerLevel(g, font, x, y, ClientSpells.spellLevel(spell), fade(0xFFFFE066, opacity));
    }

    /** The trailing "what weapon does this ability use" tile - just an item icon, no cooldown/cast overlays. */
    private static void drawWeaponTile(GuiGraphics g, int x, int y, String weaponId, float opacity) {
        int bgAlpha = (int) (0xC0 * opacity);
        int borderAlpha = (int) (0xFF * opacity);
        g.fill(x, y, x + SLOT, y + SLOT, (bgAlpha << 24) | 0x00101010);
        g.renderOutline(x, y, SLOT, SLOT, (borderAlpha << 24) | 0x006A7A8A);
        if (weaponId != null) {
            drawItem(g, weaponId, x + 2, y + 2, 1.0f, opacity);
        }
    }

    private static void drawItem(GuiGraphics g, String itemId, int x, int y, float scale, float opacity) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
        if (item == null) {
            return;
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, opacity);
        g.pose().pushPose();
        g.pose().translate(x, y, 100);
        g.pose().scale(scale, scale, 1.0f);
        g.renderItem(new ItemStack(item), 0, 0);
        g.pose().popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** Scales an ARGB color's alpha channel by {@code opacity}. */
    private static int fade(int argb, float opacity) {
        int a = (int) (((argb >>> 24) & 0xFF) * opacity);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** The small level number tucked into a slot's top-left corner. */
    private static void cornerLevel(GuiGraphics g, Font font, int x, int y, int level, int color) {
        g.pose().pushPose();
        g.pose().translate(x + 1.0f, y + 0.5f, 0.0f);
        g.pose().scale(0.7f, 0.7f, 1.0f);
        g.drawString(font, Integer.toString(level), 0, 0, color, true);
        g.pose().popPose();
    }
}
