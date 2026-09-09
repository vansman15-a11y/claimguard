package net.robmc.rpgstats.magic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.robmc.rpgstats.PlayerStats;
import net.robmc.rpgstats.RpgManager;
import net.robmc.rpgstats.StatFormulas;

/** Spawns the right {@link SpellProjectileEntity} for a projectile spell (Adept + Fire). */
public final class SpellProjectiles {

    private SpellProjectiles() {
    }

    public static void launch(ServerPlayer player, Spell spell, int spellLevel) {
        PlayerStats s = RpgManager.stats(player);
        double spellMult = StatFormulas.spellDamageMultiplier(s) * Afflictions.spellDamageMult(player);

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
            case BRIGHT_LIGHT -> {
                damage = 0.0f;
                speed = (float) StatFormulas.BRIGHT_LIGHT_SPEED;
            }
            case EMBER_DART -> {
                damage = (float) (StatFormulas.emberDartDamage(spellLevel) * spellMult);
                speed = (float) StatFormulas.EMBER_DART_SPEED;
            }
            case SUNBURST -> {
                damage = (float) (StatFormulas.sunburstDamage(spellLevel) * spellMult);
                speed = (float) StatFormulas.SUNBURST_SPEED;
            }
            case PYROCLASM -> {
                damage = (float) (StatFormulas.pyroclasmDamage(spellLevel) * spellMult);
                speed = (float) StatFormulas.PYROCLASM_SPEED;
            }
            case WITHER -> {
                damage = (float) (StatFormulas.WITHER_IMPACT_DAMAGE * spellMult);
                speed = (float) StatFormulas.CHAOS_BOLT_SPEED;
            }
            case SLUMP -> {
                damage = (float) (StatFormulas.SLUMP_IMPACT_DAMAGE * spellMult);
                speed = (float) StatFormulas.CHAOS_BOLT_SPEED;
            }
            case HEXDRAIN -> {
                damage = (float) (StatFormulas.HEXDRAIN_MOB_DAMAGE * spellMult);
                speed = (float) StatFormulas.CHAOS_BOLT_SPEED;
            }
            default -> {
                return;
            }
        }

        ServerLevel level = player.serverLevel();
        SpellProjectileEntity proj = new SpellProjectileEntity(level, player, spell, damage);
        proj.setPos(player.getX(), player.getEyeY() - 0.15, player.getZ());
        Vec3 dir = player.getViewVector(1.0f);
        if (spell == Spell.PYROCLASM) {
            // aim up so it arcs and falls like an arrow (the entity keeps gravity for this spell)
            dir = dir.add(0.0, StatFormulas.PYROCLASM_ARC_LIFT, 0.0).normalize();
        }
        proj.setDeltaMovement(dir.scale(speed));
        proj.shoot(dir.x, dir.y, dir.z, speed, 0.0f);
        level.addFreshEntity(proj);
    }
}
