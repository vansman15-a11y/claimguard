package net.robmc.claimguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.ClanInvitePacket;

/** Type a player name to invite them to the clan. The player must be online. */
public class ClanInviteScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 110;

    private final String clanName;
    private EditBox nameBox;

    public ClanInviteScreen(String clanName) {
        super(Component.literal("Invite to Clan"));
        this.clanName = clanName;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = (this.height - PANEL_H) / 2;
        int fieldW = PANEL_W - 40;

        nameBox = new EditBox(this.font, cx - fieldW / 2, top + 40, fieldW, 18, Component.literal("Player name"));
        nameBox.setMaxLength(16);
        addRenderableWidget(nameBox);

        int btnW = fieldW / 2 - 3;
        addRenderableWidget(Button.builder(Component.literal("Invite"), b -> submit())
                .bounds(cx - fieldW / 2, top + PANEL_H - 26, btnW, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(cx - fieldW / 2 + btnW + 6, top + PANEL_H - 26, btnW, 20).build());

        setInitialFocus(nameBox);
    }

    private void submit() {
        String name = nameBox.getValue().trim();
        if (!name.isEmpty()) {
            ClaimGuardNetwork.CHANNEL.sendToServer(new ClanInvitePacket(name));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xD0100C1A);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF57C97A);
        g.drawCenteredString(this.font, "Invite to " + clanName, cx, top + 12, 0xFFFFFF);
        g.drawString(this.font, "Player name (must be online)", cx - (PANEL_W - 40) / 2, top + 30, 0xA9C7D6, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
