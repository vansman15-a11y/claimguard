package net.robmc.claimguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.CreateClanPacket;

/**
 * "CREATE CLAN" - name, tag and motto fields, then Create / Cancel. The server
 * does the real validation; this only stops an obviously-empty submit.
 */
public class CreateClanScreen extends Screen {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 210;
    private static final int PANEL_BG = 0xD0100C1A;
    private static final int PANEL_BORDER = 0xFF57C97A;
    private static final int FIELD_W = PANEL_W - 40;

    private EditBox nameBox;
    private EditBox tagBox;
    private EditBox mottoBox;

    public CreateClanScreen() {
        super(Component.literal("Create Clan"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - FIELD_W / 2;
        int top = (this.height - PANEL_H) / 2;

        nameBox = new EditBox(this.font, left, top + 34, FIELD_W, 18, Component.literal("Clan Name"));
        nameBox.setMaxLength(24);
        addRenderableWidget(nameBox);

        tagBox = new EditBox(this.font, left, top + 76, FIELD_W, 18, Component.literal("Clan Tag"));
        tagBox.setMaxLength(5);
        addRenderableWidget(tagBox);

        mottoBox = new EditBox(this.font, left, top + 118, FIELD_W, 18, Component.literal("Motto"));
        mottoBox.setMaxLength(120);
        addRenderableWidget(mottoBox);

        int btnW = FIELD_W / 2 - 3;
        addRenderableWidget(Button.builder(Component.literal("Create"), b -> submit())
                .bounds(left, top + PANEL_H - 28, btnW, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(left + btnW + 6, top + PANEL_H - 28, btnW, 20).build());

        setInitialFocus(nameBox);
    }

    private void submit() {
        String name = nameBox.getValue().trim();
        String tag = tagBox.getValue().trim();
        if (name.isEmpty() || tag.isEmpty()) {
            return;
        }
        ClaimGuardNetwork.CHANNEL.sendToServer(new CreateClanPacket(name, tag, mottoBox.getValue().trim()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, PANEL_BG);
        g.renderOutline(left, top, PANEL_W, PANEL_H, PANEL_BORDER);

        g.drawCenteredString(this.font, "CREATE CLAN", cx, top + 10, 0xFFFFFF);

        int fieldLeft = cx - FIELD_W / 2;
        g.drawString(this.font, "Clan Name", fieldLeft, top + 24, 0xA9C7D6, false);
        g.drawString(this.font, "Clan Tag", fieldLeft, top + 66, 0xA9C7D6, false);
        g.drawString(this.font, "Motto", fieldLeft, top + 108, 0xA9C7D6, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
