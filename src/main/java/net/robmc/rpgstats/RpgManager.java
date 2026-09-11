package net.robmc.rpgstats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.network.PacketDistributor;
import net.robmc.claimguard.network.ClaimGuardNetwork;
import net.robmc.claimguard.network.SyncRpgStatsPacket;
import net.robmc.claimguard.network.SyncSpellBarPacket;
import net.robmc.rpgstats.magic.Spell;
import net.robmc.rpgstats.skill.RestManager;
import net.robmc.rpgstats.skill.Skill;

import java.util.UUID;

/** Server-side glue: keeps the vanilla attributes, the pools, and the client in sync with a player's stats. */
public final class RpgManager {

    private static final UUID HP_MODIFIER = UUID.fromString("b7d5c1a0-0001-4a00-8000-000000000001");
    private static final UUID SPEED_MODIFIER = UUID.fromString("b7d5c1a0-0002-4a00-8000-000000000002");

    private RpgManager() {
    }

    public static PlayerStats stats(ServerPlayer player) {
        PlayerStats s = RpgData.get(player.server).getOrCreate(player.getUUID());
        // cheap (4 armour slots) and keeps casts/spell damage honest about what's currently worn
        s.setArmorEncumbrance(net.robmc.rpgstats.item.ArmorTier.totalEncumbrance(player));
        return s;
    }

    public static void markDirty(ServerPlayer player) {
        RpgData.get(player.server).markDirty();
    }

    /** First join: seed the pools to full so a new player starts around BASE_POOL on each bar. */
    public static void ensureInitialised(ServerPlayer player) {
        PlayerStats s = stats(player);
        if (!s.isInitialised()) {
            s.setStamina(StatFormulas.maxStamina(s));
            s.setMana(StatFormulas.maxMana(s));
            s.markInitialised();
            RpgData.get(player.server).markDirty();
        }
    }

    /** Re-derive the vanilla MAX_HEALTH / MOVEMENT_SPEED attributes from the player's stats. */
    public static void applyAttributes(ServerPlayer player) {
        PlayerStats s = stats(player);

        AttributeInstance maxHp = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) {
            maxHp.removeModifier(HP_MODIFIER);
            maxHp.addPermanentModifier(new AttributeModifier(
                    HP_MODIFIER, "rpg_hp", StatFormulas.maxHealth(s) - 20.0, AttributeModifier.Operation.ADDITION));
        }

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER);
            double bonus = StatFormulas.moveSpeedBonus(s);
            if (bonus > 0) {
                speed.addPermanentModifier(new AttributeModifier(
                        SPEED_MODIFIER, "rpg_speed", bonus, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
        s.setStamina(s.getStamina()); // re-clamp to any new max
        s.setMana(s.getMana());
    }

    public static void sync(ServerPlayer player) {
        PlayerStats s = stats(player);
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncRpgStatsPacket(
                player.getHealth(), player.getMaxHealth(),
                s.getStamina(), StatFormulas.maxStamina(s),
                s.getMana(), StatFormulas.maxMana(s),
                s.getLevel(Stat.STRENGTH), s.getLevel(Stat.VITALITY), s.getLevel(Stat.DEXTERITY),
                s.getLevel(Stat.QUICKNESS), s.getLevel(Stat.INTELLIGENCE), s.getLevel(Stat.WISDOM),
                RestManager.isResting(player.getUUID()), Exhaustion.is(player.getUUID())));
    }

    public static void syncSpellBar(ServerPlayer player) {
        PlayerStats s = stats(player);
        ClaimGuardNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SyncSpellBarPacket(s.getSpellBar().clone(), s.spellLevelArray(),
                        s.schoolLevelArray(), s.skillLevelArray()));
    }

    /**
     * XP from casting / landing a spell: the spell levels on its own (scales its power),
     * and a share of the same XP feeds its school (unlocks tiers). Tuned so a school hits
     * 100 around the time all its spells reach ~70.
     */
    public static void addSpellXp(ServerPlayer player, Spell spell, double amount) {
        PlayerStats s = stats(player);
        double gross = amount * StatFormulas.SCHOOL_XP_MULT;

        int spellGained = s.addSpellXp(spell, gross);
        if (spellGained > 0) {
            int now = s.getSpellLevel(spell);
            player.displayClientMessage(Component.literal(
                    spell.displayName() + "  ->  Lv " + now
                            + "  (" + StatFormulas.effectivenessPercent(now) + "%)")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }

        var school = spell.school();
        int before = s.getSchoolLevel(school);
        int schoolGained = s.addSchoolXp(school, gross * StatFormulas.SCHOOL_XP_SHARE);
        if (schoolGained > 0) {
            int now = s.getSchoolLevel(school);
            player.displayClientMessage(Component.literal(
                    school.displayName() + " school  ->  Lv " + now).withStyle(ChatFormatting.DARK_PURPLE), false);
            for (int t = 2; t <= net.robmc.rpgstats.magic.School.MAX_TIERS; t++) {
                int unlock = school.unlockLevel(t);
                if (before < unlock && now >= unlock) {
                    net.robmc.rpgstats.magic.Spell unlocked = net.robmc.rpgstats.magic.Spell.of(school, t);
                    if (unlocked != null) {
                        player.displayClientMessage(Component.literal(
                                "Unlocked: " + unlocked.displayName()).withStyle(ChatFormatting.AQUA), false);
                    }
                }
            }
        }

        RpgData.get(player.server).markDirty();
        syncSpellBar(player);
    }

    /** Train one skill; announce a level-up and re-sync the bar. */
    public static void addSkillXp(ServerPlayer player, Skill skill, double amount) {
        PlayerStats s = stats(player);
        int gained = s.addSkillXp(skill, amount);
        RpgData.get(player.server).markDirty();
        if (gained > 0) {
            player.displayClientMessage(Component.literal(
                    skill.displayName() + "  ->  Lv " + s.getSkillLevel(skill)
                            + "  (" + StatFormulas.effectivenessPercent(s.getSkillLevel(skill)) + "%)")
                    .withStyle(ChatFormatting.GREEN), true);
        }
        syncSpellBar(player);
    }

    public static void addXp(ServerPlayer player, Stat stat, double amount) {
        PlayerStats s = stats(player);
        int gained = s.addXp(stat, amount);
        RpgData.get(player.server).markDirty();
        if (gained > 0) {
            applyAttributes(player);
            player.displayClientMessage(Component.literal(
                    stat.displayName() + " increased to " + s.getLevel(stat) + "!").withStyle(ChatFormatting.AQUA), true);
        }
        sync(player);
    }

    /** Called on the regen interval - nudges every pool up a little. Needs Nourishment (see food package) to run at all. */
    public static void regenTick(ServerPlayer player) {
        if (!player.isAlive()) {
            return;
        }
        if (!net.robmc.rpgstats.food.NourishmentManager.isNourished(player.getUUID(), player.serverLevel().getGameTime())) {
            return;
        }
        PlayerStats s = stats(player);

        if (player.getHealth() < player.getMaxHealth()) {
            player.heal((float) StatFormulas.HP_REGEN_PER_TICK);
        }
        double stamMax = StatFormulas.maxStamina(s);
        double manaMax = StatFormulas.maxMana(s);
        boolean changed = false;
        if (s.getStamina() < stamMax) {
            s.setStamina(s.getStamina() + StatFormulas.staminaRegen(s));
            changed = true;
        }
        if (s.getMana() < manaMax) {
            s.setMana(s.getMana() + StatFormulas.manaRegen(s));
            changed = true;
        }
        if (changed) {
            RpgData.get(player.server).markDirty();
        }
        sync(player);
    }
}
