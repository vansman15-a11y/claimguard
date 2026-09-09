package net.robmc.rpgstats;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.robmc.rpgstats.magic.SpellCasting;
import net.robmc.rpgstats.skill.RecallManager;
import net.robmc.rpgstats.skill.RestManager;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wires the RPG stat system into gameplay: initialises new players, keeps their
 * attributes in sync, ticks natural regen, scales combat damage to the ~300-450
 * pool, and awards stat XP for the matching actions.
 */
@Mod.EventBusSubscriber(modid = RpgStats.MOD_ID)
public class RpgEvents {

    /** Per-player last position, to measure distance travelled for Quickness. */
    private static final Map<UUID, double[]> lastPos = new HashMap<>();

    // --- lifecycle ---

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RpgManager.ensureInitialised(player);
            RpgManager.applyAttributes(player);
            RpgManager.sync(player);
            RpgManager.syncSpellBar(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SpellCasting.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerStats s = RpgManager.stats(player);
            s.setStamina(StatFormulas.maxStamina(s));
            s.setMana(StatFormulas.maxMana(s));
            RpgManager.applyAttributes(player);
            RpgManager.sync(player);
            RpgManager.syncSpellBar(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // Stats live in RpgData keyed by UUID, so nothing to copy - just refresh.
        if (event.isWasDeath() && event.getEntity() instanceof ServerPlayer player) {
            RpgManager.applyAttributes(player);
        }
    }

    // --- regen + movement tick ---

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        // distance travelled since last tick (for Quickness XP and movement stamina cost)
        double[] prev = lastPos.get(player.getUUID());
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        boolean moved = false;
        if (prev != null) {
            double dist = Math.sqrt(Math.pow(x - prev[0], 2) + Math.pow(y - prev[1], 2) + Math.pow(z - prev[2], 2));
            if (dist > 0.05 && dist < 20) { // ignore teleports and jitter
                moved = true;
                boolean inWater = player.isInWater() || player.isSwimming();
                double rate = inWater ? StatFormulas.XP_SWIM_PER_METRE : StatFormulas.XP_MOVE_PER_METRE;
                RpgManager.addXp(player, Stat.QUICKNESS, dist * rate);
            }
        }
        lastPos.put(player.getUUID(), new double[]{x, y, z});

        // Hunger is out of the picture: never let it block sprinting, never let it self-heal.
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() != StatFormulas.PINNED_FOOD_LEVEL) {
            food.setFoodLevel(StatFormulas.PINNED_FOOD_LEVEL);
        }
        food.setExhaustion(0.0f);

        // Moving on foot drains stamina - a trickle for walking, much more for sprinting.
        PlayerStats s = RpgManager.stats(player);
        boolean onFoot = player.onGround() || player.isInWater();
        if (moved && onFoot && s.getStamina() > 0) {
            double cost = player.isSprinting()
                    ? StatFormulas.STAMINA_SPRINT_PER_TICK
                    : StatFormulas.STAMINA_WALK_PER_TICK;
            s.setStamina(s.getStamina() - cost);
            if (player.tickCount % 4 == 0) {
                RpgManager.sync(player);
            }
        }
        if (player.isSprinting() && s.getStamina() <= 0) {
            player.setSprinting(false); // out of gas
        }

        Exhaustion.update(player, s.getStamina(), StatFormulas.maxStamina(s));

        RestManager.tick(player);
        RecallManager.tick(player);

