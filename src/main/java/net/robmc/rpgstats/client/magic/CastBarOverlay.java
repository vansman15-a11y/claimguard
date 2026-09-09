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
import net.robmc.rpgstats.client.HudLayout;
import net.robmc.rpgstats.magic.Spell;

/** A centred progress bar shown while casting, DAoC / Rise of Agon style. Movable in the J editor. */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CastBarOverlay {

    public static final int W = 150;
    public static final int H = 8;

    private CastBarOverlay() {
    }

    public static int defaultLeft(int screenW) {
        return (screenW - W) / 2;
    }

    public static int defaultTop(int screenH) {
        return screenH / 2 - 42;
    }

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "rpg_cast_bar", BAR);
    }

    private static final IGuiOverlay BAR = (gui, g, partialTick, screenW, screenH) -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientSpells.isCasting()) {
            return;
        }
        int x = defaultLeft(screenW) + HudLayout.offX(HudLayout.CAST_BAR);
        int y = defaultTop(screenH) + HudLayout.offY(HudLayout.CAST_BAR);
        render(g, mc.font, x, y, ClientSpells.castingSpell(), ClientSpells.castProgress(), ClientSpells.isCharged());
    };

    public static void render(GuiGraphics g, Font font, int x, int y, Spell spell, float progress, boolean charged) {
        String name = spell != null ? spell.displayName() : "Casting";
        if (charged) {
            name = name + "  -  release to cast";
        }
        g.drawString(font, name, x + (W - font.width(name)) / 2, y - 11,
                charged ? 0xFFA8F0A0 : 0xFFE8E8E8, true);

        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, 0xFF000000);
        g.fill(x, y, x + W, y + H, 0xC0202024);
        int fill = (int) (W * Math.max(0f, Math.min(1f, progress)));
        g.fill(x, y, x + fill, y + H, charged ? 0xFF6FD36A : 0xFFEAD37A);
        g.renderOutline(x, y, W, H, charged ? 0xFFB8F0B0 : 0xFF5A5A5A);
    }
}
