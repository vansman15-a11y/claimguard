package net.robmc.rpgstats.client.magic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.client.HudLayout;
import net.robmc.rpgstats.magic.Spell;

/**
 * Two 9-slot casting bars, vertical by default (left edge of the screen).
 * Movable, and - via the J editor's right-click bar menu - each can be flipped
 * horizontal and given its own background opacity.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SpellBarOverlay {

    public static final int SLOT = 20;
    public static final int SLOTS = StatFormulas.BAR_SLOTS;   // per bar
    public static final int BAR_H = SLOT * SLOTS;             // the bar's span along its own axis

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

    /** The bar's on-screen width, accounting for its saved orientation. */
    public static int width(String layoutKey) {
        return HudLayout.isHorizontal(layoutKey) ? BAR_H : SLOT;
    }

    /** The bar's on-screen height, accounting for its saved orientation. */
    public static int height(String layoutKey) {
        return HudLayout.isHorizontal(layoutKey) ? SLOT : BAR_H;
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

    /** Render one bar's SLOTS slots starting from global slot {@code firstSlot}, laid out per its saved orientation/opacity. */
    public static void render(GuiGraphics g, Font font, int x, int y, int firstSlot, String layoutKey) {
        boolean horiz = HudLayout.isHorizontal(layoutKey);
        float opacity = HudLayout.opacity(layoutKey);
        for (int i = 0; i < SLOTS; i++) {
            int sx = horiz ? x + i * SLOT : x;
            int sy = horiz ? y : y + i * SLOT;
            drawSlot(g, font, sx, sy, firstSlot + i, opacity);
        }
    }

    static void drawSlot(GuiGraphics g, Font font, int x, int y, int index, float opacity) {
        int bgAlpha = (int) (0xC0 * opacity);
        int borderAlpha = (int) (0xFF * opacity);
        g.fill(x, y, x + SLOT, y + SLOT, (bgAlpha << 24) | 0x00101010);
        g.renderOutline(x, y, SLOT, SLOT, (borderAlpha << 24) | 0x003A3A3A);

        Spell spell = ClientSpells.activeSlot(index);
        if (spell != null) {
            SpellIcons.draw(g, spell, x + 2, y + 2, SLOT - 4);

            if (!ClientSpells.unlocked(spell)) {
                g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xB0101014); // locked - dimmed
            }

            // On cooldown: the slot sits "lit" just after the cast and fades back to normal as it recharges.
            float cdp = ClientSpells.cooldownProgress(spell);
            if (cdp < 1.0f) {
                int a = (int) (0x8C * (1.0f - cdp));
                g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, (a << 24) | 0x00FFE2A6);
            }

            // Off cooldown: a very short, subtle flash to say "ready".
            float flash = ClientSpells.readyFlash(spell);
            if (flash > 0.0f) {
                g.fill(x, y, x + SLOT, y + SLOT, ((int) (0x3C * flash) << 24) | 0x00FFFFFF);
                g.renderOutline(x, y, SLOT, SLOT, ((int) (0xC0 * flash) << 24) | 0x00FFFFFF);
            }

            // Casting now: a fill that rises from the bottom.
            if (ClientSpells.isCasting() && ClientSpells.castingSpell() == spell) {
                int h = (int) (SLOT * ClientSpells.castProgress());
                g.fill(x, y + SLOT - h, x + SLOT, y + SLOT, 0x8055C9FF);
            }

            cornerLevel(g, font, x, y, ClientSpells.spellLevel(spell), 0xFFFFE066);
        }

        // stacked "ray bar" slot - a small count badge, bottom-left
        int count = ClientSpells.slotBindCount(index);
        if (count > 1) {
            String tag = "x" + count;
            g.pose().pushPose();
            g.pose().translate(x + 1.0f, y + SLOT - 9.0f, 0.0f);
            g.pose().scale(0.7f, 0.7f, 1.0f);
            g.drawString(font, tag, 0, 0, 0xFF9FC0FF, true);
            g.pose().popPose();
        }

        // this slot force-equips a weapon before firing - a tiny icon, bottom-right
        String weaponId = ClientSpells.slotForceWeapon(index) ? ClientSpells.slotWeaponId(index) : null;
        if (weaponId != null) {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    net.minecraft.resources.ResourceLocation.tryParse(weaponId));
            if (item != null) {
                g.pose().pushPose();
                g.pose().translate(x + SLOT - 9, y + 1, 100);
                g.pose().scale(0.55f, 0.55f, 1.0f);
                g.renderItem(new net.minecraft.world.item.ItemStack(item), 0, 0);
                g.pose().popPose();
            }
        }

        g.drawString(font, String.valueOf(index % SLOTS + 1), x + SLOT - 6, y + SLOT - 8, 0xFF808080, false);
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
