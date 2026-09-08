package net.robmc.claimguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Leader-only: set the clan's daily raid window start (24-hour HH:MM, server
 * local time). The window is always 3 hours long. Runs the /clan raidwindow
 * command so there's no extra packet.
 */
public class ClanRaidWindowScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 130;

    private final String clanName;
    private final String currentWindow;
    private EditBox timeBox;

    public ClanRaidWindowScreen(String clanName, String currentWindow) {
        super(Component.literal("Raid Window"));
        this.clanName = clanName;
        this.currentWindow = currentWindow;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = (this.height - PANEL_H) / 2;
        int fieldW = 90;

        timeBox = new EditBox(this.font, cx - fieldW / 2, top + 50, fieldW, 18, Component.literal("HH:MM"));
        timeBox.setMaxLength(5);
        timeBox.setHint(Component.literal("19:00"));
        if (currentWindow != null && currentWindow.contains("-")) {
            timeBox.setValue(currentWindow.substring(0, currentWindow.indexOf('-')));
        }
        addRenderableWidget(timeBox);

        int btnW = 118;
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            String v = timeBox.getValue().trim();
            if (!v.isEmpty() && minecraft != null && minecraft.player != null) {
                minecraft.player.connection.sendCommand("clan raidwindow " + v);
            }
            onClose();
        }).bounds(cx - btnW - 3, top + PANEL_H - 26, btnW, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(cx + 3, top + PANEL_H - 26, btnW, 20).build());

        setInitialFocus(timeBox);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xD0100C1A);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF57C97A);

        g.drawCenteredString(this.font, clanName + " - Raid Window", cx, top + 10, 0xFFFFFF);
        g.drawCenteredString(this.font, "Start time (24h, server local). Window is 3 hours.", cx, top + 28, 0x8FA0AA);
        g.drawCenteredString(this.font, "Currently: " + currentWindow, cx, top + 74, 0xA9C7D6);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
