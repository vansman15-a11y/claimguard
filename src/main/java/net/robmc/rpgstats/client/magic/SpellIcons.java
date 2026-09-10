package net.robmc.rpgstats.client.magic;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.robmc.rpgstats.magic.Spell;

/** The 32x32 spell art in assets/rpgstats/textures/spell/, and a helper to blit it at any size. */
public final class SpellIcons {

    private static final int TEX = 32;

    private SpellIcons() {
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation("rpgstats", "textures/spell/" + name + ".png");
    }

    public static ResourceLocation of(Spell spell) {
        return switch (spell) {
            case MANA_TO_STAMINA -> tex("mana_to_stamina");
            case STAMINA_TO_HEALTH -> tex("stamina_to_health");
            case HEALTH_TO_MANA -> tex("health_to_mana");
            case MAGIC_BOLT -> tex("magic_bolt");
            case SUNDER -> tex("sunder");
            case HEAL_OTHER -> tex("heal_other");
            case AWAY -> tex("away");
            case WARD -> tex("ward");
            case BRIGHT_LIGHT -> tex("bright_light");
            case EMBER_DART -> tex("ember_dart");
            case SERPENTS_PLUME -> tex("serpents_plume");
            case SUNBURST -> tex("sunburst");
            case PYROCLASM -> tex("pyroclasm");
            case CINDER_MAELSTROM -> tex("cinder_maelstrom");
            case HEARTWELL -> tex("heartwell");
            case WITHER -> tex("wither");
            case SLUMP -> tex("slump");
            case PESTILENCE -> tex("pestilence");
            case HEXDRAIN -> tex("hexdrain");
            case LIGHTNING_STRIKE -> tex("lightning_strike");
            case SPEED_OF_WIND -> tex("speed_of_wind");
            case CHAIN_SHOCK -> tex("chain_shock");
            case WIND_LURE -> tex("wind_lure");
            case HOWLING_IMPACT -> tex("howling_impact");
            case WATER_BREATHING -> tex("water_breathing");
            case ICE_WALL -> tex("ice_wall");
            case WATER_ORB -> tex("water_orb");
            case WATER_SPOUT -> tex("water_spout");
            case RIPTIDE -> tex("riptide");
            case EARTHEN_PATH -> tex("earthen_path");
            case SEISMIC_PILLAR -> tex("seismic_pillar");
            case QUAKE_STOMP -> tex("quake_stomp");
            case BARK_SKIN -> tex("bark_skin");
            case FISSURE -> tex("fissure");
            case BONE_SPEAR -> tex("bone_spear");
            case RAISE_MINION -> tex("raise_minion");
            case SOUL_DRAIN -> tex("soul_drain");
            case EYE_DECAY -> tex("eye_decay");
            case WRAITH_STEP -> tex("wraith_step");
            case CHANT_OF_GROWTH -> tex("chant_of_growth");
            case BATTLE_HYMN -> tex("battle_hymn");
            case WORD_OF_UNMAKING -> tex("word_of_unmaking");
            case FEARCRAFT -> tex("fearcraft");
            case SILENCING_WHISPER -> tex("silencing_whisper");
            case STARLANCE -> tex("starlance");
            case ILLUMINATE_VISION -> tex("illuminate_vision");
            case LUMINOUS_PHASE -> tex("luminous_phase");
            case AEGIS_OF_STARS -> tex("aegis_of_stars");
            case ASTRAL_NOVA -> tex("astral_nova");
        };
    }

    /** Draw a spell's icon at (x, y) scaled to size x size. */
    public static void draw(GuiGraphics g, Spell spell, int x, int y, int size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(of(spell), x, y, size, size, 0.0f, 0.0f, TEX, TEX, TEX, TEX);
        RenderSystem.disableBlend();
    }
}
