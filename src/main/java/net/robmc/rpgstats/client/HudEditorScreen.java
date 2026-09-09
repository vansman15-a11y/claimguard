package net.robmc.rpgstats.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Press J to open: the movable HUD elements show with a labelled outline, drag to
 * reposition, J or Done to lock and save. Reset puts everything back.
 */
public class HudEditorScreen extends Screen {

    private static final int HOTBAR_W = 182;
    private static final int HOTBAR_H = 22;

    private String dragging;
    private int grabX, grabY, baseX, baseY;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        addRenderableWidget(Button.builder(Component.literal("Reset positions"), b -> HudLayout.reset())
                .bounds(cx - 122, this.height - 30, 120, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cx + 2, this.height - 30, 120, 20).build());
    }

    private int[] statBarsBox() {
        int x = RpgHudOverlay.defaultLeft(width) + HudLayout.offX(HudLayout.STAT_BARS);
        int y = RpgHudOverlay.defaultTop(height) + HudLayout.offY(HudLayout.STAT_BARS);
        return new int[]{x - 1, y - 1, RpgHudOverlay.BAR_W + 2, RpgHudOverlay.BARS_TOTAL_H + 2};
    }

    private int[] hotbarBox() {
        int x = (width - HOTBAR_W) / 2 + HudLayout.offX(HudLayout.HOTBAR);
        int y = height - HOTBAR_H + HudLayout.offY(HudLayout.HOTBAR);
        return new int[]{x, y, HOTBAR_W, HOTBAR_H};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            if (inside(statBarsBox(), mx, my)) {
                startDrag(HudLayout.STAT_BARS, mx, my);
                return true;
            }
            if (inside(hotbarBox(), mx, my)) {
                startDrag(HudLayout.HOTBAR, mx, my);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void startDrag(String element, double mx, double my) {
        dragging = element;
        grabX = (int) mx;
        grabY = (int) my;
        baseX = HudLayout.offX(element);
        baseY = HudLayout.offY(element);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) {
            HudLayout.set(dragging, baseX + (int) mx - grabX, baseY + (int) my - grabY);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x50000000);
        g.drawCenteredString(this.font, "HUD Editor  -  drag the boxes, then Done (or press J)", this.width / 2, 12, 0xFFFFFF);

        int[] bars = statBarsBox();
        RpgHudOverlay.renderBars(g, this.font, bars[0] + 1, bars[1] + 1);
        outline(g, bars, 0xFF57C97A, "Stat bars");

        int[] hb = hotbarBox();
        g.fill(hb[0], hb[1], hb[0] + hb[2], hb[1] + hb[3], 0x40FFFFFF);
        outline(g, hb, 0xFFFFC24B, "Hotbar");

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void outline(GuiGraphics g, int[] box, int colour, String label) {
        g.renderOutline(box[0], box[1], box[2], box[3], colour);
        g.drawString(this.font, label, box[0], box[1] - 10, colour, true);
    }

    private static boolean inside(int[] box, double mx, double my) {
        return mx >= box[0] && mx <= box[0] + box[2] && my >= box[1] && my <= box[1] + box[3];
    }

    @Override
    public void onClose() {
        HudLayout.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
