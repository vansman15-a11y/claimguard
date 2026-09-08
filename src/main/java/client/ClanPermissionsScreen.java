package net.robmc.claimguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.ClanMemberActionPacket;

import java.util.UUID;

/**
 * Per-member permissions, opened from the roster's right-click menu. For now the
 * one toggle is building access inside the clan's claimed areas.
 */
public class ClanPermissionsScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 130;

    private final UUID memberId;
    private final String memberName;
    private boolean canBuild;

    public ClanPermissionsScreen(UUID memberId, String memberName, boolean canBuild) {
        super(Component.literal("Member Permissions"));
        this.memberId = memberId;
        this.memberName = memberName;
        this.canBuild = canBuild;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = (this.height - PANEL_H) / 2;

        addRenderableWidget(Button.builder(buildToggleLabel(), b -> {
            ClaimGuardNetwork.CHANNEL.sendToServer(new ClanMemberActionPacket(memberId, ClanMemberActionPacket.Action.TOGGLE_BUILD));
            canBuild = !canBuild; // optimistic; server re-checks and messages
            rebuildWidgets();
        }).bounds(cx - 100, top + 46, 200, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(cx - 60, top + PANEL_H - 26, 120, 20).build());
    }

    private Component buildToggleLabel() {
        return Component.literal("Building access:  " + (canBuild ? "ON" : "OFF"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = (this.height - PANEL_H) / 2;
        g.fill(left, top, left + PANEL_W, top + PANEL_H, 0xD0100C1A);
        g.renderOutline(left, top, PANEL_W, PANEL_H, 0xFF57C97A);
        g.drawCenteredString(this.font, "Permissions - " + memberName, cx, top + 12, 0xFFFFFF);
        g.drawCenteredString(this.font, "Can edit blocks in the clan's claims", cx, top + 30, 0x8FA0AA);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
