package net.robmc.claimguard.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.RespawnChoicePacket;

/**
 * Shown in place of the death screen when the player is bound to a bindstone AND
 * has a bed. Pick one; the choice is sent, then the respawn is triggered.
 */
public class RespawnChoiceScreen extends Screen {

    public RespawnChoiceScreen() {
        super(Component.literal("You Died"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        addRenderableWidget(Button.builder(Component.literal("Revive at Bindstone"), b -> choose(true))
                .bounds(cx - 100, cy, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Respawn at Bed"), b -> choose(false))
                .bounds(cx - 100, cy + 24, 200, 20).build());
    }

    private void choose(boolean atBind) {
        ClaimGuardNetwork.CHANNEL.sendToServer(new RespawnChoicePacket(atBind));
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.respawn();
        }
        if (minecraft != null) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, "You died", this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        g.drawCenteredString(this.font, "Where do you want to come back?", this.width / 2, this.height / 2 - 24, 0xA9C7D6);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
