package net.robmc.rpgstats.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.claimguard.network.CastSpellPacket;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.rpgstats.RpgStats;
import net.robmc.rpgstats.StatFormulas;
import net.robmc.rpgstats.client.magic.SpellbookScreen;
import org.lwjgl.glfw.GLFW;

/** J = HUD editor, U = spellbook. Bar 1 slots = 1-9, bar 2 slots = Alt+1-9. All rebindable in the J editor. */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RpgKeybinds {

    private static final String CATEGORY = "key.categories.rpgstats";

    public static final KeyMapping HUD_EDITOR = key("hud_editor", GLFW.GLFW_KEY_J);
    public static final KeyMapping SPELLBOOK = key("spellbook", GLFW.GLFW_KEY_U);
    public static final KeyMapping[] CAST = new KeyMapping[StatFormulas.TOTAL_BAR_SLOTS];

    static {
        for (int i = 0; i < CAST.length; i++) {
            int digit = GLFW.GLFW_KEY_1 + (i % StatFormulas.BAR_SLOTS);
            KeyModifier mod = i < StatFormulas.BAR_SLOTS ? KeyModifier.NONE : KeyModifier.ALT;
            CAST[i] = key("cast_" + (i + 1), mod, digit); // bar 1: plain 1-9, bar 2: Alt+1-9
        }
    }

    private static KeyMapping key(String name, int code) {
        return key(name, KeyModifier.NONE, code);
    }

    private static KeyMapping key(String name, KeyModifier modifier, int code) {
        return new KeyMapping("key.rpgstats." + name, KeyConflictContext.IN_GAME, modifier,
                InputConstants.Type.KEYSYM.getOrCreate(code), CATEGORY);
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

        /** The bar slot whose key is currently being held for a charged cast, or -1. */
        private static int heldSlot = -1;
        private static int heldSinceTick = 0;

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

            // hold-to-charge: press starts the cast, releasing the key fires it
            if (heldSlot >= 0 && (mc.screen != null || mc.player == null || !CAST[heldSlot].isDown())) {
                ClaimGuardNetwork.CHANNEL.sendToServer(new CastSpellPacket(heldSlot, true));
                net.robmc.rpgstats.client.magic.ClientSpells.setCastHeld(false);
                heldSlot = -1;
            } else if (heldSlot >= 0 && mc.player != null
                    && mc.player.tickCount - heldSinceTick > 10
                    && !net.robmc.rpgstats.client.magic.ClientSpells.isCasting()) {
                // the server cancelled it (interrupted / rejected) - stop tracking without a release
                net.robmc.rpgstats.client.magic.ClientSpells.setCastHeld(false);
                heldSlot = -1;
            }

            if (mc.screen == null && mc.player != null && heldSlot < 0) {
                for (int i = 0; i < CAST.length; i++) {
                    boolean pressed = false;
                    while (CAST[i].consumeClick()) {
                        pressed = true;
                    }
                    if (pressed && heldSlot < 0) {
                        heldSlot = i;
                        heldSinceTick = mc.player.tickCount;
                        net.robmc.rpgstats.client.magic.ClientSpells.setCastHeld(true);
                        ClaimGuardNetwork.CHANNEL.sendToServer(new CastSpellPacket(i, false));
                    }
                }
            }
        }
    }
}
