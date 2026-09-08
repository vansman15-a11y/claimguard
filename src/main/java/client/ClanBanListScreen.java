package net.robmc.claimguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.ClanUnbanPacket;
import net.robmc.claimguard.network.OpenClanBanListPacket;

import java.util.List;

/** The clan's ban list. A leader gets an Unban button on each row. */
public class ClanBanListScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 210;
    private static final int ROW_H = 18;

    private final String clanName;
    private final boolean canUnban;
    private final List<OpenClanBanListPacket.BanRow> banned;

    public ClanBanListScreen(OpenClanBanListPacket data) {
        super(Component.literal("Clan Ban List"));
        this.clanName = data.clanName();
        this.canUnban = data.canUnban();
        this.banned = data.banned();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;

        int y = top + 40;
        for (OpenClanBanListPacket.BanRow row : banned) {
            if (canUnban) {
                addRenderableWidget(Button.builder(Component.literal("Unban"), b ->
                        ClaimGuardNetwork.CHANNEL.sendToServer(new ClanUnbanPacket(row.id()))
                ).bounds(left + PANEL_W - 74, y - 2, 58, 16).build());
            }
            y += ROW_H;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(cx - 60, top + PANEL_H - 26, 120, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xD0100C1A);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF57C97A);

        g.drawCenteredString(this.font, clanName.toUpperCase() + " - BANNED", cx, top + 12, 0xFFFFFF);

        if (banned.isEmpty()) {
            g.drawCenteredString(this.font, "Nobody is banned.", cx, top + 44, 0xFF8FA0AA);
        } else {
            int y = top + 40;
            for (OpenClanBanListPacket.BanRow row : banned) {
                g.drawString(this.font, row.name(), left + 16, y, 0xFFDDDDDD, false);
                y += ROW_H;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
