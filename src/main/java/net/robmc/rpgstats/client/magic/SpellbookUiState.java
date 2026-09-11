package net.robmc.rpgstats.client.magic;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.robmc.rpgstats.RpgStats;

import java.util.HashSet;
import java.util.Set;

/**
 * Which Spellbook / Skills Book rows are expanded and how far it's scrolled -
 * kept alive across opening and closing the U screen so it "remembers where you
 * left off" for as long as you're logged on. Starts empty (nothing expanded)
 * and is wiped the moment you log out, so a fresh login always starts clean.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID, value = Dist.CLIENT)
public final class SpellbookUiState {

    private static final Set<String> expanded = new HashSet<>();
    private static int scroll;

    private SpellbookUiState() {
    }

    /** Live, mutable - callers add/remove directly to persist a change. */
    public static Set<String> expanded() {
        return expanded;
    }

    public static int scroll() {
        return scroll;
    }

    public static void setScroll(int value) {
        scroll = value;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        expanded.clear();
        scroll = 0;
    }
}
