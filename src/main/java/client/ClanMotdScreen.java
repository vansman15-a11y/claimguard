package net.robmc.claimguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.SetClanMotdPacket;

import java.util.List;

/**
 * The clan message of the day. Everyone can read it; a leader or officer gets an
 * edit field and a Save button.
 */
public class ClanMotdScreen extends Screen {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 170;

    private final String clanName;
    private final String motd;
    private final boolean editable;
    private EditBox editBox;

    public ClanMotdScreen(String clanName, String motd, boolean editable) {
        super(Component.literal("Clan MOTD"));
        this.clanName = clanName;
        this.motd = motd == null ? "" : motd;
        this.editable = editable;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = (this.height - PANEL_H) / 2;
        int fieldW = PANEL_W - 40;

        if (editable) {
            editBox = new EditBox(this.font, cx - fieldW / 2, top + 60, fieldW, 18, Component.literal("MOTD"));
            editBox.setMaxLength(120);
            editBox.setValue(motd);
            addRenderableWidget(editBox);

            int btnW = fieldW / 2 - 3;
            addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
                ClaimGuardNetwork.CHANNEL.sendToServer(new SetClanMotdPacket(editBox.getValue().trim()));
                onClose();
            }).bounds(cx - fieldW / 2, top + PANEL_H - 26, btnW, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                    .bounds(cx - fieldW / 2 + btnW + 6, top + PANEL_H - 26, btnW, 20).build());
        } else {
            addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                    .bounds(cx - 60, top + PANEL_H - 26, 120, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xD0100C1A);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF57C97A);

        g.drawCenteredString(this.font, clanName + " - MOTD", cx, top + 12, 0xFFFFFF);

        String shown = motd.isBlank() ? "(no message of the day set)" : motd;
        List<net.minecraft.util.FormattedCharSequence> lines =
                this.font.split(Component.literal(shown), PANEL_W - 32);
        int ty = top + 30;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            g.drawString(this.font, line, left + 16, ty, motd.isBlank() ? 0xFF8FA0AA : 0xFFDDDDDD, false);
            ty += 11;
        }

        if (editable) {
            g.drawString(this.font, "Edit:", cx - (PANEL_W - 40) / 2, top + 50, 0xA9C7D6, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
