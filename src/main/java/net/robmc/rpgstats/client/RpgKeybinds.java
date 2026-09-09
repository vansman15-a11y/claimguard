package net.robmc.rpgstats.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.network.CastSpellPacket;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.client.magic.ClientRecall;
import net.robmc.rpgstats.client.magic.ClientSpells;
import net.robmc.rpgstats.client.magic.SpellbookScreen;
import net.robmc.rpgstats.skill.Skill;
import org.lwjgl.glfw.GLFW;

/** J = HUD editor, U = spellbook, numpad 1-8 = cast spell-bar slot (all rebindable). */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RpgKeybinds {

    private static final String CATEGORY = "key.categories.rpgstats";

    public static final KeyMapping HUD_EDITOR = key("hud_editor", GLFW.GLFW_KEY_J);
    public static final KeyMapping SPELLBOOK = key("spellbook", GLFW.GLFW_KEY_U);
    public static final KeyMapping[] CAST = new KeyMapping[8];

    static {
        for (int i = 0; i < 8; i++) {
            CAST[i] = key("cast_" + (i + 1), GLFW.GLFW_KEY_1 + i); // plain 1..8 by default
        }
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.rpgstats." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, code, CATEGORY);
    }

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.register(HUD_EDITOR);
        event.register(SPELLBOOK);
        for (KeyMapping k : CAST) {
            event.register(k);
        }
    }

    @Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
    public static class Handler {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();

            while (HUD_EDITOR.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new HudEditorScreen());
                } else if (mc.screen instanceof HudEditorScreen) {
                    mc.setScreen(null);
                }
            }
            while (SPELLBOOK.consumeClick()) {
                if (mc.screen == null) {
                    mc.setScreen(new SpellbookScreen());
                } else if (mc.screen instanceof SpellbookScreen) {
                    mc.setScreen(null);
                }
            }
            if (mc.screen == null && mc.player != null) {
                for (int i = 0; i < 8; i++) {
                    while (CAST[i].consumeClick()) {
                        activateSlot(mc, i);
                    }
                }
            }
        }

        private static void activateSlot(Minecraft mc, int slot) {
            // Recall asks first (and only when starting - pressing it again mid-channel cancels).
            if (ClientSpells.skillAt(slot) == Skill.RECALL && !ClientRecall.isRecalling()) {
                mc.setScreen(new ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                ClaimGuardNetwork.CHANNEL.sendToServer(new CastSpellPacket(slot));
                            }
                            mc.setScreen(null);
                        },
                        Component.literal("Recall to Bindstone"),
                        Component.literal("Channel for 1 minute, then teleport to your bindstone. Taking a hit cancels it.")));
                return;
            }
            ClaimGuardNetwork.CHANNEL.sendToServer(new CastSpellPacket(slot));
        }
    }
}