        // Resting runs its own (faster) regen; skip the normal pass so they don't stack.
        if (!RestManager.isResting(player.getUUID())
                && player.tickCount % StatFormulas.REGEN_INTERVAL_TICKS == 0) {
            RpgManager.regenTick(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RestManager.clear(event.getEntity().getUUID());
        RecallManager.clear(event.getEntity().getUUID());
        Exhaustion.clear(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        RestManager.stop(player, "You get up.");
        if (Exhaustion.is(player)) {
            // too exhausted to jump - kill the upward velocity the jump just applied
            Vec3 dm = player.getDeltaMovement();
            player.setDeltaMovement(dm.x, Math.min(dm.y, 0.0), dm.z);
            player.hurtMarked = true; // force the corrected velocity to the client
            return;
        }
        PlayerStats s = RpgManager.stats(player);
        if (s.getStamina() > 0) {
            s.setStamina(s.getStamina() - StatFormulas.STAMINA_JUMP);
            RpgManager.sync(player);
        }
    }

    // --- exhausted: can't fight ---

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Exhaustion.is(player)) {
            event.setCanceled(true);
            player.displayClientMessage(exhaustedMsg(), true);
        }
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Exhaustion.is(player)) {
            event.setCanceled(true);
            player.displayClientMessage(exhaustedMsg(), true);
        }
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Exhaustion.is(player)) {
            return;
        }
        var item = event.getItem().getItem();
        if (item instanceof BowItem || item instanceof CrossbowItem || item instanceof ShieldItem) {
            event.setCanceled(true);
            player.displayClientMessage(exhaustedMsg(), true);
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Exhaustion.is(player)) {
            event.setCanceled(true); // no parry while exhausted - the hit lands in full
        }
    }

    private static net.minecraft.network.chat.Component exhaustedMsg() {
        return net.minecraft.network.chat.Component.literal("Too exhausted.")
                .withStyle(net.minecraft.ChatFormatting.RED);
    }

    // --- combat: scale damage to the pool, apply stat multipliers, award XP ---

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        float amount = event.getAmount();
        boolean magic = event.getSource().is(DamageTypes.INDIRECT_MAGIC) || event.getSource().is(DamageTypes.MAGIC);
        boolean projectile = event.getSource().is(DamageTypeTags.IS_PROJECTILE)
                || event.getSource().getDirectEntity() instanceof Projectile;
        boolean fromAttacker = event.getSource().getEntity() != null;

        if (!magic && event.getSource().getEntity() instanceof ServerPlayer attacker && attacker.isAlive()) {
            PlayerStats as = RpgManager.stats(attacker);
            if (projectile) {
                amount *= (float) StatFormulas.rangedDamageMultiplier(as);
                RpgManager.addXp(attacker, Stat.DEXTERITY, StatFormulas.XP_RANGED_HIT);
            } else if (event.getSource().getDirectEntity() == attacker) {
                amount *= (float) StatFormulas.meleeDamageMultiplier(as);
                RpgManager.addXp(attacker, Stat.STRENGTH, StatFormulas.XP_MELEE_HIT);
                RpgManager.addXp(attacker, Stat.VITALITY, StatFormulas.XP_MELEE_HIT * 0.6);
                var weapon = attacker.getMainHandItem().getItem();
                if (weapon instanceof SwordItem || weapon instanceof AxeItem || weapon instanceof TridentItem) {
                    as.setStamina(as.getStamina() - StatFormulas.STAMINA_MELEE_SWING);
                    RpgManager.sync(attacker);
                }
            }
        }

        // Everything hitting a player is scaled to the big pool; Dexterity shaves a little off spells.
        if (event.getEntity() instanceof ServerPlayer victim) {
            amount *= (float) StatFormulas.DAMAGE_SCALE;
            if (magic) {
                amount *= (float) (1.0 - StatFormulas.spellDamageResist(RpgManager.stats(victim)));
            }
            SpellCasting.interrupt(victim); // taking a hit breaks your cast
            RestManager.stop(victim, "Knocked out of your rest!");
            RecallManager.cancel(victim, "Recall interrupted!");

            // A slice of any hit from a mob or player also bleeds your other pools.
            if (fromAttacker && amount > 0) {
                double leech = amount * StatFormulas.DAMAGE_POOL_LEECH_FRACTION;
                double staminaShare = magic ? StatFormulas.LEECH_MAGIC_STAMINA
                        : projectile ? StatFormulas.LEECH_PROJECTILE_STAMINA
                        : StatFormulas.LEECH_PHYSICAL_STAMINA;
                PlayerStats vs = RpgManager.stats(victim);
                vs.setStamina(vs.getStamina() - leech * staminaShare);
                vs.setMana(vs.getMana() - leech * (1.0 - staminaShare));
                RpgManager.sync(victim);
            }
        }

        event.setAmount(amount);
    }

    // --- gathering XP ---

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        var state = event.getState();
        if (state.is(BlockTags.LOGS)) {
            boolean axe = player.getMainHandItem().getItem() instanceof AxeItem;
            RpgManager.addXp(player, axe ? Stat.VITALITY : Stat.STRENGTH, StatFormulas.XP_CHOP_LOG);
        } else if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            RpgManager.addXp(player, Stat.WISDOM, StatFormulas.XP_HARVEST_CROP);
        } else {
            RpgManager.addXp(player, Stat.STRENGTH, StatFormulas.XP_MINE_BLOCK);
        }
    }

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RpgManager.addXp(player, Stat.WISDOM, StatFormulas.XP_FISH_CATCH);
        }
    }
}
