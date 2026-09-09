package net.robmc.rpgstats.magic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.PlayerStats;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

/** Spawns the right {@link SpellProjectileEntity} for an Adept spell. */
public final class SpellProjectiles {

    private SpellProjectiles() {
    }

    public static void launch(ServerPlayer player, Spell spell, int schoolLevel) {
        PlayerStats s = RpgManager.stats(player);
        double spellMult = StatFormulas.spellDamageMultiplier(s);

        float damage;
        float speed;
        switch (spell) {
            case SUNDER -> {
                damage = (float) (StatFormulas.SUNDER_IMPACT_DAMAGE * spellMult);
                speed = (float) StatFormulas.SUNDER_SPEED;
            }
            case HEAL_OTHER -> {
                damage = 0.0f;
                speed = (float) StatFormulas.HEAL_OTHER_SPEED;
            }
            case AWAY -> {
                damage = (float) (StatFormulas.AWAY_IMPACT_DAMAGE * spellMult);
                speed = (float) StatFormulas.AWAY_SPEED;
            }
            case SCATTER -> {
                damage = (float) (StatFormulas.SCATTER_IMPACT_DAMAGE * spellMult);
                speed = (float) StatFormulas.SCATTER_SPEED;
            }
            case BRIGHT_LIGHT -> {
                damage = 0.0f;
                speed = (float) StatFormulas.BRIGHT_LIGHT_SPEED;
            }
            default -> {
                return;
            }
        }

        ServerLevel level = player.serverLevel();
        SpellProjectileEntity proj = new SpellProjectileEntity(level, player, spell, damage);
        proj.setPos(player.getX(), player.getEyeY() - 0.15, player.getZ());
        Vec3 dir = player.getViewVector(1.0f);
        proj.setDeltaMovement(dir.scale(speed));
        proj.shoot(dir.x, dir.y, dir.z, speed, 0.0f);
        level.addFreshEntity(proj);
    }
}
