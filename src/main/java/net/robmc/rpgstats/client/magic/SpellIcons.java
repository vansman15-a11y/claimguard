package net.robmc.rpgstats.client.magic;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.robmc.rpgstats.magic.Spell;

/** The 32x32 spell art in assets/rpgstats/textures/spell/, and a helper to blit it at any size. */
public final class SpellIcons {

    private static final int TEX = 32;

    private static final ResourceLocation MANA_TO_STAMINA = tex("mana_to_stamina");
    private static final ResourceLocation STAMINA_TO_HEALTH = tex("stamina_to_health");
    private static final ResourceLocation HEALTH_TO_MANA = tex("health_to_mana");
    private static final ResourceLocation MAGIC_BOLT = tex("magic_bolt");

    private SpellIcons() {
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation("rpgstats", "textures/spell/" + name + ".png");
    }

    public static ResourceLocation of(Spell spell) {
        return switch (spell) {
            case MANA_TO_STAMINA -> MANA_TO_STAMINA;
            case STAMINA_TO_HEALTH -> STAMINA_TO_HEALTH;
            case HEALTH_TO_MANA -> HEALTH_TO_MANA;
            case MAGIC_BOLT -> MAGIC_BOLT;
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
